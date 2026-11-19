package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "nexa_preferences")

data class NexaPreferences(
    val themeMode: String = "DARK", // "DARK", "LIGHT", "SYSTEM"
    val editorFontSize: Float = 14f,
    val editorTabSize: Int = 4,
    val editorAutoSave: Boolean = true,
    val editorTheme: String = "one-dark", // "one-dark", "dracula", "material-light", "github-dark"
    val editorAutoIndent: Boolean = true,
    val defaultBuildType: String = "DEBUG", // "DEBUG", "RELEASE", "BUNDLE"
    val downloadFolder: String = "default",
    val centralBuildEnabled: Boolean = true,
    val lastActiveProjectId: String? = null
)

class NexaPreferencesRepository(private val context: Context) {
    companion object {
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_EDITOR_FONT_SIZE = floatPreferencesKey("editor_font_size")
        val KEY_EDITOR_TAB_SIZE = intPreferencesKey("editor_tab_size")
        val KEY_EDITOR_AUTO_SAVE = booleanPreferencesKey("editor_auto_save")
        val KEY_EDITOR_THEME = stringPreferencesKey("editor_theme")
        val KEY_EDITOR_AUTO_INDENT = booleanPreferencesKey("editor_auto_indent")
        val KEY_DEFAULT_BUILD_TYPE = stringPreferencesKey("default_build_type")
        val KEY_DOWNLOAD_FOLDER = stringPreferencesKey("download_folder")
        val KEY_CENTRAL_BUILD_ENABLED = booleanPreferencesKey("central_build_enabled")
        val KEY_LAST_PROJECT_ID = stringPreferencesKey("last_active_project_id")
    }

    val preferencesFlow: Flow<NexaPreferences> = context.dataStore.data.map { pref ->
        NexaPreferences(
            themeMode = pref[KEY_THEME_MODE] ?: "DARK",
            editorFontSize = pref[KEY_EDITOR_FONT_SIZE] ?: 14f,
            editorTabSize = pref[KEY_EDITOR_TAB_SIZE] ?: 4,
            editorAutoSave = pref[KEY_EDITOR_AUTO_SAVE] ?: true,
            editorTheme = pref[KEY_EDITOR_THEME] ?: "one-dark",
            editorAutoIndent = pref[KEY_EDITOR_AUTO_INDENT] ?: true,
            defaultBuildType = pref[KEY_DEFAULT_BUILD_TYPE] ?: "DEBUG",
            downloadFolder = pref[KEY_DOWNLOAD_FOLDER] ?: "default",
            centralBuildEnabled = pref[KEY_CENTRAL_BUILD_ENABLED] ?: true,
            lastActiveProjectId = pref[KEY_LAST_PROJECT_ID]
        )
    }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { it[KEY_THEME_MODE] = mode }
    }

    suspend fun setEditorFontSize(size: Float) {
        context.dataStore.edit { it[KEY_EDITOR_FONT_SIZE] = size }
    }

    suspend fun setEditorTabSize(tabSize: Int) {
        context.dataStore.edit { it[KEY_EDITOR_TAB_SIZE] = tabSize }
    }

    suspend fun setEditorAutoSave(enabled: Boolean) {
        context.dataStore.edit { it[KEY_EDITOR_AUTO_SAVE] = enabled }
    }

    suspend fun setEditorTheme(theme: String) {
        context.dataStore.edit { it[KEY_EDITOR_THEME] = theme }
    }

    suspend fun setDefaultBuildType(buildType: String) {
        context.dataStore.edit { it[KEY_DEFAULT_BUILD_TYPE] = buildType }
    }

    suspend fun setCentralBuildEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_CENTRAL_BUILD_ENABLED] = enabled }
    }

    suspend fun setLastActiveProjectId(projectId: String?) {
        context.dataStore.edit {
            if (projectId != null) {
                it[KEY_LAST_PROJECT_ID] = projectId
            } else {
                it.remove(KEY_LAST_PROJECT_ID)
            }
        }
    }
}
