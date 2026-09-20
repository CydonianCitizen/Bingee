package com.cydoniancitizen.bingee.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cydoniancitizen.bingee.core.result.AppError
import com.cydoniancitizen.bingee.data.settings.AppLanguage
import com.cydoniancitizen.bingee.data.settings.AppTheme
import com.cydoniancitizen.bingee.data.settings.AppearancePreferences
import com.cydoniancitizen.bingee.data.settings.SpoilerPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class AppearanceLanguageUiState(
    val theme: AppTheme = AppTheme.SYSTEM_DEFAULT,
    val language: AppLanguage = AppLanguage.ENGLISH,
    val hideSpoilers: Boolean = false,
    val error: AppError? = null
)

@HiltViewModel
internal class AppearanceLanguageViewModel @Inject constructor(
    private val appearancePreferences: AppearancePreferences,
    private val spoilerPreferences: SpoilerPreferences
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
        viewModelScope.launch {
            spoilerPreferences.observeHideSpoilers().collect { hide ->
                mutableUiState.update { it.copy(hideSpoilers = hide) }
            }
        }
    }

    fun setHideSpoilers(hide: Boolean) = save { spoilerPreferences.setHideSpoilers(hide) }

    fun setTheme(theme: AppTheme) = save { appearancePreferences.setTheme(theme) }

    fun setLanguage(language: AppLanguage) {
        if (language == mutableUiState.value.language) return
        save { appearancePreferences.setLanguage(language) }
    }

    fun clearError() {
        mutableUiState.update { it.copy(error = null) }
    }

    /** The state shows only what the preferences emit, so a failed write keeps the last stored value on screen. */
    private fun save(write: suspend () -> Unit) {
        mutableUiState.update { it.copy(error = null) }
        viewModelScope.launch {
            try {
                write()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                mutableUiState.update { it.copy(error = AppError.LocalStorageFailure) }
            }
        }
    }
}
