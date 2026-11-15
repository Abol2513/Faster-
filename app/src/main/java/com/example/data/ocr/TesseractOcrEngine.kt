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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.max
import kotlin.math.roundToInt

class TesseractOcrEngine(private val context: Context) {

    companion object {
        private const val TAG = "TesseractOcrEngine"
        private const val TESSDATA_URL_PREFIX =
            "https://github.com/tesseract-ocr/tessdata_fast/raw/main/"
        private val BUNDLED_LANGUAGES = listOf("fas", "eng")
        private const val MAX_OCR_DIMENSION = 1920
    }

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
                if (!outFile.exists() || outFile.length() == 0L) {
                    context.assets.open("tessdata/$fileName").use { input ->
                        FileOutputStream(outFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    Log.d(TAG, "Copied bundled $fileName to ${outFile.absolutePath}")
                }
            }
        }
    }

    fun getInstalledOcrCodes(): Set<String> {
        val installed = mutableSetOf<String>()
        installed.addAll(BUNDLED_LANGUAGES)
        tessDataDir.listFiles()?.forEach { file ->
            if (file.isFile && file.name.endsWith(".traineddata") && file.length() > 0L) {
                installed.add(file.name.removeSuffix(".traineddata"))
            }
        }
        return installed
    }

    fun isOcrLanguageInstalled(tessCode: String): Boolean {
        if (tessCode in BUNDLED_LANGUAGES) return true
        val file = File(tessDataDir, "$tessCode.traineddata")
        return file.exists() && file.length() > 0L
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
    ): OcrResult = withContext(Dispatchers.Default) {
        ensureBundledTessData()

        val installed = getInstalledOcrCodes()
        val usableCodes = requestedTessCodes.filter { it in installed }
            .ifEmpty { listOf("fas", "eng") }
        val langExpression = usableCodes.joinToString("+")

        val prepared = prepareBitmapForOcr(sourceBitmap)
        val imgW = prepared.width.toFloat().coerceAtLeast(1f)
        val imgH = prepared.height.toFloat().coerceAtLeast(1f)

        ocrMutex.withLock {
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

                // Trigger recognition
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
                        val word = OcrWord(
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
                        words.add(word)
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

    private fun isNetworkAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
