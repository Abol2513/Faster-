package com.example.domain.model

import android.graphics.RectF

data class OcrWord(
    val index: Int,
    val text: String,
    val normalizedBounds: RectF,
    val lineIndex: Int,
    val paragraphIndex: Int
)

data class OcrLine(
    val index: Int,
    val text: String,
    val normalizedBounds: RectF,
    val words: List<OcrWord>,
    val paragraphIndex: Int
)

enum class OcrEngineSource {
    TESSERACT,
    ASSIST_STRUCTURE,
    COMBINED
}

data class OcrResult(
    val fullText: String,
    val lines: List<OcrLine>,
    val words: List<OcrWord>,
    val engineSource: OcrEngineSource
)

enum class CaptureSource {
    ASSISTANT_SCREENSHOT,
    MEDIA_PROJECTION,
    PHOTO_PICKER,
    ASSIST_TEXT_ONLY
}

enum class TranslationEngineMode(val key: String, val persianLabel: String) {
    SMART_HYBRID("smart", "هوشمند (آنلاین + آفلاین)"),
    ONLINE_ONLY("online", "فقط آنلاین"),
    OFFLINE_ONLY("offline", "فقط آفلاین");

    companion object {
        fun fromKey(key: String?): TranslationEngineMode {
            return entries.firstOrNull { it.key == key } ?: SMART_HYBRID
        }
    }
}

enum class UsedTranslationEngine(val badgeText: String) {
    ONLINE_GEMINI("ترجمه آنلاین (Gemini)"),
    ONLINE_FREE("ترجمه آنلاین"),
    OFFLINE_MLKIT("ترجمه آفلاین")
}

data class PrewarmStatus(
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val currentLanguageCode: String? = null,
    val completedCount: Int = 0,
    val totalCount: Int = LanguageCatalog.PREWARM_CORE_LANGUAGE_CODES.size,
    val lastError: String? = null
)

data class SupportedLanguage(
    val mlKitCode: String,
    val tessCode: String?,
    val persianName: String,
    val nativeName: String,
    val isRtl: Boolean,
    val ocrPackSizeMb: Float,
    val translateModelSizeMb: Int,
    val isOcrBundled: Boolean = false
)

data class LanguagePackStatus(
    val language: SupportedLanguage,
    val isOcrInstalled: Boolean,
    val isTranslateModelInstalled: Boolean,
    val isTranslateBuiltIn: Boolean,
    val isDownloadingOcr: Boolean = false,
    val isDownloadingTranslate: Boolean = false
)

object LanguageCatalog {
    const val AUTO_DETECT_CODE = "auto"

    val PREWARM_CORE_LANGUAGE_CODES: List<String> = listOf(
        "fa", "fr", "es", "de", "ar", "tr", "ru", "it", "pt"
    )

    val supportedLanguages: List<SupportedLanguage> = listOf(
        SupportedLanguage(
            mlKitCode = "fa",
            tessCode = "fas",
            persianName = "فارسی",
            nativeName = "فارسی",
            isRtl = true,
            ocrPackSizeMb = 0.4f,
            translateModelSizeMb = 30,
            isOcrBundled = true
        ),
        SupportedLanguage(
            mlKitCode = "en",
            tessCode = "eng",
            persianName = "انگلیسی",
            nativeName = "English",
            isRtl = false,
            ocrPackSizeMb = 4.0f,
            translateModelSizeMb = 0,
            isOcrBundled = true
        ),
        SupportedLanguage(
            mlKitCode = "fr",
            tessCode = "fra",
            persianName = "فرانسوی",
            nativeName = "Français",
            isRtl = false,
            ocrPackSizeMb = 1.1f,
            translateModelSizeMb = 30,
            isOcrBundled = true
        ),
        SupportedLanguage(
            mlKitCode = "es",
            tessCode = "spa",
            persianName = "اسپانیایی",
            nativeName = "Español",
            isRtl = false,
            ocrPackSizeMb = 2.2f,
            translateModelSizeMb = 30,
            isOcrBundled = true
        ),
        SupportedLanguage(
            mlKitCode = "de",
            tessCode = "deu",
            persianName = "آلمانی",
            nativeName = "Deutsch",
            isRtl = false,
            ocrPackSizeMb = 1.5f,
            translateModelSizeMb = 30,
            isOcrBundled = true
        ),
        SupportedLanguage(
            mlKitCode = "ar",
            tessCode = "ara",
            persianName = "عربی",
            nativeName = "العربية",
            isRtl = true,
            ocrPackSizeMb = 1.4f,
            translateModelSizeMb = 30,
            isOcrBundled = true
        ),
        SupportedLanguage(
            mlKitCode = "tr",
            tessCode = "tur",
            persianName = "ترکی استانبولی",
            nativeName = "Türkçe",
            isRtl = false,
            ocrPackSizeMb = 4.4f,
            translateModelSizeMb = 30,
            isOcrBundled = true
        ),
        SupportedLanguage(
            mlKitCode = "ru",
            tessCode = "rus",
            persianName = "روسی",
            nativeName = "Русский",
            isRtl = false,
            ocrPackSizeMb = 3.7f,
            translateModelSizeMb = 30,
            isOcrBundled = true
        ),
        SupportedLanguage(
            mlKitCode = "it",
            tessCode = "ita",
            persianName = "ایتالیایی",
            nativeName = "Italiano",
            isRtl = false,
            ocrPackSizeMb = 2.6f,
            translateModelSizeMb = 30,
            isOcrBundled = true
        ),
        SupportedLanguage(
            mlKitCode = "pt",
            tessCode = "por",
            persianName = "پرتغالی",
            nativeName = "Português",
            isRtl = false,
            ocrPackSizeMb = 1.9f,
            translateModelSizeMb = 30,
            isOcrBundled = true
        ),
        SupportedLanguage(
            mlKitCode = "zh",
            tessCode = "chi_sim",
            persianName = "چینی ساده",
            nativeName = "中文",
            isRtl = false,
            ocrPackSizeMb = 2.4f,
            translateModelSizeMb = 35,
            isOcrBundled = false
        ),
        SupportedLanguage(
            mlKitCode = "ja",
            tessCode = "jpn",
            persianName = "ژاپنی",
            nativeName = "日本語",
            isRtl = false,
            ocrPackSizeMb = 2.3f,
            translateModelSizeMb = 35,
            isOcrBundled = false
        ),
        SupportedLanguage(
            mlKitCode = "ko",
            tessCode = "kor",
            persianName = "کره‌ای",
            nativeName = "한국어",
            isRtl = false,
            ocrPackSizeMb = 1.8f,
            translateModelSizeMb = 32,
            isOcrBundled = false
        ),
        SupportedLanguage(
            mlKitCode = "hi",
            tessCode = "hin",
            persianName = "هندی",
            nativeName = "हिन्दी",
            isRtl = false,
            ocrPackSizeMb = 1.4f,
            translateModelSizeMb = 30,
            isOcrBundled = false
        )
    )

    fun findByMlKitCode(code: String): SupportedLanguage? =
        supportedLanguages.firstOrNull { it.mlKitCode.equals(code, ignoreCase = true) }

    fun displayNameForCode(code: String): String {
        if (code == AUTO_DETECT_CODE) return "تشخیص خودکار"
        return findByMlKitCode(code)?.persianName ?: code.uppercase()
    }

    fun isRtlLanguage(code: String): Boolean {
        return findByMlKitCode(code)?.isRtl ?: false
    }
}
