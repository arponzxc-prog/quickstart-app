package com.example.data.ai

import android.content.Context

/**
 * Single place that decides which Gemini model and endpoint Nexa AI uses.
 *
 * To move to a newer model change [DEFAULT_MODEL_ID] here (nothing else references a model id), or
 * set a runtime override with [setModelOverride] without rebuilding the app.
 * The API key is NOT configured here; it lives in SecureTokenManager.
 */
object GeminiConfig {
    /** Model recommended by the API after gemini-2.5-flash was withdrawn for new users. */
    const val DEFAULT_MODEL_ID = "gemini-3.8-flash"

    const val API_BASE_URL = "https://generativelanguage.googleapis.com/v1beta"

    private const val PREFS_NAME = "nexa_ai_config"
    private const val KEY_MODEL_OVERRIDE = "gemini_model_override"
    private const val KEY_MODEL_LIST = "gemini_model_list"
    private const val MAX_SAVED_MODELS = 30
    private val validIdRegex = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{1,63}$")

    /** Accepts "gemini-x", "models/gemini-x" and strips anything that is not a valid id character. */
    fun normalizeModelId(raw: String?): String {
        val cleaned = raw.orEmpty().trim().removePrefix("models/")
            .filter { it.isLetterOrDigit() || it == '-' || it == '.' || it == '_' }
        return cleaned
    }

    fun modelId(context: Context): String {
        val override = try {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_MODEL_OVERRIDE, null)
        } catch (e: Exception) {
            null
        }
        return normalizeModelId(override).ifBlank { DEFAULT_MODEL_ID }
    }

    /** Pass null/blank to go back to [DEFAULT_MODEL_ID]. */
    fun setModelOverride(context: Context, modelId: String?) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        val normalized = normalizeModelId(modelId)
        if (normalized.isBlank()) prefs.remove(KEY_MODEL_OVERRIDE) else prefs.putString(KEY_MODEL_OVERRIDE, normalized)
        prefs.commit()
    }

    fun generateContentUrl(modelId: String): String =
        "$API_BASE_URL/models/${normalizeModelId(modelId)}:generateContent"

    // ---------- Model list managed from Settings ----------

    /** Returns an error message, or null when [raw] is an acceptable model id. */
    fun validateModelId(raw: String): String? {
        val id = raw.trim().removePrefix("models/")
        return when {
            id.isEmpty() -> "Enter a model name, e.g. $DEFAULT_MODEL_ID"
            !validIdRegex.matches(id) ->
                "Invalid model name. Use letters, digits, '.', '-' or '_' only (2-64 characters, no spaces)."
            else -> null
        }
    }

    internal fun parseList(raw: String?): List<String> =
        raw.orEmpty().lines().map { normalizeModelId(it) }.filter { it.isNotBlank() }.distinct()

    /** Default model first, then user-added models, plus the currently selected one if unlisted. */
    fun getModels(context: Context): List<String> {
        val saved = try {
            parseList(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_MODEL_LIST, null))
        } catch (e: Exception) {
            emptyList()
        }
        return (listOf(DEFAULT_MODEL_ID) + saved + modelId(context)).distinct()
    }

    /** Adds a model to the list. Returns an error message, or null on success. */
    fun addModel(context: Context, raw: String): String? {
        validateModelId(raw)?.let { return it }
        val id = normalizeModelId(raw)
        val current = getModels(context)
        if (id in current) return "'$id' is already in the list."
        if (current.size >= MAX_SAVED_MODELS) return "Model list is full. Remove one first."
        val custom = (current - DEFAULT_MODEL_ID) + id
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_MODEL_LIST, custom.joinToString("\n")).commit()
        return null
    }

    /** Removes a user-added model. The built-in default cannot be removed. */
    fun removeModel(context: Context, id: String) {
        if (id == DEFAULT_MODEL_ID) return
        val custom = getModels(context) - DEFAULT_MODEL_ID - id
        val editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        editor.putString(KEY_MODEL_LIST, custom.joinToString("\n"))
        if (modelId(context) == id) editor.remove(KEY_MODEL_OVERRIDE) // fall back to the default
        editor.commit()
    }
}
