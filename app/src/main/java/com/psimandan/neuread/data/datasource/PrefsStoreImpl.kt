package com.psimandan.neuread.data.datasource

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
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

    companion object {
        private const val STORE_NAME = "data_store"
        val RECENT_LANGUAGE_SELECTIONS = stringSetPreferencesKey("RECENT_LANGUAGE_SELECTIONS")
        val CLONED_VOICES = stringPreferencesKey("CLONED_VOICES")
    }
}