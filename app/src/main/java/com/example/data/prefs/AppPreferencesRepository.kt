package com.example.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.model.TranslationEngineMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "circle_translate_prefs")

class AppPreferencesRepository(private val context: Context) {

    private object Keys {
        val TARGET_LANGUAGE = stringPreferencesKey("target_language")
        val ACTIVE_OCR_LANGUAGES = stringSetPreferencesKey("active_ocr_languages")
        val AUTO_TRANSLATE = booleanPreferencesKey("auto_translate_on_select")
        val ALLOW_CELLULAR_DOWNLOADS = booleanPreferencesKey("allow_cellular_downloads")
        val TRANSLATION_ENGINE_MODE = stringPreferencesKey("translation_engine_mode")
        val GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
    }

    val targetLanguageCode: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.TARGET_LANGUAGE] ?: "fa"
    }

    val activeOcrLanguages: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[Keys.ACTIVE_OCR_LANGUAGES] ?: setOf("fas", "eng")
    }

    val autoTranslateOnSelect: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.AUTO_TRANSLATE] ?: true
    }

    val allowCellularDownloads: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.ALLOW_CELLULAR_DOWNLOADS] ?: true
    }

    val translationEngineMode: Flow<TranslationEngineMode> = context.dataStore.data.map { prefs ->
        TranslationEngineMode.fromKey(prefs[Keys.TRANSLATION_ENGINE_MODE])
    }

    val geminiApiKey: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.GEMINI_API_KEY] ?: ""
    }

    suspend fun setTargetLanguage(code: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.TARGET_LANGUAGE] = code
        }
    }

    suspend fun toggleOcrLanguage(tessCode: String, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            val current = (prefs[Keys.ACTIVE_OCR_LANGUAGES] ?: setOf("fas", "eng")).toMutableSet()
            if (enabled) {
                current.add(tessCode)
            } else if (current.size > 1) {
                current.remove(tessCode)
            }
            prefs[Keys.ACTIVE_OCR_LANGUAGES] = current
        }
    }

    suspend fun setAutoTranslateOnSelect(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.AUTO_TRANSLATE] = enabled
        }
    }

    suspend fun setAllowCellularDownloads(allowed: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.ALLOW_CELLULAR_DOWNLOADS] = allowed
        }
    }

    suspend fun setTranslationEngineMode(mode: TranslationEngineMode) {
        context.dataStore.edit { prefs ->
            prefs[Keys.TRANSLATION_ENGINE_MODE] = mode.key
        }
    }

    suspend fun setGeminiApiKey(apiKey: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.GEMINI_API_KEY] = apiKey.trim()
        }
    }
}
