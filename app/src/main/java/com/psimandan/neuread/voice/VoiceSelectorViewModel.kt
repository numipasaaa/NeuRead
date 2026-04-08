package com.psimandan.neuread.voice

import android.speech.tts.Voice
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.psimandan.neuread.data.repository.VoiceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.Locale
import javax.inject.Inject


fun Locale.languageId(): String {
    return "${this.language}_${this.country}"
}

fun Voice.toNeuReadVoice(): NeuReadVoice {
    return NeuReadVoice(
        name,
        locale.languageId(),
        locale,
        quality,
        latency,
        isNetworkConnectionRequired,
        features
    )
}

fun NeuReadVoice.toVoice(): Voice {
    return Voice(name, locale, quality, latency, requiresNetworkConnection, features)
}

fun NeuReadVoice.toLocale(): Locale {
    return language.toLocale()
}

fun NeuReadVoice.isVoiceNotInstalled(): Boolean {
    return features?.contains("notInstalled") != true
}

fun String.toLocale(): Locale {
    val parts = this.split("_")
    return when (parts.size) {
        1 -> Locale(parts[0]) // Only language (e.g., "en")
        2 -> Locale(parts[0], parts[1]) // Language and country (e.g., "en", "US")
        3 -> Locale(parts[0], parts[1], parts[2]) // Language, country, and variant
        else -> Locale.getDefault() // Fallback to device default
    }
}

data class NeuReadVoice(
    val name: String,
    val language: String,
    val locale: Locale = language.toLocale(),
    val quality: Int = 0,
    val latency: Int = 0,
    val requiresNetworkConnection: Boolean = false,
    val features: Set<String>? = null
)

@HiltViewModel
class VoiceSelectorViewModel @Inject constructor(
    private val repository: VoiceRepository
) : ViewModel() {

    private val _availableVoices = MutableStateFlow<Set<NeuReadVoice>>(emptySet())
    val availableVoices: StateFlow<Set<NeuReadVoice>> = _availableVoices

    private val _availableLocales = MutableStateFlow<Set<Locale>>(emptySet())
    val availableLocales: StateFlow<Set<Locale>> = _availableLocales

    init {
        Timber.d("BookSettingsScreenView.VoiceSelectorViewModel.init=>")
    }

    fun loadVoices() {
        Timber.d("BookSettingsScreenView.loadVoices()=>")
        val start = System.currentTimeMillis()
        viewModelScope.launch {
            val (voices, locales) = withContext(Dispatchers.IO) {
                val fetchedVoices = repository.fetchAvailableVoices()
                val fetchedLocales = repository.getAvailableLocales()
                fetchedVoices to fetchedLocales
            }
            _availableVoices.value = voices
            _availableLocales.value = locales
            Timber.d("loadVoices=>${System.currentTimeMillis() - start}")
        }
    }

    fun nameToVoice(name: String, language: String): NeuReadVoice {
        return repository.nameToVoice(name, language)
    }
}