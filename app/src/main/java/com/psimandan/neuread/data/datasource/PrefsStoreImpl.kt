package com.psimandan.neuread.data.datasource

import android.content.Context
import java.io.File
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ActivityScoped
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

@ActivityScoped
class PrefsStoreImpl @Inject constructor(@ApplicationContext private val context: Context) :
    PrefsStore {
    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
        name = STORE_NAME
    )

    override suspend fun saveSelectedLanguages(recent: List<String>) {
        context.dataStore.edit {
            it[RECENT_LANGUAGE_SELECTIONS] = recent.toSet()
        }
    }

    override fun selectedLanguages(): Flow<List<String>> {
        return context.dataStore.data.map {
            it[RECENT_LANGUAGE_SELECTIONS]?.toList() ?: emptyList()
        }
    }

    override suspend fun saveClonedVoice(voice: ClonedVoice) {
        context.dataStore.edit { preferences ->
            val currentVoicesJson = preferences[CLONED_VOICES] ?: "[]"
            val currentVoices = Json.decodeFromString<List<ClonedVoice>>(currentVoicesJson).toMutableList()
            currentVoices.add(voice)
            preferences[CLONED_VOICES] = Json.encodeToString(currentVoices)
        }
    }

    override suspend fun updateClonedVoice(voice: ClonedVoice) {
        context.dataStore.edit { preferences ->
            val currentVoicesJson = preferences[CLONED_VOICES] ?: "[]"
            val currentVoices = Json.decodeFromString<List<ClonedVoice>>(currentVoicesJson).toMutableList()
            val index = currentVoices.indexOfFirst { it.id == voice.id }
            if (index != -1) {
                currentVoices[index] = voice
                preferences[CLONED_VOICES] = Json.encodeToString(currentVoices)
            }
        }
    }

    override suspend fun deleteClonedVoice(voiceId: String) {
        context.dataStore.edit { preferences ->
            val currentVoicesJson = preferences[CLONED_VOICES] ?: "[]"
            val currentVoices = Json.decodeFromString<List<ClonedVoice>>(currentVoicesJson).toMutableList()
            
            // Delete persistent audio file if it exists
            currentVoices.find { it.id == voiceId }?.samplePath?.let { path ->
                val file = File(path)
                if (file.exists()) file.delete()
            }

            currentVoices.removeAll { it.id == voiceId }
            preferences[CLONED_VOICES] = Json.encodeToString(currentVoices)
        }
    }

    override fun getClonedVoices(): Flow<List<ClonedVoice>> {
        return context.dataStore.data.map { preferences ->
            val voicesJson = preferences[CLONED_VOICES] ?: "[]"
            Json.decodeFromString<List<ClonedVoice>>(voicesJson)
        }
    }

    override suspend fun saveAccentColor(color: Int) {
        context.dataStore.edit {
            it[ACCENT_COLOR] = color.toString()
        }
    }

    override fun getAccentColor(): Flow<Int?> {
        return context.dataStore.data.map {
            it[ACCENT_COLOR]?.toIntOrNull()
        }
    }

    override suspend fun saveThemeMode(mode: Int) {
        context.dataStore.edit {
            it[THEME_MODE] = mode
        }
    }

    override fun getThemeMode(): Flow<Int> {
        return context.dataStore.data.map {
            it[THEME_MODE] ?: 0 // Default to 0 (Auto/Follow System)
        }
    }

    override suspend fun saveDyslexicFontEnabled(enabled: Boolean) {
        context.dataStore.edit {
            it[DYSLEXIC_FONT_ENABLED] = enabled
        }
    }

    override fun isDyslexicFontEnabled(): Flow<Boolean> {
        return context.dataStore.data.map {
            it[DYSLEXIC_FONT_ENABLED] ?: false
        }
    }

    override suspend fun saveHighlightingEnabled(enabled: Boolean) {
        context.dataStore.edit {
            it[HIGHLIGHTING_ENABLED] = enabled
        }
    }

    override fun isHighlightingEnabled(): Flow<Boolean> {
        return context.dataStore.data.map {
            it[HIGHLIGHTING_ENABLED] ?: true
        }
    }

    companion object {
        private const val STORE_NAME = "data_store"
        val RECENT_LANGUAGE_SELECTIONS = stringSetPreferencesKey("RECENT_LANGUAGE_SELECTIONS")
        val CLONED_VOICES = stringPreferencesKey("CLONED_VOICES")
        val ACCENT_COLOR = stringPreferencesKey("ACCENT_COLOR")
        val THEME_MODE = intPreferencesKey("THEME_MODE")
        val DYSLEXIC_FONT_ENABLED = androidx.datastore.preferences.core.booleanPreferencesKey("DYSLEXIC_FONT_ENABLED")
        val HIGHLIGHTING_ENABLED = androidx.datastore.preferences.core.booleanPreferencesKey("HIGHLIGHTING_ENABLED")
    }
}
