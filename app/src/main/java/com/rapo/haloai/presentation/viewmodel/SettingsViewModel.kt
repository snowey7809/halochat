package com.rapo.haloai.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rapo.haloai.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val theme = settingsRepository.theme.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = "system"
    )

    fun setTheme(theme: String) {
        viewModelScope.launch {
            settingsRepository.setTheme(theme)
        }
    }

    val hardwareAcceleration = settingsRepository.hardwareAcceleration.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = true
    )

    fun setHardwareAcceleration(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setHardwareAcceleration(enabled)
        }
    }

    val memoryMode = settingsRepository.memoryMode.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = "normal"
    )

    fun setMemoryMode(mode: String) {
        viewModelScope.launch {
            settingsRepository.setMemoryMode(mode)
        }
    }
}