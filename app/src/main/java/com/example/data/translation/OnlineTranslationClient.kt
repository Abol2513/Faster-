package com.example.data.translation

import android.util.Log
import com.example.domain.model.LanguageCatalog
import com.example.domain.model.UsedTranslationEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class OnlineTranslationResult(
    val translatedText: String,
    val detectedSourceCode: String?,
    val usedEngine: UsedTranslationEngine
)

class OnlineTranslationClient {

    companion object {
        private const val TAG = "OnlineTranslation"
        private const val LINGVA_BASE = "https://lingva.ml/api/v1"
        private const val MYMEMORY_BASE = "https://api.mymemory.translated.net/get"
        private const val GEMINI_ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"
        private const val MYMEMORY_MAX_CHUNK_CHARS = 440
    }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .writeTimeout(12, TimeUnit.SECONDS)
            .build()
    }

    suspend fun translateOnline(
        text: String,
        sourceCode: String,
        targetCode: String,
        detectedHintCode: String?,
        geminiApiKey: String
    ): OnlineTranslationResult = withContext(Dispatchers.IO) {
        val cleaned = text.trim()
        if (cleaned.isEmpty()) {
            return@withContext OnlineTranslationResult("", sourceCode, UsedTranslationEngine.ONLINE_FREE)
        }

        // 1. Optional user-entered Gemini API key takes priority when non-empty
        if (geminiApiKey.isNotBlank()) {
            runCatching {
                translateWithGemini(cleaned, sourceCode, targetCode, geminiApiKey.trim())
            }.onSuccess { result ->
                if (result.translatedText.isNotBlank()) return@withContext result
            }.onFailure { e ->
                Log.w(TAG, "Gemini online translation failed, falling back to free providers", e)
            }
        }

        // 2. Primary zero-key free provider: Lingva REST API
        val lingvaResult = runCatching {
            translateWithLingva(cleaned, sourceCode, targetCode)
        }
        lingvaResult.onSuccess { result ->
            if (result.translatedText.isNotBlank()) return@withContext result
        }.onFailure { e ->
            Log.w(TAG, "Lingva failed, falling back to MyMemory", e)
        }

        // 3. Automatic zero-key fallback: MyMemory REST API (chunked safely < 450 chars)
        val myMemorySource = resolveConcreteSourceForMyMemory(
            sourceCode = sourceCode,
            detectedHintCode = detectedHintCode,
            targetCode = targetCode
        )
        translateWithMyMemory(cleaned, myMemorySource, targetCode)
    }

    private suspend fun translateWithGemini(
        text: String,
        sourceCode: String,
        targetCode: String,
        apiKey: String
    ): OnlineTranslationResult {
        val targetName = LanguageCatalog.displayNameForCode(targetCode)
        val sourceHint = if (sourceCode == LanguageCatalog.AUTO_DETECT_CODE) {
            "automatically detected source language"
        } else {
            LanguageCatalog.displayNameForCode(sourceCode)
        }

        val systemInstruction = JSONObject().put(
            "parts",
            JSONArray().put(
                JSONObject().put(
                    "text",
                    "You are a professional translator. Translate the user's text from $sourceHint into $targetName ($targetCode). " +
                        "Return ONLY the natural, accurate translation without quotes, explanations, or markdown."
                )
            )
        )

        val payload = JSONObject()
            .put("system_instruction", systemInstruction)
            .put(
                "contents",
                JSONArray().put(
                    JSONObject().put(
                        "parts",
                        JSONArray().put(JSONObject().put("text", text))
                    )
                )
            )
            .put(
                "generationConfig",
                JSONObject().put("temperature", 0.2)
            )

        val request = Request.Builder()
            .url("$GEMINI_ENDPOINT?key=${urlEncode(apiKey)}")
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        val body = executeRequest(request)
        val json = JSONObject(body)
        val candidates = json.optJSONArray("candidates")
            ?: throw IOException("پاسخی از Gemini دریافت نشد.")
        if (candidates.length() == 0) throw IOException("پاسخ Gemini خالی بود.")

        val parts = candidates.getJSONObject(0)
            .getJSONObject("content")
            .getJSONArray("parts")
        val translated = buildString {
            for (i in 0 until parts.length()) {
                append(parts.getJSONObject(i).optString("text", ""))
            }
        }.trim()

        if (translated.isBlank()) throw IOException("ترجمه Gemini خالی بود.")
        return OnlineTranslationResult(
            translatedText = translated,
            detectedSourceCode = if (sourceCode == LanguageCatalog.AUTO_DETECT_CODE) null else sourceCode,
            usedEngine = UsedTranslationEngine.ONLINE_GEMINI
        )
    }

    private suspend fun translateWithLingva(
        text: String,
        sourceCode: String,
        targetCode: String
    ): OnlineTranslationResult {
        val src = if (sourceCode == LanguageCatalog.AUTO_DETECT_CODE) "auto" else sourceCode
        val encodedText = URLEncoder.encode(text, "UTF-8").replace("+", "%20")
        val url = "$LINGVA_BASE/$src/$targetCode/$encodedText"
        val request = Request.Builder().url(url).get().build()

        val body = executeRequest(request)
        val json = JSONObject(body)
        val translation = json.optString("translation", "").trim()
        if (translation.isBlank()) {
            throw IOException("پاسخ سرویس ترجمه Lingva خالی بود.")
        }
        val detected = json.optJSONObject("info")?.optString("detectedSource")?.takeIf { it.isNotBlank() }
        return OnlineTranslationResult(
            translatedText = translation,
            detectedSourceCode = detected,
            usedEngine = UsedTranslationEngine.ONLINE_FREE
        )
    }

    private suspend fun translateWithMyMemory(
        text: String,
        sourceCode: String,
        targetCode: String
    ): OnlineTranslationResult {
        val chunks = chunkTextSafely(text, MYMEMORY_MAX_CHUNK_CHARS)
        val translatedParts = mutableListOf<String>()

        for (chunk in chunks) {
            val langPair = urlEncode("$sourceCode|$targetCode")
            val q = urlEncode(chunk)
            val url = "$MYMEMORY_BASE?q=$q&langpair=$langPair"
            val request = Request.Builder().url(url).get().build()
            val body = executeRequest(request)
            val json = JSONObject(body)
            val responseData = json.optJSONObject("responseData")
                ?: throw IOException("پاسخ نامعتبر از سرویس MyMemory.")
            val translated = responseData.optString("translatedText", "").trim()
            if (translated.isBlank()) {
                throw IOException("ترجمه آنلاین خالی دریافت شد.")
            }
            translatedParts.add(translated)
        }

        return OnlineTranslationResult(
            translatedText = translatedParts.joinToString("\n"),
            detectedSourceCode = sourceCode,
            usedEngine = UsedTranslationEngine.ONLINE_FREE
        )
    }

    private fun resolveConcreteSourceForMyMemory(
        sourceCode: String,
        detectedHintCode: String?,
        targetCode: String
    ): String {
        val candidate = if (sourceCode == LanguageCatalog.AUTO_DETECT_CODE) {
            detectedHintCode?.takeIf { it != "und" && it.isNotBlank() } ?: "en"
        } else {
            sourceCode
        }
        return if (candidate.equals(targetCode, ignoreCase = true)) {
            if (targetCode == "en") "fa" else "en"
        } else {
            candidate
        }
    }

    private fun chunkTextSafely(text: String, maxChars: Int): List<String> {
        if (text.length <= maxChars) return listOf(text)
        val chunks = mutableListOf<String>()
        var index = 0
        while (index < text.length) {
            val endLimit = (index + maxChars).coerceAtMost(text.length)
            if (endLimit == text.length) {
                chunks.add(text.substring(index).trim())
                break
            }
            val splitAt = text.lastIndexOf('\n', endLimit).takeIf { it > index }
                ?: text.lastIndexOf(' ', endLimit).takeIf { it > index }
                ?: endLimit
            chunks.add(text.substring(index, splitAt).trim())
            index = splitAt
            while (index < text.length && text[index].isWhitespace()) index++
        }
        return chunks.filter { it.isNotBlank() }
    }

    private suspend fun executeRequest(request: Request): String =
        suspendCancellableCoroutine { cont ->
            val call = client.newCall(request)
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (cont.isActive) cont.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use { res ->
                        val raw = res.body?.string().orEmpty()
                        if (!res.isSuccessful) {
                            if (cont.isActive) {
                                cont.resumeWithException(
                                    IOException("خطای شبکه ترجمه آنلاین (کد ${res.code})")
                                )
                            }
                            return
                        }
                        if (cont.isActive) cont.resume(raw)
                    }
                }
            })
        }

    private fun urlEncode(value: String): String = URLEncoder.encode(value, "UTF-8")
}
