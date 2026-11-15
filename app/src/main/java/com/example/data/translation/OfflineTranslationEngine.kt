package com.example.data.translation

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.domain.model.LanguageCatalog
import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentificationOptions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class TranslationOutcome(
    val translatedText: String,
    val detectedSourceCode: String,
    val targetCode: String,
    val wasSameLanguage: Boolean = false
)

class OfflineModelMissingException(
    val missingLanguageCodes: List<String>,
    message: String
) : IOException(message)

class OfflineTranslationEngine(private val context: Context) {

    private val modelManager: RemoteModelManager by lazy { RemoteModelManager.getInstance() }
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
                throw IOException("دانلود روی اینترنت همراه غیرفعال است. به وای‌فای متصل شوید یا دانلود با دیتای موبایل را در تنظیمات فعال کنید.")
            }
            val model = TranslateRemoteModel.Builder(tag).build()
            val conditions = DownloadConditions.Builder().apply {
                if (!allowCellular) requireWifi()
            }.build()
            modelManager.download(model, conditions).awaitResult()
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

    suspend fun identifyLanguage(text: String): String = withContext(Dispatchers.Default) {
        val cleaned = text.trim()
        if (cleaned.isEmpty()) return@withContext "en"

        val detected = runCatching {
            languageIdentifier.identifyLanguage(cleaned).awaitResult()
        }.getOrDefault("und")

        if (detected != "und" && toMlKitTranslateLanguage(detected) != null) {
            return@withContext normalizeToAppCode(detected)
        }

        heuristicDetectScript(cleaned)
    }

    suspend fun translate(
        text: String,
        sourceCode: String,
        targetCode: String,
        autoDownloadIfOnline: Boolean = true,
        allowCellular: Boolean = true
    ): TranslationOutcome = withContext(Dispatchers.IO) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            return@withContext TranslationOutcome(
                translatedText = "",
                detectedSourceCode = if (sourceCode == LanguageCatalog.AUTO_DETECT_CODE) "en" else sourceCode,
                targetCode = targetCode
            )
        }

        val resolvedSource = if (sourceCode == LanguageCatalog.AUTO_DETECT_CODE) {
            identifyLanguage(trimmed)
        } else {
            sourceCode
        }

        if (resolvedSource.equals(targetCode, ignoreCase = true)) {
            return@withContext TranslationOutcome(
                translatedText = trimmed,
                detectedSourceCode = resolvedSource,
                targetCode = targetCode,
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
                    "دانلود مدل ترجمه آفلاین ($names) به وای‌فای محدود شده است. می‌توانید گزینه دانلود با اینترنت همراه را فعال کنید."
                } else {
                    "مدل ترجمه آفلاین برای زبان $names هنوز دریافت نشده است. لطفاً اینترنت را روشن کرده و مدل را دریافت کنید."
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
            val result = translator.translate(trimmed).awaitResult()
            TranslationOutcome(
                translatedText = result,
                detectedSourceCode = resolvedSource,
                targetCode = targetCode,
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
        for (ch in text) {
            when (ch) {
                'پ', 'چ', 'ژ', 'گ', 'ک', 'ی' -> {
                    persianSpecificCount++
                    arabicScriptCount++
                }
                in '\u0600'..'\u06FF' -> arabicScriptCount++
                in 'A'..'Z', in 'a'..'z' -> latinCount++
                in '\u0400'..'\u04FF' -> cyrillicCount++
            }
        }
        return when {
            persianSpecificCount > 0 -> "fa"
            arabicScriptCount > latinCount -> "fa"
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
