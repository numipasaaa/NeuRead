package com.psimandan.neuread.data.datasource

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Serializable
data class ClonedVoice(
    val id: String,
    val name: String,
    val language: String = "en_US",
    val referenceText: String,
    val codes: List<Int>,
    val samplePath: String? = null
)

interface PrefsStore {
    suspend fun saveSelectedLanguages(recent: List<String>)
    fun selectedLanguages(): Flow<List<String>>

    suspend fun saveClonedVoice(voice: ClonedVoice)
    suspend fun updateClonedVoice(voice: ClonedVoice)
    suspend fun deleteClonedVoice(voiceId: String)
    fun getClonedVoices(): Flow<List<ClonedVoice>>

    suspend fun saveAccentColor(color: Int)
    fun getAccentColor(): Flow<Int?>

    suspend fun saveThemeMode(mode: Int)
    fun getThemeMode(): Flow<Int>

    suspend fun saveDyslexicFontEnabled(enabled: Boolean)
    fun isDyslexicFontEnabled(): Flow<Boolean>

    suspend fun saveHighlightingEnabled(enabled: Boolean)
    fun isHighlightingEnabled(): Flow<Boolean>
}
