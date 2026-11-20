package com.example.data.ai

import android.content.Context
import com.example.BuildConfig
import com.example.data.security.SecureTokenManager
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class GeminiPart(val text: String? = null, val thought: Boolean? = null)

@JsonClass(generateAdapter = true)
data class GeminiContent(val role: String? = "user", val parts: List<GeminiPart>? = null)

@JsonClass(generateAdapter = true)
data class GeminiRequest(
    val contents: List<GeminiContent>,
    @Json(name = "system_instruction") val systemInstruction: GeminiContent? = null
)

@JsonClass(generateAdapter = true)
data class GeminiCandidate(
    val content: GeminiContent? = null,
    @Json(name = "finishReason") val finishReason: String? = null
)

@JsonClass(generateAdapter = true)
data class GeminiPromptFeedback(@Json(name = "blockReason") val blockReason: String? = null)

@JsonClass(generateAdapter = true)
data class GeminiResponse(
    val candidates: List<GeminiCandidate>? = null,
    @Json(name = "promptFeedback") val promptFeedback: GeminiPromptFeedback? = null
)

enum class GeminiErrorKind {
    NO_KEY, INVALID_REQUEST, AUTH, MODEL_UNAVAILABLE, RATE_LIMIT, SERVER, NETWORK, BLOCKED, EMPTY_RESPONSE, UNKNOWN
}

class GeminiApiException(
    val kind: GeminiErrorKind,
    val httpCode: Int,
    message: String
) : Exception(message)

/** Real, verified state of the Gemini connection. "Ready" only follows a successful API call. */
sealed class GeminiConnectionStatus {
    object NoKey : GeminiConnectionStatus()
    object Checking : GeminiConnectionStatus()
    data class Ready(val modelId: String) : GeminiConnectionStatus()
    data class Failed(val kind: GeminiErrorKind, val message: String) : GeminiConnectionStatus()
}

object GeminiErrors {
    private val keyPattern = Regex("AIza[0-9A-Za-z_\\-]{20,}")
    private val anyAdapter = Moshi.Builder().build().adapter(Any::class.java)

    /** Removes anything that looks like a Google API key before text reaches the UI or logs. */
    fun redact(text: String?): String = keyPattern.replace(text.orEmpty(), "AIza…[redacted]")

    /** Extracts `error.message` from Google's JSON error body, or null. */
    fun extractMessage(body: String?): String? {
        if (body.isNullOrBlank()) return null
        return try {
            val root = anyAdapter.fromJson(body) as? Map<*, *>
            ((root?.get("error") as? Map<*, *>)?.get("message") as? String)?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            null
        }
    }

    fun fromHttp(code: Int, body: String?, modelId: String): GeminiApiException {
        val detail = redact(extractMessage(body) ?: body?.take(300)).ifBlank { "no details returned" }
        return when (code) {
            400 ->
                if (detail.contains("API key", ignoreCase = true) || detail.contains("API_KEY_INVALID"))
                    GeminiApiException(GeminiErrorKind.AUTH, code,
                        "API key issue (HTTP 400): $detail")
                else
                    GeminiApiException(GeminiErrorKind.INVALID_REQUEST, code,
                        "Invalid request (HTTP 400): $detail")
            401, 403 -> GeminiApiException(GeminiErrorKind.AUTH, code,
                "API key or permission issue (HTTP $code): $detail")
            404 -> GeminiApiException(GeminiErrorKind.MODEL_UNAVAILABLE, code,
                "Model or endpoint unavailable (HTTP 404) for model '$modelId': $detail")
            429 -> GeminiApiException(GeminiErrorKind.RATE_LIMIT, code,
                "Rate limit or quota exceeded (HTTP 429): $detail")
            500, 502, 503, 504 -> GeminiApiException(GeminiErrorKind.SERVER, code,
                "Temporary Gemini server error (HTTP $code), please try again shortly: $detail")
            else -> GeminiApiException(GeminiErrorKind.UNKNOWN, code, "Gemini API error (HTTP $code): $detail")
        }
    }
}

data class AiChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val isUser: Boolean,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val proposal: AiCodeProposal? = null
)

data class AiCodeProposal(
    val originalCode: String,
    val proposedCode: String,
    val summary: String,
    val targetFileName: String? = null
)

enum class DiffLineType { UNCHANGED, ADDED, REMOVED }
data class DiffLine(val type: DiffLineType, val text: String, val oldLineNumber: Int?, val newLineNumber: Int?)

/**
 * Nexa AI Service
 *
 * Direct REST integration with the Gemini API (stable `generateContent` method) using the
 * user-provided API key stored in the Keystore (or the injected BuildConfig key).
 * The model id comes only from [GeminiConfig].
 */
class GeminiAiService(private val context: Context) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val requestAdapter = moshi.adapter(GeminiRequest::class.java)
    private val responseAdapter = moshi.adapter(GeminiResponse::class.java)

    val modelId: String get() = GeminiConfig.modelId(context)

    fun getEffectiveApiKey(): String? {
        val storedKey = SecureTokenManager.getGeminiApiKey(context)?.let { cleanKey(it) }
        if (!storedKey.isNullOrBlank()) return storedKey

        // Fallback to BuildConfig if present and not empty placeholder
        return try {
            val buildKey = BuildConfig::class.java.getField("GEMINI_API_KEY").get(null) as? String
            buildKey?.let { cleanKey(it) }
                ?.takeIf { it.isNotBlank() && !it.contains("MY_GEMINI_API_KEY") && !it.contains("YOUR_") }
        } catch (e: Exception) {
            null
        }
    }

    private fun cleanKey(raw: String): String = raw.filter { !it.isWhitespace() && !it.isISOControl() }

    /**
     * Performs one real, minimal generateContent call. Only a successful response yields Ready.
     */
    suspend fun checkConnection(): GeminiConnectionStatus {
        if (getEffectiveApiKey().isNullOrBlank()) return GeminiConnectionStatus.NoKey
        return try {
            generate(
                contents = listOf(GeminiContent("user", listOf(GeminiPart("Reply with exactly one word: OK")))),
                systemInstruction = null
            )
            GeminiConnectionStatus.Ready(modelId)
        } catch (e: GeminiApiException) {
            GeminiConnectionStatus.Failed(e.kind, e.message ?: "Gemini request failed")
        }
    }

    suspend fun sendMessage(
        prompt: String,
        history: List<AiChatMessage> = emptyList(),
        currentFileName: String? = null,
        currentFileContent: String? = null
    ): String {
        // Contextual prompt with active file
        val contextualPrompt = buildString {
            if (!currentFileName.isNullOrBlank() && !currentFileContent.isNullOrBlank()) {
                append("Active File Context: '$currentFileName'\n```\n")
                append(currentFileContent.take(4000))
                append("\n```\n\nUser Question / Task:\n")
            }
            append(prompt)
        }

        val systemInstruction = GeminiContent(
            role = null,
            parts = listOf(GeminiPart(SYSTEM_PROMPT))
        )
        return generate(buildContents(history, prompt, contextualPrompt), systemInstruction)
    }

    private suspend fun generate(contents: List<GeminiContent>, systemInstruction: GeminiContent?): String =
        withContext(Dispatchers.IO) {
            val apiKey = getEffectiveApiKey()
            if (apiKey.isNullOrBlank()) {
                throw GeminiApiException(
                    GeminiErrorKind.NO_KEY, 0,
                    "No Gemini API Key found. Please enter your Gemini API key in Nexa AI settings to start chatting."
                )
            }
            val model = modelId
            val jsonPayload = requestAdapter.toJson(GeminiRequest(contents, systemInstruction))

            // The key goes in a header (not the URL) so it can never leak through URLs or exceptions.
            val request = Request.Builder()
                .url(GeminiConfig.generateContentUrl(model))
                .header("x-goog-api-key", apiKey)
                .post(jsonPayload.toRequestBody("application/json".toMediaType()))
                .build()

            val (code, responseBody) = try {
                httpClient.newCall(request).execute().use { response ->
                    response.code to (response.body?.string() ?: "")
                }
            } catch (e: java.io.IOException) {
                throw GeminiApiException(
                    GeminiErrorKind.NETWORK, 0,
                    "Network error while contacting Gemini: ${GeminiErrors.redact(e.message)}"
                )
            }

            if (code !in 200..299) throw GeminiErrors.fromHttp(code, responseBody, model)

            val parsed = try {
                responseAdapter.fromJson(responseBody)
            } catch (e: Exception) {
                throw GeminiApiException(GeminiErrorKind.EMPTY_RESPONSE, code, "Could not parse Gemini response.")
            }

            val candidate = parsed?.candidates?.firstOrNull()
            val text = candidate?.content?.parts
                ?.filter { it.thought != true }
                ?.mapNotNull { it.text }
                ?.joinToString("")
                ?.trim()

            if (text.isNullOrBlank()) {
                val blocked = parsed?.promptFeedback?.blockReason
                if (blocked != null) {
                    throw GeminiApiException(GeminiErrorKind.BLOCKED, code, "Request blocked by Gemini (reason: $blocked).")
                }
                throw GeminiApiException(
                    GeminiErrorKind.EMPTY_RESPONSE, code,
                    "Received empty response from Gemini API" +
                        (candidate?.finishReason?.let { " (finish reason: $it)" } ?: "") + "."
                )
            }
            text
        }

    suspend fun explainCode(code: String, fileName: String): String {
        return sendMessage(
            prompt = "Please explain the purpose and functionality of this code clearly in bullet points with high-level architecture insights:\n\n```\n$code\n```",
            currentFileName = fileName,
            currentFileContent = code
        )
    }

    suspend fun fixErrors(code: String, errorMessage: String?, fileName: String): AiCodeProposal {
        val prompt = buildString {
            append("Please fix the following code and resolve any errors. ")
            if (!errorMessage.isNullOrBlank()) {
                append("Error description:\n$errorMessage\n\n")
            }
            append("Provide the complete, corrected code in a markdown ``` code block, followed by a brief 2-3 bullet explanation of what was changed.")
        }

        val response = sendMessage(prompt = prompt, currentFileName = fileName, currentFileContent = code)
        val extractedCode = extractCodeBlock(response).ifBlank { code }
        val summary = extractExplanation(response).ifBlank { "Fixed syntax and potential bugs in $fileName" }

        return AiCodeProposal(
            originalCode = code,
            proposedCode = extractedCode,
            summary = summary,
            targetFileName = fileName
        )
    }

    suspend fun refactorCode(code: String, instruction: String, fileName: String): AiCodeProposal {
        val prompt = "Refactor this code according to modern clean architecture and best practices ($instruction). " +
                "Output the complete refactored code in a markdown ``` code block followed by a summary of changes."

        val response = sendMessage(prompt = prompt, currentFileName = fileName, currentFileContent = code)
        val extractedCode = extractCodeBlock(response).ifBlank { code }
        val summary = extractExplanation(response).ifBlank { "Refactored code for cleaner structure and performance." }

        return AiCodeProposal(
            originalCode = code,
            proposedCode = extractedCode,
            summary = summary,
            targetFileName = fileName
        )
    }

    suspend fun codeReview(code: String, fileName: String): String {
        return sendMessage(
            prompt = "Conduct a thorough code review of this file. Identify potential memory leaks, security issues, edge cases, and performance improvements.",
            currentFileName = fileName,
            currentFileContent = code
        )
    }

    /**
     * Computes line-by-line diff between original and proposed code
     */
    fun computeDiff(original: String, proposed: String): List<DiffLine> {
        val oldLines = original.lines()
        val newLines = proposed.lines()
        val diffList = mutableListOf<DiffLine>()

        var oldIdx = 0
        var newIdx = 0

        while (oldIdx < oldLines.size || newIdx < newLines.size) {
            val oldLine = oldLines.getOrNull(oldIdx)
            val newLine = newLines.getOrNull(newIdx)

            if (oldLine == newLine && oldLine != null) {
                diffList.add(DiffLine(DiffLineType.UNCHANGED, oldLine, oldIdx + 1, newIdx + 1))
                oldIdx++
                newIdx++
            } else if (oldLine != null && (newLine == null || !newLines.contains(oldLine))) {
                diffList.add(DiffLine(DiffLineType.REMOVED, oldLine, oldIdx + 1, null))
                oldIdx++
            } else if (newLine != null) {
                diffList.add(DiffLine(DiffLineType.ADDED, newLine, null, newIdx + 1))
                newIdx++
            } else {
                break
            }
        }
        return diffList
    }

    private fun extractCodeBlock(markdown: String): String {
        val regex = Regex("```(?:[a-zA-Z0-9_-]+)?\\s*\\n([\\s\\S]*?)\\n```")
        val match = regex.find(markdown)
        return match?.groupValues?.get(1)?.trim() ?: ""
    }

    private fun extractExplanation(markdown: String): String {
        val withoutBlocks = markdown.replace(Regex("```[\\s\\S]*?```"), "").trim()
        return withoutBlocks.lines().filter { it.isNotBlank() }.take(5).joinToString("\n")
    }

    companion object {
        private const val MAX_HISTORY_MESSAGES = 6

        private const val SYSTEM_PROMPT =
            "You are Nexa AI, a friendly and highly skilled Android & Web software engineer embedded inside Nexa IDE. " +
            "Your job is to assist the developer with Kotlin, Java, XML, Gradle, Jetpack Compose, HTML, CSS, JavaScript, and APK building. " +
            "Always provide precise, clean code. When proposing code changes, output the complete updated code block so the developer can review and apply it directly. " +
            "Reply in the language the user writes in (for example Bengali), keeping code and identifiers in their original form."

        /**
         * Builds a valid alternating user/model conversation from the chat history:
         * - drops the just-typed prompt if the UI already appended it to the history,
         * - keeps the most recent [MAX_HISTORY_MESSAGES] earlier messages,
         * - drops leading model turns (e.g. the greeting) because a conversation must start with a user turn,
         * - merges consecutive turns of the same role.
         */
        internal fun buildContents(
            history: List<AiChatMessage>,
            rawPrompt: String,
            contextualPrompt: String
        ): List<GeminiContent> {
            val prior = history.toMutableList()
            if (prior.lastOrNull()?.let { it.isUser && it.text == rawPrompt } == true) prior.removeAt(prior.lastIndex)

            val turns = mutableListOf<Pair<String, String>>()
            fun add(role: String, text: String) {
                if (text.isBlank()) return
                if (turns.isEmpty() && role == "model") return
                val last = turns.lastOrNull()
                if (last != null && last.first == role) {
                    turns[turns.lastIndex] = role to (last.second + "\n\n" + text)
                } else {
                    turns.add(role to text)
                }
            }
            prior.takeLast(MAX_HISTORY_MESSAGES).forEach { add(if (it.isUser) "user" else "model", it.text) }
            add("user", contextualPrompt)
            return turns.map { GeminiContent(role = it.first, parts = listOf(GeminiPart(it.second))) }
        }
    }
}
