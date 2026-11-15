package com.example.data.translation

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.example.domain.model.LanguageCatalog
import com.example.domain.model.PrewarmStatus
import com.example.domain.model.TranslationEngineMode
import com.example.domain.model.UsedTranslationEngine
import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentificationOptions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class TranslationOutcome(
    val translatedText: String,
    val detectedSourceCode: String,
    val targetCode: String,
    val usedEngine: UsedTranslationEngine,
    val wasSameLanguage: Boolean = false
)

class OfflineModelMissingException(
    val missingLanguageCodes: List<String>,
    message: String
) : IOException(message)

class OfflineTranslationEngine(private val context: Context) {

    companion object {
        private const val TAG = "TranslationEngine"
        private const val MODEL_DOWNLOAD_TIMEOUT_MS = 45_000L

        // Strictly serialize ALL ML Kit RemoteModelManager downloads across the whole process
        private val downloadMutex = Mutex()
        private val prewarmScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private var prewarmJob: Job? = null

        private val _prewarmStatus = MutableStateFlow(PrewarmStatus())
        val prewarmStatus: StateFlow<PrewarmStatus> = _prewarmStatus.asStateFlow()

        private val _activeDownloadingCode = MutableStateFlow<String?>(null)
        val activeDownloadingCode: StateFlow<String?> = _activeDownloadingCode.asStateFlow()
    }

    private val modelManager: RemoteModelManager by lazy { RemoteModelManager.getInstance() }
    private val onlineClient = OnlineTranslationClient()
    private val languageIdentifier by lazy {
        LanguageIdentification.getClient(
            LanguageIdentificationOptions.Builder()
                .setConfidenceThreshold(0.35f)
                .build()
        )
    }

    suspend fun getDownloadedModelCodes(): Set<String> = withContext(Dispatchers.IO) {
        val models = modelManager.getDownloadedModels(TranslateRemoteModel::class.java).awaitResult()
        models.mapNotNull { it.language }.toSet() + setOf(TranslateLanguage.ENGLISH)
    }

    suspend fun isModelInstalled(mlKitCode: String): Boolean {
        val tag = toMlKitTranslateLanguage(mlKitCode) ?: return false
        if (tag == TranslateLanguage.ENGLISH) return true
        val model = TranslateRemoteModel.Builder(tag).build()
        return modelManager.isModelDownloaded(model).awaitResult()
    }

    suspend fun downloadModel(
        mlKitCode: String,
        allowCellular: Boolean = true
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val tag = toMlKitTranslateLanguage(mlKitCode)
                ?: throw IllegalArgumentException("زبان $mlKitCode پشتیبانی نمی‌شود.")
            if (tag == TranslateLanguage.ENGLISH) return@runCatching
            if (!isNetworkAvailable()) {
                throw IOException("برای دانلود مدل آفلاین زبان، اتصال اینترنت لازم است.")
            }
            if (!allowCellular && !isWifiConnected()) {
                throw IOException("دانلود روی اینترنت همراه غیرفعال است. به وای‌فای متصل شوید یا گزینه اینترنت همراه را فعال کنید.")
            }

            val model = TranslateRemoteModel.Builder(tag).build()
            val conditions = DownloadConditions.Builder().apply {
                if (!allowCellular) requireWifi()
            }.build()

            downloadMutex.withLock {
                _activeDownloadingCode.value = mlKitCode
                try {
                    if (modelManager.isModelDownloaded(model).awaitResult()) {
                        return@withLock
                    }
                    withTimeout(MODEL_DOWNLOAD_TIMEOUT_MS) {
                        modelManager.download(model, conditions).awaitResult()
                    }
                } catch (e: TimeoutCancellationException) {
                    withContext(NonCancellable) {
                        runCatching { modelManager.deleteDownloadedModel(model).awaitResult() }
                    }
                    throw IOException(
                        "زمان دانلود مدل ${LanguageCatalog.displayNameForCode(mlKitCode)} به پایان رسید (۴۵ ثانیه). فایل ناتمام پاکسازی شد؛ دوباره تلاش کنید.",
                        e
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    withContext(NonCancellable) {
                        runCatching { modelManager.deleteDownloadedModel(model).awaitResult() }
                    }
                    throw e
                } finally {
                    if (_activeDownloadingCode.value == mlKitCode) {
                        _activeDownloadingCode.value = null
                    }
                }
            }
        }
    }

    suspend fun deleteModel(mlKitCode: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val tag = toMlKitTranslateLanguage(mlKitCode)
                ?: throw IllegalArgumentException("زبان $mlKitCode پشتیبانی نمی‌شود.")
            if (tag == TranslateLanguage.ENGLISH) return@runCatching
            val model = TranslateRemoteModel.Builder(tag).build()
            modelManager.deleteDownloadedModel(model).awaitResult()
        }
    }

    fun startCorePrewarm(allowCellular: Boolean) {
        if (prewarmJob?.isActive == true) return
        if (!isNetworkAvailable()) return
        if (!allowCellular && !isWifiConnected()) return

        prewarmJob = prewarmScope.launch {
            val coreCodes = LanguageCatalog.PREWARM_CORE_LANGUAGE_CODES
            _prewarmStatus.update {
                it.copy(
                    isRunning = true,
                    isPaused = false,
                    totalCount = coreCodes.size,
                    lastError = null
                )
            }

            var completed = 0
            try {
                for (code in coreCodes) {
                    if (!isNetworkAvailable() || (!allowCellular && !isWifiConnected())) {
                        _prewarmStatus.update {
                            it.copy(
                                isRunning = false,
                                isPaused = true,
                                currentLanguageCode = null,
                                lastError = "اتصال مناسب شبکه برقرار نیست."
                            )
                        }
                        return@launch
                    }

                    val alreadyInstalled = runCatching { isModelInstalled(code) }.getOrDefault(false)
                    if (alreadyInstalled) {
                        completed++
                        _prewarmStatus.update { it.copy(completedCount = completed) }
                        continue
                    }

                    _prewarmStatus.update {
                        it.copy(
                            currentLanguageCode = code,
                            completedCount = completed,
                            lastError = null
                        )
                    }

                    val res = downloadModel(code, allowCellular = allowCellular)
                    if (res.isSuccess) {
                        completed++
                        _prewarmStatus.update { it.copy(completedCount = completed) }
                    } else {
                        val err = res.exceptionOrNull()?.localizedMessage ?: "خطا در دریافت مدل"
                        _prewarmStatus.update {
                            it.copy(
                                isRunning = false,
                                currentLanguageCode = null,
                                lastError = err
                            )
                        }
                        return@launch
                    }
                }

                _prewarmStatus.update {
                    it.copy(
                        isRunning = false,
                        isPaused = false,
                        currentLanguageCode = null,
                        completedCount = coreCodes.size,
                        lastError = null
                    )
                }
            } catch (e: CancellationException) {
                _prewarmStatus.update {
                    it.copy(isRunning = false, isPaused = true, currentLanguageCode = null)
                }
                throw e
            }
        }
    }

    fun pausePrewarm() {
        prewarmJob?.cancel()
        prewarmJob = null
        _prewarmStatus.update {
            it.copy(isRunning = false, isPaused = true, currentLanguageCode = null)
        }
    }

    suspend fun cleanStalledAndRetryPrewarm(allowCellular: Boolean) {
        pausePrewarm()
        withContext(Dispatchers.IO) {
            downloadMutex.withLock {
                for (code in LanguageCatalog.PREWARM_CORE_LANGUAGE_CODES) {
                    val installed = runCatching { isModelInstalled(code) }.getOrDefault(false)
                    if (!installed) {
                        val tag = toMlKitTranslateLanguage(code) ?: continue
                        runCatching {
                            modelManager.deleteDownloadedModel(
                                TranslateRemoteModel.Builder(tag).build()
                            ).awaitResult()
                        }
                    }
                }
            }
        }
        startCorePrewarm(allowCellular)
    }

    suspend fun identifyLanguage(text: String): String = withContext(Dispatchers.Default) {
        val cleaned = text.trim()
        if (cleaned.isEmpty()) return@withContext "en"

        val detected = runCatching {
            languageIdentifier.identifyLanguage(cleaned).awaitResult()
        }.getOrDefault("und")

        if (!detected.isNullOrBlank() && detected != "und" && toMlKitTranslateLanguage(detected) != null) {
            return@withContext normalizeToAppCode(detected)
        }

        heuristicDetectScript(cleaned)
    }

    suspend fun translateWithMode(
        text: String,
        sourceCode: String,
        targetCode: String,
        mode: TranslationEngineMode,
        geminiApiKey: String,
        allowCellular: Boolean = true
    ): TranslationOutcome = withContext(Dispatchers.IO) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            return@withContext TranslationOutcome(
                translatedText = "",
                detectedSourceCode = if (sourceCode == LanguageCatalog.AUTO_DETECT_CODE) "en" else sourceCode,
                targetCode = targetCode,
                usedEngine = UsedTranslationEngine.OFFLINE_MLKIT
            )
        }

        val detectedHint = if (sourceCode == LanguageCatalog.AUTO_DETECT_CODE) {
            identifyLanguage(trimmed)
        } else {
            sourceCode
        }

        if (detectedHint.equals(targetCode, ignoreCase = true) && sourceCode != LanguageCatalog.AUTO_DETECT_CODE) {
            return@withContext TranslationOutcome(
                translatedText = trimmed,
                detectedSourceCode = detectedHint,
                targetCode = targetCode,
                usedEngine = UsedTranslationEngine.OFFLINE_MLKIT,
                wasSameLanguage = true
            )
        }

        when (mode) {
            TranslationEngineMode.ONLINE_ONLY -> {
                if (!isNetworkAvailable()) {
                    throw IOException("حالت «فقط آنلاین» فعال است و اتصال اینترنت برقرار نیست.")
                }
                val online = onlineClient.translateOnline(
                    text = trimmed,
                    sourceCode = sourceCode,
                    targetCode = targetCode,
                    detectedHintCode = detectedHint,
                    geminiApiKey = geminiApiKey
                )
                TranslationOutcome(
                    translatedText = online.translatedText,
                    detectedSourceCode = online.detectedSourceCode ?: detectedHint,
                    targetCode = targetCode,
                    usedEngine = online.usedEngine,
                    wasSameLanguage = false
                )
            }

            TranslationEngineMode.OFFLINE_ONLY -> {
                translateOffline(
                    text = trimmed,
                    sourceCode = sourceCode,
                    resolvedSourceHint = detectedHint,
                    targetCode = targetCode,
                    autoDownloadIfOnline = true,
                    allowCellular = allowCellular
                )
            }

            TranslationEngineMode.SMART_HYBRID -> {
                if (isNetworkAvailable()) {
                    val onlineAttempt = runCatching {
                        onlineClient.translateOnline(
                            text = trimmed,
                            sourceCode = sourceCode,
                            targetCode = targetCode,
                            detectedHintCode = detectedHint,
                            geminiApiKey = geminiApiKey
                        )
                    }
                    onlineAttempt.onSuccess { online ->
                        if (online.translatedText.isNotBlank()) {
                            return@withContext TranslationOutcome(
                                translatedText = online.translatedText,
                                detectedSourceCode = online.detectedSourceCode ?: detectedHint,
                                targetCode = targetCode,
                                usedEngine = online.usedEngine,
                                wasSameLanguage = false
                            )
                        }
                    }.onFailure { e ->
                        Log.w(TAG, "Online translation failed in Smart Hybrid mode; falling back to offline ML Kit", e)
                    }
                }

                // Fallback to offline ML Kit
                translateOffline(
                    text = trimmed,
                    sourceCode = sourceCode,
                    resolvedSourceHint = detectedHint,
                    targetCode = targetCode,
                    autoDownloadIfOnline = true,
                    allowCellular = allowCellular
                )
            }
        }
    }

    private suspend fun translateOffline(
        text: String,
        sourceCode: String,
        resolvedSourceHint: String,
        targetCode: String,
        autoDownloadIfOnline: Boolean,
        allowCellular: Boolean
    ): TranslationOutcome {
        val resolvedSource = if (sourceCode == LanguageCatalog.AUTO_DETECT_CODE) {
            resolvedSourceHint
        } else {
            sourceCode
        }

        if (resolvedSource.equals(targetCode, ignoreCase = true)) {
            return TranslationOutcome(
                translatedText = text,
                detectedSourceCode = resolvedSource,
                targetCode = targetCode,
                usedEngine = UsedTranslationEngine.OFFLINE_MLKIT,
                wasSameLanguage = true
            )
        }

        val mlSource = toMlKitTranslateLanguage(resolvedSource)
            ?: throw IllegalArgumentException("زبان مبدأ (${LanguageCatalog.displayNameForCode(resolvedSource)}) برای ترجمه آفلاین پشتیبانی نمی‌شود.")
        val mlTarget = toMlKitTranslateLanguage(targetCode)
            ?: throw IllegalArgumentException("زبان مقصد (${LanguageCatalog.displayNameForCode(targetCode)}) برای ترجمه آفلاین پشتیبانی نمی‌شود.")

        val missing = mutableListOf<String>()
        if (mlSource != TranslateLanguage.ENGLISH && !isModelInstalled(resolvedSource)) {
            missing.add(resolvedSource)
        }
        if (mlTarget != TranslateLanguage.ENGLISH && !isModelInstalled(targetCode)) {
            missing.add(targetCode)
        }

        if (missing.isNotEmpty()) {
            val canDownloadNow = autoDownloadIfOnline && isNetworkAvailable() && (allowCellular || isWifiConnected())
            if (canDownloadNow) {
                for (code in missing) {
                    downloadModel(code, allowCellular = allowCellular).getOrThrow()
                }
            } else {
                val names = missing.joinToString(" و ") { LanguageCatalog.displayNameForCode(it) }
                val reason = if (!allowCellular && isNetworkAvailable() && !isWifiConnected()) {
                    "دانلود مدل ترجمه آفلاین ($names) به وای‌فای محدود شده است. می‌توانید گزینه اینترنت همراه را فعال کنید."
                } else {
                    "مدل ترجمه آفلاین برای زبان $names هنوز دریافت نشده است. برای دانلود یک‌باره به اینترنت متصل شوید."
                }
                throw OfflineModelMissingException(
                    missingLanguageCodes = missing,
                    message = reason
                )
            }
        }

        val options = TranslatorOptions.Builder()
            .setSourceLanguage(mlSource)
            .setTargetLanguage(mlTarget)
            .build()
        val translator = Translation.getClient(options)
        try {
            val result = translator.translate(text).awaitResult()
            return TranslationOutcome(
                translatedText = result,
                detectedSourceCode = resolvedSource,
                targetCode = targetCode,
                usedEngine = UsedTranslationEngine.OFFLINE_MLKIT,
                wasSameLanguage = false
            )
        } finally {
            translator.close()
        }
    }

    fun isNetworkAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun isWifiConnected(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    private fun heuristicDetectScript(text: String): String {
        var persianSpecificCount = 0
        var arabicScriptCount = 0
        var latinCount = 0
        var cyrillicCount = 0
        var cjkCount = 0
        var hiraganaKatakanaCount = 0
        var hangulCount = 0
        var devanagariCount = 0

        for (ch in text) {
            when (ch) {
                'پ', 'چ', 'ژ', 'گ', 'ک', 'ی' -> {
                    persianSpecificCount++
                    arabicScriptCount++
                }
                in '\u0600'..'\u06FF' -> arabicScriptCount++
                in 'A'..'Z', in 'a'..'z' -> latinCount++
                in '\u0400'..'\u04FF' -> cyrillicCount++
                in '\u4E00'..'\u9FFF' -> cjkCount++
                in '\u3040'..'\u30FF' -> hiraganaKatakanaCount++
                in '\uAC00'..'\uD7AF' -> hangulCount++
                in '\u0900'..'\u097F' -> devanagariCount++
            }
        }
        return when {
            persianSpecificCount > 0 -> "fa"
            arabicScriptCount > latinCount -> "ar"
            hiraganaKatakanaCount > 0 -> "ja"
            hangulCount > 0 -> "ko"
            cjkCount > 0 -> "zh"
            devanagariCount > 0 -> "hi"
            cyrillicCount > latinCount -> "ru"
            else -> "en"
        }
    }

    private fun normalizeToAppCode(mlTag: String): String {
        return when {
            mlTag.startsWith("zh", ignoreCase = true) -> "zh"
            mlTag.contains("-") -> mlTag.substringBefore("-").lowercase()
            else -> mlTag.lowercase()
        }
    }

    private fun toMlKitTranslateLanguage(appCode: String): String? {
        val normalized = normalizeToAppCode(appCode)
        return TranslateLanguage.fromLanguageTag(normalized)
    }
}

private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { result ->
        if (cont.isActive) cont.resume(result)
    }.addOnFailureListener { exception ->
        if (cont.isActive) cont.resumeWithException(exception)
    }.addOnCanceledListener {
        if (cont.isActive) cont.cancel()
    }
}
