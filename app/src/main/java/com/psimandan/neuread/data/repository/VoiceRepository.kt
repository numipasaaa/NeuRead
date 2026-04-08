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
        availableVoices = voiceDataSource.loadVoices()
        return availableVoices
    }

    fun getAvailableLocales(): Set<Locale> {
        return voiceDataSource.getAvailableLocales()
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
