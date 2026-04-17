package com.psimandan.neuread.data.repository

import android.speech.tts.Voice
import com.psimandan.neuread.data.datasource.ClonedVoice
import com.psimandan.neuread.data.datasource.VoiceDataSource
import com.psimandan.neuread.voice.NeuReadVoice
import com.psimandan.neuread.voice.languageId
import com.psimandan.neuread.voice.toNeuReadVoice
import com.psimandan.neuread.data.datasource.PrefsStore
import kotlinx.coroutines.flow.first
import java.util.Locale
import javax.inject.Inject

class VoiceRepository @Inject constructor(
    private val voiceDataSource: VoiceDataSource,
    private val prefsStore: PrefsStore
) {
    private var availableVoices: Set<NeuReadVoice> = setOf()

    fun getContext() = voiceDataSource.getContext()

    suspend fun fetchAvailableVoices(): Set<NeuReadVoice> {
        // 1. Fetch the normal offline/native voices from the device
        val nativeVoices = voiceDataSource.loadVoices()

        val joVoice = Voice(
            "Jo (AI)",
            Locale.US,
            405,
            200,
            true,
            null
        ).toNeuReadVoice()

        val daveVoice = Voice(
            "Dave (AI)",
            Locale.US,
            406,
            200,
            true,
            null
        ).toNeuReadVoice()

        // 3. Fetch cloned voices from PrefsStore
        val clonedVoices = prefsStore.getClonedVoices().first().map { voice ->
            NeuReadVoice(
                name = voice.name,
                language = "en_US",
                requiresNetworkConnection = true,
                quality = 401,
                latency = 200,
                features = setOf("cloned"),
                clonedVoice = voice
            )
        }

        // 4. Combine them and save to state
        availableVoices = nativeVoices + joVoice + daveVoice + clonedVoices.toSet()
        return availableVoices
    }

    fun getAvailableLocales(): Set<Locale> {
        // Update this to read from our combined list rather than just the native data source,
        // ensuring the language tab in the UI shows up even if a network voice is the ONLY voice for that language.
        return availableVoices.map { it.locale }.toSet()
    }

    fun nameToVoice(name: String, language: String): NeuReadVoice {
        // 1. Try to find the voice by name only first if it's a special voice (cloned or network AI)
        val specialVoice = availableVoices.find { 
            it.name == name && (it.features?.contains("cloned") == true || it.requiresNetworkConnection)
        }
        if (specialVoice != null) return specialVoice

        // 2. Try exact match with language
        val exactMatch = availableVoices.find { it.locale.languageId() == language && it.name == name }
        if (exactMatch != null) return exactMatch

        // 3. Fallback to name only for any voice
        val nameMatch = availableVoices.find { it.name == name }
        if (nameMatch != null) return nameMatch

        return defaultVoice()
    }

    fun localeToVoice(locale: Locale): NeuReadVoice {
        return availableVoices.firstOrNull { it.locale.languageId() == locale.languageId() } ?: defaultVoice()
    }

    fun languageToLocale(language: String): Locale {
        return getAvailableLocales().firstOrNull { it.languageId() == language } ?: Locale.getDefault()
    }

    private fun defaultVoice(): NeuReadVoice {
        return Voice("No voices installed", Locale.getDefault(), 5, 5, false, null).toNeuReadVoice()
    }
}
