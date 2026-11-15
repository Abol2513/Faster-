package com.example.ui.setup

import android.app.Application
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.service.voice.VoiceInteractionService
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.assistant.CircleVoiceInteractionService
import com.example.data.ocr.TesseractOcrEngine
import com.example.data.prefs.AppPreferencesRepository
import com.example.data.session.OverlaySessionStore
import com.example.data.translation.OfflineTranslationEngine
import com.example.domain.model.CaptureSource
import com.example.domain.model.LanguageCatalog
import com.example.domain.model.LanguagePackStatus
import com.example.domain.model.PrewarmStatus
import com.example.domain.model.TranslationEngineMode
import com.example.util.DeviceCompatibilityHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SetupUiState(
    val isDefaultAssistant: Boolean = false,
    val isXiaomiDevice: Boolean = DeviceCompatibilityHelper.isXiaomiDevice(),
    val targetLanguageCode: String = "fa",
    val activeOcrLanguages: Set<String> = setOf("fas", "eng"),
    val autoTranslateOnSelect: Boolean = true,
    val allowCellularDownloads: Boolean = true,
    val translationEngineMode: TranslationEngineMode = TranslationEngineMode.SMART_HYBRID,
    val geminiApiKey: String = "",
    val prewarmStatus: PrewarmStatus = PrewarmStatus(),
    val languagePacks: List<LanguagePackStatus> = emptyList(),
    val isLoadingPacks: Boolean = true
)

class SetupViewModel(application: Application) : AndroidViewModel(application) {

    private val prefsRepo = AppPreferencesRepository(application)
    private val ocrEngine = TesseractOcrEngine(application)
    private val translationEngine = OfflineTranslationEngine(application)

    private val _uiState = MutableStateFlow(SetupUiState())
    val uiState: StateFlow<SetupUiState> = _uiState.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val downloadingOcr = MutableStateFlow<Set<String>>(emptySet())
    private val downloadingTranslate = MutableStateFlow<Set<String>>(emptySet())
    private var prewarmTriggeredOnce = false

    init {
        viewModelScope.launch {
            runCatching { ocrEngine.ensureBundledTessData() }
            refreshSystemStatus()
            val allowCellular = prefsRepo.allowCellularDownloads.first()
            refreshLanguagePacks()
            if (!prewarmTriggeredOnce && !OfflineTranslationEngine.prewarmStatus.value.isPaused) {
                prewarmTriggeredOnce = true
                translationEngine.startCorePrewarm(allowCellular)
            }
        }

        viewModelScope.launch {
            prefsRepo.targetLanguageCode.collectLatest { code ->
                _uiState.update { it.copy(targetLanguageCode = code) }
            }
        }
        viewModelScope.launch {
            prefsRepo.activeOcrLanguages.collectLatest { langs ->
                _uiState.update { it.copy(activeOcrLanguages = langs) }
            }
        }
        viewModelScope.launch {
            prefsRepo.autoTranslateOnSelect.collectLatest { auto ->
                _uiState.update { it.copy(autoTranslateOnSelect = auto) }
            }
        }
        viewModelScope.launch {
            prefsRepo.allowCellularDownloads.collectLatest { allowCellular ->
                _uiState.update { it.copy(allowCellularDownloads = allowCellular) }
            }
        }
        viewModelScope.launch {
            prefsRepo.translationEngineMode.collectLatest { mode ->
                _uiState.update { it.copy(translationEngineMode = mode) }
            }
        }
        viewModelScope.launch {
            prefsRepo.geminiApiKey.collectLatest { key ->
                _uiState.update { it.copy(geminiApiKey = key) }
            }
        }
        viewModelScope.launch {
            OfflineTranslationEngine.prewarmStatus.collectLatest { status ->
                _uiState.update { it.copy(prewarmStatus = status) }
                refreshLanguagePacks()
            }
        }
        viewModelScope.launch {
            OfflineTranslationEngine.activeDownloadingCode.collectLatest {
                refreshLanguagePacks()
            }
        }
    }

    fun refreshSystemStatus() {
        val ctx = getApplication<Application>()
        val held = checkDefaultAssistant(ctx)
        _uiState.update { it.copy(isDefaultAssistant = held) }
    }

    fun refreshLanguagePacks() {
        viewModelScope.launch {
            val installedOcr = ocrEngine.getInstalledOcrCodes()
            val downloadedTranslate = runCatching {
                translationEngine.getDownloadedModelCodes()
            }.getOrDefault(setOf("en"))

            val ocrBusy = downloadingOcr.value
            val trBusy = downloadingTranslate.value
            val activeGlobalCode = OfflineTranslationEngine.activeDownloadingCode.value

            val packs = LanguageCatalog.supportedLanguages.map { lang ->
                val isOcrOk = lang.tessCode != null && lang.tessCode in installedOcr
                val isBuiltInTr = lang.mlKitCode == "en"
                val isTrOk = isBuiltInTr || lang.mlKitCode in downloadedTranslate
                val isTrBusy = !isTrOk && (lang.mlKitCode in trBusy || lang.mlKitCode == activeGlobalCode)
                LanguagePackStatus(
                    language = lang,
                    isOcrInstalled = isOcrOk,
                    isTranslateModelInstalled = isTrOk,
                    isTranslateBuiltIn = isBuiltInTr,
                    isDownloadingOcr = lang.tessCode != null && lang.tessCode in ocrBusy,
                    isDownloadingTranslate = isTrBusy
                )
            }
            _uiState.update { it.copy(languagePacks = packs, isLoadingPacks = false) }
        }
    }

    fun setTargetLanguage(code: String) {
        viewModelScope.launch {
            prefsRepo.setTargetLanguage(code)
        }
    }

    fun toggleOcrLanguage(tessCode: String, enabled: Boolean) {
        viewModelScope.launch {
            if (!ocrEngine.isOcrLanguageInstalled(tessCode)) {
                _messages.tryEmit("ابتدا بسته OCR این زبان را دانلود کنید.")
                return@launch
            }
            prefsRepo.toggleOcrLanguage(tessCode, enabled)
        }
    }

    fun setAutoTranslate(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepo.setAutoTranslateOnSelect(enabled)
        }
    }

    fun setAllowCellularDownloads(allowed: Boolean) {
        viewModelScope.launch {
            prefsRepo.setAllowCellularDownloads(allowed)
        }
    }

    fun setTranslationEngineMode(mode: TranslationEngineMode) {
        viewModelScope.launch {
            prefsRepo.setTranslationEngineMode(mode)
        }
    }

    fun saveGeminiApiKey(apiKey: String) {
        viewModelScope.launch {
            val trimmed = apiKey.trim()
            prefsRepo.setGeminiApiKey(trimmed)
            _messages.tryEmit(
                if (trimmed.isEmpty()) {
                    "کلید Gemini API پاک شد؛ ترجمه آنلاین از سرویس رایگان پیش‌فرض استفاده می‌کند."
                } else {
                    "کلید Gemini API با موفقیت روی دستگاه ذخیره شد."
                }
            )
        }
    }

    fun pausePrewarm() {
        translationEngine.pausePrewarm()
    }

    fun resumePrewarm() {
        val allowCellular = _uiState.value.allowCellularDownloads
        if (!translationEngine.isNetworkAvailable()) {
            _messages.tryEmit("برای دریافت مدل‌های آفلاین، اتصال اینترنت را بررسی کنید.")
            return
        }
        if (!allowCellular && !translationEngine.isWifiConnected()) {
            _messages.tryEmit("دانلود روی اینترنت همراه غیرفعال است؛ به وای‌فای متصل شوید یا گزینه اینترنت همراه را فعال کنید.")
            return
        }
        translationEngine.startCorePrewarm(allowCellular)
    }

    fun retryCleanPrewarm() {
        viewModelScope.launch {
            val allowCellular = _uiState.value.allowCellularDownloads
            if (!translationEngine.isNetworkAvailable()) {
                _messages.tryEmit("برای پاکسازی و تلاش مجدد، اتصال اینترنت را برقرار کنید.")
                return@launch
            }
            translationEngine.cleanStalledAndRetryPrewarm(allowCellular)
            refreshLanguagePacks()
        }
    }

    fun downloadOcrPack(tessCode: String) {
        viewModelScope.launch {
            downloadingOcr.update { it + tessCode }
            refreshLanguagePacks()
            val result = ocrEngine.downloadOcrLanguage(tessCode)
            downloadingOcr.update { it - tessCode }
            result.onSuccess {
                prefsRepo.toggleOcrLanguage(tessCode, true)
                _messages.tryEmit("بسته تشخیص متن آفلاین با موفقیت نصب شد.")
            }.onFailure { e ->
                _messages.tryEmit(e.localizedMessage ?: "دانلود بسته OCR ناموفق بود.")
            }
            refreshLanguagePacks()
        }
    }

    fun deleteOcrPack(tessCode: String) {
        viewModelScope.launch {
            prefsRepo.toggleOcrLanguage(tessCode, false)
            ocrEngine.deleteOcrLanguage(tessCode)
            _messages.tryEmit("بسته OCR حذف شد.")
            refreshLanguagePacks()
        }
    }

    fun downloadTranslateModel(mlKitCode: String) {
        viewModelScope.launch {
            downloadingTranslate.update { it + mlKitCode }
            refreshLanguagePacks()
            val result = translationEngine.downloadModel(
                mlKitCode = mlKitCode,
                allowCellular = _uiState.value.allowCellularDownloads
            )
            downloadingTranslate.update { it - mlKitCode }
            result.onSuccess {
                _messages.tryEmit("مدل ترجمه آفلاین با موفقیت دانلود شد.")
            }.onFailure { e ->
                _messages.tryEmit(e.localizedMessage ?: "دانلود مدل ترجمه ناموفق بود.")
            }
            refreshLanguagePacks()
        }
    }

    fun deleteTranslateModel(mlKitCode: String) {
        viewModelScope.launch {
            translationEngine.deleteModel(mlKitCode)
            _messages.tryEmit("مدل ترجمه حذف شد.")
            refreshLanguagePacks()
        }
    }

    suspend fun loadPhotoAndPublishSession(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val ctx = getApplication<Application>()
            val bitmap: Bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(ctx.contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.isMutableRequired = false
                }
            } else {
                @Suppress("DEPRECATION")
                android.provider.MediaStore.Images.Media.getBitmap(ctx.contentResolver, uri)
            }
            OverlaySessionStore.publish(
                bitmap = bitmap,
                source = CaptureSource.PHOTO_PICKER,
                isProtectedOrUnavailable = false
            )
            true
        }.getOrElse {
            _messages.tryEmit("امکان باز کردن تصویر انتخاب‌شده وجود نداشت.")
            false
        }
    }

    private fun checkDefaultAssistant(context: Context): Boolean {
        val isRoleHeld = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            roleManager != null &&
                roleManager.isRoleAvailable(RoleManager.ROLE_ASSISTANT) &&
                roleManager.isRoleHeld(RoleManager.ROLE_ASSISTANT)
        } else {
            false
        }
        if (isRoleHeld) return true

        val isVoiceActive = runCatching {
            VoiceInteractionService.isActiveService(
                context,
                ComponentName(context, CircleVoiceInteractionService::class.java)
            )
        }.getOrDefault(false)
        if (isVoiceActive) return true

        val assistantSetting = Settings.Secure.getString(
            context.contentResolver,
            "assistant"
        ).orEmpty()
        return assistantSetting.contains(context.packageName)
    }
}
