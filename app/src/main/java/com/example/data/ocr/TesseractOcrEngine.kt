package com.example.data.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.RectF
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.example.domain.model.OcrEngineSource
import com.example.domain.model.OcrLine
import com.example.domain.model.OcrResult
import com.example.domain.model.OcrWord
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class TesseractOcrEngine(private val context: Context) {

    companion object {
        private const val TAG = "TesseractOcrEngine"
        private const val TESSDATA_URL_PREFIX =
            "https://github.com/tesseract-ocr/tessdata_fast/raw/main/"
        private val BUNDLED_LANGUAGES = listOf(
            "eng", "fas", "fra", "spa", "deu", "ara", "tur", "rus", "ita", "por"
        )
        private const val MAX_OCR_DIMENSION = 1920
        private const val MIN_CROP_OCR_DIMENSION = 420
        private const val MAX_CROP_OCR_DIMENSION = 1440
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val tessDispatcher = Dispatchers.IO.limitedParallelism(1)
    private val setupMutex = Mutex()
    private val ocrMutex = Mutex()

    private val tesseractRootDir: File
        get() = File(context.filesDir, "tesseract")

    private val tessDataDir: File
        get() = File(tesseractRootDir, "tessdata")

    suspend fun ensureBundledTessData() = withContext(Dispatchers.IO) {
        setupMutex.withLock {
            if (!tessDataDir.exists()) {
                tessDataDir.mkdirs()
            }
            for (lang in BUNDLED_LANGUAGES) {
                val fileName = "$lang.traineddata"
                val outFile = File(tessDataDir, fileName)
                if (!outFile.exists() || outFile.length() < 10_000L) {
                    val tempFile = File(tessDataDir, "$fileName.tmp")
                    runCatching {
                        context.assets.open("tessdata/$fileName").use { input ->
                            FileOutputStream(tempFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                        if (tempFile.length() >= 10_000L) {
                            if (outFile.exists()) outFile.delete()
                            if (!tempFile.renameTo(outFile)) {
                                tempFile.copyTo(outFile, overwrite = true)
                                tempFile.delete()
                            }
                            Log.d(TAG, "Copied bundled $fileName (${outFile.length()} bytes)")
                        } else {
                            tempFile.delete()
                        }
                    }.onFailure { e ->
                        tempFile.delete()
                        Log.w(TAG, "Failed to copy bundled asset $fileName", e)
                    }
                }
            }
        }
    }

    fun getInstalledOcrCodes(): Set<String> {
        val installed = mutableSetOf<String>()
        installed.addAll(BUNDLED_LANGUAGES)
        tessDataDir.listFiles()?.forEach { file ->
            if (file.isFile && file.name.endsWith(".traineddata") && file.length() > 10_000L) {
                installed.add(file.name.removeSuffix(".traineddata"))
            }
        }
        return installed
    }

    fun isOcrLanguageInstalled(tessCode: String): Boolean {
        if (tessCode in BUNDLED_LANGUAGES) return true
        val file = File(tessDataDir, "$tessCode.traineddata")
        return file.exists() && file.length() > 10_000L
    }

    suspend fun downloadOcrLanguage(tessCode: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (!isNetworkAvailable()) {
                throw IOException("اتصال اینترنت برقرار نیست. برای دانلود بسته زبان آفلاین به اینترنت نیاز است.")
            }
            ensureBundledTessData()
            val targetFile = File(tessDataDir, "$tessCode.traineddata")
            val tempFile = File(tessDataDir, "$tessCode.traineddata.tmp")
            val url = URL("$TESSDATA_URL_PREFIX$tessCode.traineddata")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = true
            }
            try {
                conn.connect()
                if (conn.responseCode !in 200..299) {
                    throw IOException("خطا در دریافت فایل زبان (کد ${conn.responseCode})")
                }
                conn.inputStream.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }
                if (tempFile.length() < 10_000L) {
                    tempFile.delete()
                    throw IOException("فایل دریافت‌شده معتبر نیست.")
                }
                if (targetFile.exists()) targetFile.delete()
                if (!tempFile.renameTo(targetFile)) {
                    tempFile.copyTo(targetFile, overwrite = true)
                    tempFile.delete()
                }
            } finally {
                conn.disconnect()
            }
        }
    }

    suspend fun deleteOcrLanguage(tessCode: String): Boolean = withContext(Dispatchers.IO) {
        if (tessCode in BUNDLED_LANGUAGES) return@withContext false
        val file = File(tessDataDir, "$tessCode.traineddata")
        if (file.exists()) file.delete() else false
    }

    suspend fun recognize(
        sourceBitmap: Bitmap,
        requestedTessCodes: Set<String>
    ): OcrResult = withContext(tessDispatcher) {
        ensureBundledTessData()

        val installed = getInstalledOcrCodes()
        val usableCodes = requestedTessCodes.filter { it in installed }
            .ifEmpty { listOf("fas", "eng") }
        val langExpression = usableCodes.joinToString("+")

        ocrMutex.withLock {
            if (sourceBitmap.isRecycled) {
                return@withLock OcrResult("", emptyList(), emptyList(), OcrEngineSource.TESSERACT)
            }

            val prepared = prepareBitmapForOcr(sourceBitmap)
            val imgW = prepared.width.toFloat().coerceAtLeast(1f)
            val imgH = prepared.height.toFloat().coerceAtLeast(1f)
            val tess = TessBaseAPI()

            try {
                val initialized = tess.init(
                    tesseractRootDir.absolutePath + File.separator,
                    langExpression,
                    TessBaseAPI.OEM_LSTM_ONLY
                )
                if (!initialized) {
                    throw IllegalStateException("موتور تشخیص متن برای زبان‌های $langExpression آماده نشد.")
                }
                tess.pageSegMode = TessBaseAPI.PageSegMode.PSM_AUTO
                tess.setImage(prepared)

                val rawFullText = tess.utF8Text.orEmpty().trim()
                val iterator = tess.resultIterator

                val lineBoxes = mutableListOf<Pair<String, Rect>>()
                val wordBoxes = mutableListOf<Pair<String, Rect>>()

                if (iterator != null) {
                    try {
                        iterator.begin()
                        do {
                            val lineText = iterator.getUTF8Text(TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE)
                            val lineRect = iterator.getBoundingRect(TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE)
                            if (!lineText.isNullOrBlank() && lineRect != null && !lineRect.isEmpty) {
                                lineBoxes.add(lineText.trim() to lineRect)
                            }
                        } while (iterator.next(TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE))

                        iterator.begin()
                        do {
                            val wordText = iterator.getUTF8Text(TessBaseAPI.PageIteratorLevel.RIL_WORD)
                            val wordRect = iterator.getBoundingRect(TessBaseAPI.PageIteratorLevel.RIL_WORD)
                            if (!wordText.isNullOrBlank() && wordRect != null && !wordRect.isEmpty) {
                                wordBoxes.add(wordText.trim() to wordRect)
                            }
                        } while (iterator.next(TessBaseAPI.PageIteratorLevel.RIL_WORD))
                    } finally {
                        iterator.delete()
                    }
                }

                val words = mutableListOf<OcrWord>()
                val lines = mutableListOf<OcrLine>()

                if (lineBoxes.isNotEmpty()) {
                    val wordsByLine = Array(lineBoxes.size) { mutableListOf<OcrWord>() }

                    wordBoxes.forEachIndexed { wordIdx, (wordText, wordRect) ->
                        val cx = wordRect.exactCenterX()
                        val cy = wordRect.exactCenterY()
                        var matchedLineIdx = lineBoxes.indexOfFirst { (_, lr) ->
                            cx >= lr.left - 8 && cx <= lr.right + 8 &&
                                cy >= lr.top - 8 && cy <= lr.bottom + 8
                        }
                        if (matchedLineIdx < 0) {
                            matchedLineIdx = lineBoxes.indices.minByOrNull { idx ->
                                val lr = lineBoxes[idx].second
                                val dy = cy - lr.exactCenterY()
                                val dx = cx - lr.exactCenterX()
                                dy * dy * 4f + dx * dx
                            } ?: 0
                        }
                        val ocrWord = OcrWord(
                            index = wordIdx,
                            text = wordText,
                            normalizedBounds = RectF(
                                (wordRect.left / imgW).coerceIn(0f, 1f),
                                (wordRect.top / imgH).coerceIn(0f, 1f),
                                (wordRect.right / imgW).coerceIn(0f, 1f),
                                (wordRect.bottom / imgH).coerceIn(0f, 1f)
                            ),
                            lineIndex = matchedLineIdx,
                            paragraphIndex = matchedLineIdx
                        )
                        words.add(ocrWord)
                        wordsByLine[matchedLineIdx].add(ocrWord)
                    }

                    lineBoxes.forEachIndexed { lineIdx, (lineText, lineRect) ->
                        lines.add(
                            OcrLine(
                                index = lineIdx,
                                text = lineText,
                                normalizedBounds = RectF(
                                    (lineRect.left / imgW).coerceIn(0f, 1f),
                                    (lineRect.top / imgH).coerceIn(0f, 1f),
                                    (lineRect.right / imgW).coerceIn(0f, 1f),
                                    (lineRect.bottom / imgH).coerceIn(0f, 1f)
                                ),
                                words = wordsByLine[lineIdx],
                                paragraphIndex = lineIdx
                            )
                        )
                    }
                } else if (wordBoxes.isNotEmpty()) {
                    wordBoxes.forEachIndexed { idx, (wText, wRect) ->
                        words.add(
                            OcrWord(
                                index = idx,
                                text = wText,
                                normalizedBounds = RectF(
                                    (wRect.left / imgW).coerceIn(0f, 1f),
                                    (wRect.top / imgH).coerceIn(0f, 1f),
                                    (wRect.right / imgW).coerceIn(0f, 1f),
                                    (wRect.bottom / imgH).coerceIn(0f, 1f)
                                ),
                                lineIndex = 0,
                                paragraphIndex = 0
                            )
                        )
                    }
                    lines.add(
                        OcrLine(
                            index = 0,
                            text = words.joinToString(" ") { it.text },
                            normalizedBounds = RectF(0.05f, 0.05f, 0.95f, 0.95f),
                            words = words,
                            paragraphIndex = 0
                        )
                    )
                }

                val combinedText = if (lines.isNotEmpty()) {
                    lines.joinToString("\n") { it.text }
                } else {
                    rawFullText
                }

                OcrResult(
                    fullText = combinedText,
                    lines = lines,
                    words = words,
                    engineSource = OcrEngineSource.TESSERACT
                )
            } finally {
                tess.recycle()
                if (prepared !== sourceBitmap && !prepared.isRecycled) {
                    prepared.recycle()
                }
            }
        }
    }

    suspend fun recognizeCroppedRegion(
        sourceBitmap: Bitmap,
        normalizedBounds: RectF,
        requestedTessCodes: Set<String>
    ): String = withContext(tessDispatcher) {
        ensureBundledTessData()

        val installed = getInstalledOcrCodes()
        val usableCodes = requestedTessCodes.filter { it in installed }
            .ifEmpty { listOf("fas", "eng") }
        val langExpression = usableCodes.joinToString("+")

        ocrMutex.withLock {
            if (sourceBitmap.isRecycled || sourceBitmap.width < 4 || sourceBitmap.height < 4) {
                return@withLock ""
            }

            val bmpW = sourceBitmap.width
            val bmpH = sourceBitmap.height

            val rawLeft = (normalizedBounds.left * bmpW).roundToInt()
            val rawTop = (normalizedBounds.top * bmpH).roundToInt()
            val rawRight = (normalizedBounds.right * bmpW).roundToInt()
            val rawBottom = (normalizedBounds.bottom * bmpH).roundToInt()

            val spanW = (rawRight - rawLeft).coerceAtLeast(24)
            val spanH = (rawBottom - rawTop).coerceAtLeast(24)
            val padX = max(16, (spanW * 0.06f).roundToInt())
            val padY = max(16, (spanH * 0.08f).roundToInt())

            val cropLeft = (rawLeft - padX).coerceIn(0, bmpW - 2)
            val cropTop = (rawTop - padY).coerceIn(0, bmpH - 2)
            val cropRight = (rawRight + padX).coerceIn(cropLeft + 2, bmpW)
            val cropBottom = (rawBottom + padY).coerceIn(cropTop + 2, bmpH)

            val cropW = (cropRight - cropLeft).coerceAtLeast(2)
            val cropH = (cropBottom - cropTop).coerceAtLeast(2)

            val cropped = Bitmap.createBitmap(sourceBitmap, cropLeft, cropTop, cropW, cropH)
            val preprocessed = preprocessCropForOcr(cropped)
            val tess = TessBaseAPI()

            try {
                val initialized = tess.init(
                    tesseractRootDir.absolutePath + File.separator,
                    langExpression,
                    TessBaseAPI.OEM_LSTM_ONLY
                )
                if (!initialized) return@withLock ""

                tess.pageSegMode = TessBaseAPI.PageSegMode.PSM_AUTO
                tess.setImage(preprocessed)
                tess.utF8Text.orEmpty().trim()
            } finally {
                tess.recycle()
                if (preprocessed !== cropped && !preprocessed.isRecycled) {
                    preprocessed.recycle()
                }
                if (!cropped.isRecycled) {
                    cropped.recycle()
                }
            }
        }
    }

    private fun prepareBitmapForOcr(source: Bitmap): Bitmap {
        val maxSide = max(source.width, source.height)
        val scaled = if (maxSide > MAX_OCR_DIMENSION) {
            val scale = MAX_OCR_DIMENSION.toFloat() / maxSide.toFloat()
            val newW = (source.width * scale).roundToInt().coerceAtLeast(1)
            val newH = (source.height * scale).roundToInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(source, newW, newH, true)
        } else {
            source
        }

        return if (scaled.config == Bitmap.Config.ARGB_8888) {
            scaled
        } else {
            val converted = scaled.copy(Bitmap.Config.ARGB_8888, false)
            if (scaled !== source && !scaled.isRecycled) {
                scaled.recycle()
            }
            converted
        }
    }

    private fun preprocessCropForOcr(crop: Bitmap): Bitmap {
        val minDim = min(crop.width, crop.height)
        val maxDim = max(crop.width, crop.height)

        val resized = if (minDim < MIN_CROP_OCR_DIMENSION && maxDim < MAX_CROP_OCR_DIMENSION) {
            val desiredScale = (MIN_CROP_OCR_DIMENSION.toFloat() / minDim.coerceAtLeast(1)).coerceIn(1.5f, 2.5f)
            val maxAllowedScale = MAX_CROP_OCR_DIMENSION.toFloat() / maxDim.coerceAtLeast(1)
            val finalScale = min(desiredScale, maxAllowedScale).coerceAtLeast(1f)
            val newW = (crop.width * finalScale).roundToInt().coerceAtLeast(1)
            val newH = (crop.height * finalScale).roundToInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(crop, newW, newH, true)
        } else {
            crop
        }

        return if (resized.config == Bitmap.Config.ARGB_8888) {
            resized
        } else {
            val converted = resized.copy(Bitmap.Config.ARGB_8888, false)
            if (resized !== crop && !resized.isRecycled) {
                resized.recycle()
            }
            converted
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
