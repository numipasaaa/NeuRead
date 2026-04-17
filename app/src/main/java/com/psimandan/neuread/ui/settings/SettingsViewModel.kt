package com.psimandan.neuread.ui.settings

import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.psimandan.neuread.data.datasource.PrefsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefsStore: PrefsStore
) : ViewModel() {

    val accentColor: StateFlow<Int?> = prefsStore.getAccentColor()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val themeMode: StateFlow<Int> = prefsStore.getThemeMode()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun updateAccentColor(color: androidx.compose.ui.graphics.Color) {
        viewModelScope.launch {
            prefsStore.saveAccentColor(color.toArgb())
        }
    }

    fun updateThemeMode(mode: Int) {
        viewModelScope.launch {
            prefsStore.saveThemeMode(mode)
        }
    }
}
