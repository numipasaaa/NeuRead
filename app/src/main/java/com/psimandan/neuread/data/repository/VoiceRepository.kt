package com.psimandan.neuread.data.repository

import android.speech.tts.Voice
import com.psimandan.neuread.data.datasource.VoiceDataSource
import com.psimandan.neuread.voice.NeuReadVoice
import com.psimandan.neuread.voice.languageId
import com.psimandan.neuread.voice.toNeuReadVoice
import java.util.Locale
import javax.inject.Inject

class VoiceRepository @Inject constructor(
    private val voiceDataSource: VoiceDataSource
) {
    private var availableVoices: Set<NeuReadVoice> = setOf()

    suspend fun fetchAvailableVoices(): Set<NeuReadVoice> {
        // 1. Fetch the normal offline/native voices from the device
        val nativeVoices = voiceDataSource.loadVoices()

        // 2. Create our custom Network Voice for the Python API
        // Note: Change Locale("ro") to Locale("en", "US") if you reverted to the English model
        val neuTtsVoice = Voice(
            "NeuTTS (High Quality AI)", // The name that will appear in your UI
            Locale("en"),               // The language category it belongs to
            400,                        // Quality indicator (High)
            200,                        // Latency indicator (Network)
            true,                       // requiresNetwork = true
            null                        // features
        ).toNeuReadVoice()

        // 3. Combine them and save to state
        availableVoices = nativeVoices + neuTtsVoice
        return availableVoices
    }

    fun getAvailableLocales(): Set<Locale> {
        // Update this to read from our combined list rather than just the native data source,
        // ensuring the language tab in the UI shows up even if NeuTTS is the ONLY voice for that language.
        return availableVoices.map { it.locale }.toSet()
    }

    fun nameToVoice(name: String, language: String): NeuReadVoice {
        val voices = availableVoices.filter { it.locale.languageId() == language && it.name == name }
        return voices.firstOrNull() ?: defaultVoice()
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
