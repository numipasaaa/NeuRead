package com.psimandan.neuread.voice

import com.psimandan.neuread.data.datasource.ClonedVoice
import com.psimandan.neuread.data.datasource.PrefsStore
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
import java.io.File
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
    val features: Set<String>? = null,
    val clonedVoice: ClonedVoice? = null
)

@HiltViewModel
class VoiceSelectorViewModel @Inject constructor(
    private val repository: VoiceRepository,
    private val prefsStore: PrefsStore
) : ViewModel() {

    private val _availableVoices = MutableStateFlow<List<NeuReadVoice>>(emptyList())
    val availableVoices: StateFlow<List<NeuReadVoice>> = _availableVoices

    private val _availableLocales = MutableStateFlow<List<Locale>>(emptyList())
    val availableLocales: StateFlow<List<Locale>> = _availableLocales

    private val _clonedVoices = MutableStateFlow<List<NeuReadVoice>>(emptyList())
    val clonedVoices: StateFlow<List<NeuReadVoice>> = _clonedVoices

    init {
        Timber.d("BookSettingsScreenView.VoiceSelectorViewModel.init=>")
        loadClonedVoices()
    }

    fun loadClonedVoices() {
        viewModelScope.launch {
            prefsStore.getClonedVoices().collect { voices ->
                val neuReadVoices = voices.map { voice ->
                    NeuReadVoice(
                        name = voice.name,
                        language = voice.language,
                        requiresNetworkConnection = true,
                        features = setOf("cloned"),
                        clonedVoice = voice
                    )
                }
                _clonedVoices.value = neuReadVoices
            }
        }
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
            
            _availableLocales.value = (locales + Locale.US).toList()
            _availableVoices.value = voices.toList()

            Timber.d("loadVoices=>${System.currentTimeMillis() - start}")
        }
    }

    fun nameToVoice(name: String, language: String): NeuReadVoice {
        val cloned = _clonedVoices.value.find { it.name == name && it.language == language }
        if (cloned != null) return cloned
        return repository.nameToVoice(name, language)
    }

    fun deleteVoice(voice: NeuReadVoice) {
        viewModelScope.launch {
            voice.clonedVoice?.let {
                prefsStore.deleteClonedVoice(it.id)
            }
        }
    }

    fun updateVoice(voice: NeuReadVoice, newName: String, newLanguage: String) {
        viewModelScope.launch {
            voice.clonedVoice?.let {
                val updated = it.copy(name = newName, language = newLanguage)
                prefsStore.updateClonedVoice(updated)
            }
        }
    }
}
