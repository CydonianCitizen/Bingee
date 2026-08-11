package com.cydoniancitizen.bingee.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cydoniancitizen.bingee.data.settings.AppLanguage
import com.cydoniancitizen.bingee.data.settings.AppTheme
import com.cydoniancitizen.bingee.data.settings.AppearancePreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class AppearanceLanguageUiState(
    val theme: AppTheme = AppTheme.SYSTEM_DEFAULT,
    val language: AppLanguage = AppLanguage.ENGLISH
)

@HiltViewModel
internal class AppearanceLanguageViewModel @Inject constructor(
    private val appearancePreferences: AppearancePreferences
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(AppearanceLanguageUiState())
    val uiState: StateFlow<AppearanceLanguageUiState> = mutableUiState.asStateFlow()

    init {
        viewModelScope.launch {
            appearancePreferences.observeTheme().collect { theme ->
                mutableUiState.update { it.copy(theme = theme) }
            }
        }
        viewModelScope.launch {
            appearancePreferences.observeLanguage().collect { language ->
                mutableUiState.update { it.copy(language = language) }
            }
        }
    }

    fun setTheme(theme: AppTheme) {
        viewModelScope.launch {
            appearancePreferences.setTheme(theme)
        }
    }

    fun setLanguage(language: AppLanguage) {
        if (language == mutableUiState.value.language) return
        viewModelScope.launch {
            appearancePreferences.setLanguage(language)
        }
    }
}
