package com.harsh.gymmanager.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.harsh.gymmanager.domain.model.AppLanguage
import com.harsh.gymmanager.domain.model.AppSettings
import com.harsh.gymmanager.domain.model.ThemeMode
import com.harsh.gymmanager.domain.usecase.SettingsUseCases
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** App-wide settings: the gym, theme and language. Null until the stored settings have loaded. */
class SettingsViewModel(private val settings: SettingsUseCases) : ViewModel() {
    val state: StateFlow<AppSettings?> = settings.observe()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun onThemeChange(theme: ThemeMode) {
        viewModelScope.launch { settings.setTheme(theme) }
    }

    fun onLanguageChange(language: AppLanguage) {
        viewModelScope.launch { settings.setLanguage(language) }
    }
}
