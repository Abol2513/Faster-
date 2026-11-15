package com.example.ui.overlay

import android.app.Application
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ocr.TesseractOcrEngine
import com.example.data.prefs.AppPreferencesRepository
import com.example.data.session.OverlaySessionStore
import com.example.data.translation.OfflineModelMissingException
import com.example.data.translation.OfflineTranslationEngine
import com.example.domain.model.CaptureSource
import com.example.domain.model.LanguageCatalog
import com.example.domain.model.OcrEngineSource
import com.example.domain.model.OcrLine
import com.example.domain.model.OcrResult
import com.example.domain.model.OcrWord
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

sealed interface OcrStage {
    data object Scanning : OcrStage
    data class Ready(val result: OcrResult) : OcrStage
    data object Empty : OcrStage
    data object UnavailableOrProtected : OcrStage
}

sealed interface TranslationStage {
    data object Idle : TranslationStage
    data object Translating : TranslationStage
    data object DownloadingModels : TranslationStage
    data class Success(
        val translatedText: String,
        val detectedSourceCode: String,
        val wasSameLanguage: Boolean
    ) : TranslationStage
    data class MissingModel(
        val missingCodes: List<String>,
        val message: String
    ) : TranslationStage
    data class Error(val message: String) : TranslationStage
}

data class OverlayUiState(
    val sessionId: Long = 0L,
    val screenshot: Bitmap? = null,
    val captureSource: CaptureSource = CaptureSource.ASSISTANT_SCREENSHOT,
    val ocrStage: OcrStage = OcrStage.Scanning,
    val isRefiningCroppedOcr: Boolean = false,
    val selectedWordIndices: Set<Int> = emptySet(),
    val selectedLineIndices: Set<Int> = emptySet(),
    val editableSourceText: String = "",
    val sourceLangCode: String = LanguageCatalog.AUTO_DETECT_CODE,
    val detectedSourceCode: String? = null,
    val targetLangCode: String = "fa",
    val translationStage: TranslationStage = TranslationStage.Idle,
    val isSheetExpanded: Boolean = false
)

class OverlayViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "OverlayViewModel"
    }

    private val prefsRepo = AppPreferencesRepository(application)
    private val ocrEngine = TesseractOcrEngine(application)
    private val translationEngine = OfflineTranslationEngine(application)

    private val _uiState = MutableStateFlow(OverlayUiState())
    val uiState: StateFlow<OverlayUiState> = _uiState.asStateFlow()

    private var ocrJob: Job? = null
    private var croppedOcrJob: Job? = null
    private var translateJob: Job? = null
    private var autoTranslateEnabled = true
    private var allowCellularDownloads = true

    init {
        viewModelScope.launch {
            val defaultTarget = prefsRepo.targetLanguageCode.first()
            autoTranslateEnabled = prefsRepo.autoTranslateOnSelect.first()
            allowCellularDownloads = prefsRepo.allowCellularDownloads.first()
            _uiState.update { it.copy(targetLangCode = defaultTarget) }

            OverlaySessionStore.payload.collect { payload ->
                if (payload != null && payload.id != _uiState.value.sessionId) {
                    startSession(
                        sessionId = payload.id,
                        bitmap = payload.bitmap,
                        assistLines = payload.assistLines,
                        source = payload.source,
                        isProtectedOrUnavailable = payload.isProtectedOrUnavailable
                    )
                }
            }
        }
    }

    private fun startSession(
        sessionId: Long,
        bitmap: Bitmap?,
        assistLines: List<OcrLine>,
        source: CaptureSource,
        isProtectedOrUnavailable: Boolean
    ) {
        ocrJob?.cancel()
        croppedOcrJob?.cancel()
        translateJob?.cancel()

        _uiState.update {
            it.copy(
                sessionId = sessionId,
                screenshot = bitmap,
                captureSource = source,
                ocrStage = if (isProtectedOrUnavailable && bitmap == null && assistLines.isEmpty()) {
                    OcrStage.UnavailableOrProtected
                } else {
                    OcrStage.Scanning
                },
                isRefiningCroppedOcr = false,
                selectedWordIndices = emptySet(),
                selectedLineIndices = emptySet(),
                editableSourceText = "",
                sourceLangCode = LanguageCatalog.AUTO_DETECT_CODE,
                detectedSourceCode = null,
                translationStage = TranslationStage.Idle,
                isSheetExpanded = isProtectedOrUnavailable || bitmap == null
            )
        }

        if (isProtectedOrUnavailable && bitmap == null && assistLines.isEmpty()) {
            return
        }

        ocrJob = viewModelScope.launch {
            try {
                val activeOcr = prefsRepo.activeOcrLanguages.first()
                val result: OcrResult = if (bitmap != null) {
                    val tessResult = ocrEngine.recognize(bitmap, activeOcr)
                    if (tessResult.words.isNotEmpty() || assistLines.isEmpty()) {
                        tessResult
                    } else {
                        buildResultFromAssistLines(assistLines)
                    }
                } else {
                    buildResultFromAssistLines(assistLines)
                }

                if (result.words.isEmpty() && result.fullText.isBlank()) {
                    _uiState.update {
                        it.copy(
                            ocrStage = OcrStage.Empty,
                            isSheetExpanded = bitmap == null
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            ocrStage = OcrStage.Ready(result),
                            isSheetExpanded = bitmap == null
                        )
                    }
                    if (bitmap == null) {
                        selectAll()
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "OCR failed", e)
                if (assistLines.isNotEmpty()) {
                    val fallback = buildResultFromAssistLines(assistLines)
                    _uiState.update {
                        it.copy(
                            ocrStage = OcrStage.Ready(fallback),
                            isSheetExpanded = true
                        )
                    }
                    selectAll()
                } else {
                    _uiState.update {
                        it.copy(
                            ocrStage = OcrStage.Empty,
                            isSheetExpanded = true
                        )
                    }
                }
            }
        }
    }

    private fun buildResultFromAssistLines(lines: List<OcrLine>): OcrResult {
        val allWords = mutableListOf<OcrWord>()
        lines.forEach { line -> allWords.addAll(line.words) }
        return OcrResult(
            fullText = lines.joinToString("\n") { it.text },
            lines = lines,
            words = allWords,
            engineSource = OcrEngineSource.ASSIST_STRUCTURE
        )
    }

    fun selectByGesture(normalizedPath: List<Offset>) {
        if (normalizedPath.isEmpty()) return

        val ready = _uiState.value.ocrStage as? OcrStage.Ready
        val words = ready?.result?.words.orEmpty()

        // Tap gesture: select line/word immediately from full-screen OCR
        if (normalizedPath.size <= 3) {
            if (ready == null) return
            val pt = normalizedPath.first()
            val hitLine = ready.result.lines.firstOrNull { line ->
                val r = line.normalizedBounds
                pt.x in (r.left - 0.02f)..(r.right + 0.02f) &&
                    pt.y in (r.top - 0.02f)..(r.bottom + 0.02f)
            }
            if (hitLine != null) {
                val tappedWordIds = hitLine.words.map { it.index }.toSet()
                if (tappedWordIds.isNotEmpty()) {
                    applyWordSelection(ready.result, tappedWordIds)
                }
            }
            return
        }

        // Circle / Lasso / Box drag gesture: compute exact normalized bounds
        var minX = 1f
        var minY = 1f
        var maxX = 0f
        var maxY = 0f
        for (p in normalizedPath) {
            minX = min(minX, p.x)
            minY = min(minY, p.y)
            maxX = max(maxX, p.x)
            maxY = max(maxY, p.y)
        }

        val pad = 0.015f
        val gestureRect = RectF(
            (minX - pad).coerceIn(0f, 1f),
            (minY - pad).coerceIn(0f, 1f),
            (maxX + pad).coerceIn(0f, 1f),
            (maxY + pad).coerceIn(0f, 1f)
        )
        val isBoxOrCircle = gestureRect.height() > 0.035f && gestureRect.width() > 0.035f

        val selectedWords = mutableSetOf<Int>()
        for (word in words) {
            val wb = word.normalizedBounds
            val cx = wb.centerX()
            val cy = wb.centerY()
            val insideBox = isBoxOrCircle &&
                cx in gestureRect.left..gestureRect.right &&
                cy in gestureRect.top..gestureRect.bottom
            val touchesStroke = normalizedPath.any { p ->
                p.x in (wb.left - 0.02f)..(wb.right + 0.02f) &&
                    p.y in (wb.top - 0.02f)..(wb.bottom + 0.02f)
            }
            if (insideBox || touchesStroke) {
                selectedWords.add(word.index)
            }
        }

        if (ready != null && selectedWords.isNotEmpty()) {
            applyWordSelection(ready.result, selectedWords)
        }

        // Two-stage high-res cropped OCR pass on the drawn circle/box region for crisp accuracy
        val bitmap = _uiState.value.screenshot
        if (bitmap != null && !bitmap.isRecycled && (gestureRect.width() > 0.02f || gestureRect.height() > 0.02f)) {
            croppedOcrJob?.cancel()
            croppedOcrJob = viewModelScope.launch {
                _uiState.update {
                    it.copy(
                        isRefiningCroppedOcr = true,
                        isSheetExpanded = true
                    )
                }
                try {
                    val activeOcr = prefsRepo.activeOcrLanguages.first()
                    val refinedText = ocrEngine.recognizeCroppedRegion(
                        sourceBitmap = bitmap,
                        normalizedBounds = gestureRect,
                        requestedTessCodes = activeOcr
                    )
                    if (refinedText.isNotBlank()) {
                        _uiState.update {
                            it.copy(
                                editableSourceText = refinedText,
                                isRefiningCroppedOcr = false,
                                isSheetExpanded = true
                            )
                        }
                        if (autoTranslateEnabled) {
                            runTranslation(refinedText, autoDownloadIfOnline = true)
                        }
                    } else {
                        _uiState.update { it.copy(isRefiningCroppedOcr = false) }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Cropped OCR pass failed", e)
                    _uiState.update { it.copy(isRefiningCroppedOcr = false) }
                }
            }
        }
    }

    fun selectAll() {
        val ready = _uiState.value.ocrStage as? OcrStage.Ready ?: return
        val allWordIds = ready.result.words.map { it.index }.toSet()
        applyWordSelection(ready.result, allWordIds)
    }

    fun clearSelection() {
        croppedOcrJob?.cancel()
        translateJob?.cancel()
        _uiState.update {
            it.copy(
                selectedWordIndices = emptySet(),
                selectedLineIndices = emptySet(),
                editableSourceText = "",
                isRefiningCroppedOcr = false,
                translationStage = TranslationStage.Idle,
                isSheetExpanded = false
            )
        }
    }

    fun updateEditableText(newText: String) {
        _uiState.update { it.copy(editableSourceText = newText) }
    }

    fun setSourceLanguage(code: String) {
        _uiState.update { it.copy(sourceLangCode = code) }
        retranslate()
    }

    fun setTargetLanguage(code: String) {
        _uiState.update { it.copy(targetLangCode = code) }
        viewModelScope.launch { prefsRepo.setTargetLanguage(code) }
        retranslate()
    }

    fun swapLanguages() {
        val state = _uiState.value
        val currentSource = if (state.sourceLangCode == LanguageCatalog.AUTO_DETECT_CODE) {
            state.detectedSourceCode ?: "en"
        } else {
            state.sourceLangCode
        }
        val currentTarget = state.targetLangCode
        if (currentSource == currentTarget) return
        _uiState.update {
            it.copy(
                sourceLangCode = currentTarget,
                targetLangCode = currentSource
            )
        }
        retranslate()
    }

    fun openSheetManually() {
        _uiState.update { it.copy(isSheetExpanded = true) }
    }

    fun retranslate() {
        val text = _uiState.value.editableSourceText.trim()
        if (text.isEmpty()) {
            _uiState.update { it.copy(translationStage = TranslationStage.Idle) }
            return
        }
        runTranslation(text, autoDownloadIfOnline = true)
    }

    fun downloadMissingModelsAndRetranslate(missingCodes: List<String>) {
        translateJob?.cancel()
        translateJob = viewModelScope.launch {
            _uiState.update { it.copy(translationStage = TranslationStage.DownloadingModels) }
            try {
                allowCellularDownloads = prefsRepo.allowCellularDownloads.first()
                for (code in missingCodes) {
                    translationEngine.downloadModel(
                        mlKitCode = code,
                        allowCellular = allowCellularDownloads
                    ).getOrThrow()
                }
                val text = _uiState.value.editableSourceText.trim()
                if (text.isNotEmpty()) {
                    runTranslationInternal(text, autoDownloadIfOnline = false)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        translationStage = TranslationStage.Error(
                            e.localizedMessage ?: "دانلود مدل آفلاین ناموفق بود."
                        )
                    )
                }
            }
        }
    }

    private fun applyWordSelection(result: OcrResult, wordIndices: Set<Int>) {
        if (wordIndices.isEmpty()) {
            clearSelection()
            return
        }

        val selectedLines = mutableSetOf<Int>()
        val formattedText = buildString {
            var firstLine = true
            for (line in result.lines) {
                val lineSelectedWords = line.words.filter { it.index in wordIndices }
                if (lineSelectedWords.isNotEmpty()) {
                    selectedLines.add(line.index)
                    if (!firstLine) append('\n')
                    firstLine = false
                    if (lineSelectedWords.size == line.words.size) {
                        append(line.text)
                    } else {
                        append(lineSelectedWords.joinToString(" ") { it.text })
                    }
                }
            }
        }.ifBlank {
            result.words.filter { it.index in wordIndices }.joinToString(" ") { it.text }
        }

        _uiState.update {
            it.copy(
                selectedWordIndices = wordIndices,
                selectedLineIndices = selectedLines,
                editableSourceText = formattedText,
                isSheetExpanded = true
            )
        }

        if (autoTranslateEnabled && formattedText.isNotBlank()) {
            runTranslation(formattedText, autoDownloadIfOnline = true)
        }
    }

    private fun runTranslation(text: String, autoDownloadIfOnline: Boolean) {
        translateJob?.cancel()
        translateJob = viewModelScope.launch {
            runTranslationInternal(text, autoDownloadIfOnline)
        }
    }

    private suspend fun runTranslationInternal(text: String, autoDownloadIfOnline: Boolean) {
        val state = _uiState.value
        allowCellularDownloads = prefsRepo.allowCellularDownloads.first()
        _uiState.update { it.copy(translationStage = TranslationStage.Translating) }
        try {
            val outcome = translationEngine.translate(
                text = text,
                sourceCode = state.sourceLangCode,
                targetCode = state.targetLangCode,
                autoDownloadIfOnline = autoDownloadIfOnline,
                allowCellular = allowCellularDownloads
            )
            _uiState.update {
                it.copy(
                    detectedSourceCode = outcome.detectedSourceCode,
                    translationStage = TranslationStage.Success(
                        translatedText = outcome.translatedText,
                        detectedSourceCode = outcome.detectedSourceCode,
                        wasSameLanguage = outcome.wasSameLanguage
                    )
                )
            }
        } catch (e: OfflineModelMissingException) {
            _uiState.update {
                it.copy(
                    translationStage = TranslationStage.MissingModel(
                        missingCodes = e.missingLanguageCodes,
                        message = e.message ?: "مدل ترجمه آفلاین نیاز به دانلود دارد."
                    )
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Translation failed", e)
            _uiState.update {
                it.copy(
                    translationStage = TranslationStage.Error(
                        e.localizedMessage ?: "خطا در ترجمه متن."
                    )
                )
            }
        }
    }

    override fun onCleared() {
        ocrJob?.cancel()
        croppedOcrJob?.cancel()
        translateJob?.cancel()
        super.onCleared()
    }
}
