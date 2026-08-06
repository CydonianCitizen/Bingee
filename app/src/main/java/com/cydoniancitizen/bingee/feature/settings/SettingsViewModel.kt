package com.cydoniancitizen.bingee.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cydoniancitizen.bingee.core.credential.TmdbCredentialInputStatus
import com.cydoniancitizen.bingee.core.credential.TmdbCredentialStatus
import com.cydoniancitizen.bingee.core.credential.TmdbCredentialValidation
import com.cydoniancitizen.bingee.core.credential.TmdbCredentialValidator
import com.cydoniancitizen.bingee.core.result.AppError
import com.cydoniancitizen.bingee.core.result.AppResult
import com.cydoniancitizen.bingee.data.settings.AppLanguage
import com.cydoniancitizen.bingee.data.settings.AppTheme
import com.cydoniancitizen.bingee.data.settings.AppearancePreferences
import com.cydoniancitizen.bingee.domain.repository.TmdbCredentialRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class SettingsUiState(
    val credentialStatus: TmdbCredentialStatus = TmdbCredentialStatus.Checking,
    val inputStatus: TmdbCredentialInputStatus = TmdbCredentialInputStatus.EMPTY,
    val error: AppError? = null,
    val showRemovalConfirmation: Boolean = false,
    val isRemoving: Boolean = false,
    val theme: AppTheme = AppTheme.SYSTEM_DEFAULT,
    val language: AppLanguage = AppLanguage.ENGLISH
) {
    val isSubmitting: Boolean
        get() = credentialStatus is TmdbCredentialStatus.Validating

    val canRemove: Boolean
        get() =
            credentialStatus == TmdbCredentialStatus.Valid ||
                (
                    credentialStatus is TmdbCredentialStatus.Rejected &&
                        credentialStatus.hasStoredCredential
                    ) ||
                (
                    credentialStatus is TmdbCredentialStatus.TemporarilyUnverifiable &&
                        credentialStatus.hasStoredCredential
                    ) ||
                credentialStatus == TmdbCredentialStatus.StorageUnreadable
}

@HiltViewModel
internal class SettingsViewModel @Inject constructor(
    private val repository: TmdbCredentialRepository,
    private val validator: TmdbCredentialValidator,
    private val appearancePreferences: AppearancePreferences
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = mutableUiState.asStateFlow()
    private var validationJob: Job? = null
    private var removalJob: Job? = null

    init {
        viewModelScope.launch {
            repository.status.collect { status ->
                mutableUiState.update { current -> current.copy(credentialStatus = status) }
            }
        }
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

    fun onInputChanged(input: String) {
        cancelCurrentValidation()
        mutableUiState.update { it.copy(inputStatus = validator.inputStatus(input), error = null) }
    }

    fun submit(input: String) {
        if (validationJob?.isActive == true) return
        if (validator.validate(input) !is TmdbCredentialValidation.Valid) {
            mutableUiState.update {
                it.copy(
                    inputStatus = TmdbCredentialInputStatus.LOCALLY_INVALID,
                    error = AppError.InvalidInput
                )
            }
            return
        }
        validationJob = viewModelScope.launch {
            mutableUiState.update { it.copy(error = null) }
            when (val result = repository.validateAndSave(input)) {
                is AppResult.Success -> mutableUiState.update {
                    it.copy(inputStatus = TmdbCredentialInputStatus.EMPTY, error = null)
                }

                is AppResult.Failure -> mutableUiState.update { it.copy(error = result.error) }
            }
        }
    }

    fun retry(input: String) {
        if (input.isNotBlank()) {
            submit(input)
            return
        }
        if (validationJob?.isActive == true) return
        validationJob = viewModelScope.launch {
            when (val result = repository.revalidateStored()) {
                is AppResult.Success -> mutableUiState.update { it.copy(error = null) }
                is AppResult.Failure -> mutableUiState.update { it.copy(error = result.error) }
            }
        }
    }

    fun requestRemoval() {
        if (mutableUiState.value.canRemove) {
            mutableUiState.update { it.copy(showRemovalConfirmation = true) }
        }
    }

    fun dismissRemoval() {
        mutableUiState.update { it.copy(showRemovalConfirmation = false) }
    }

    fun confirmRemoval() {
        if (removalJob?.isActive == true) return
        mutableUiState.update { it.copy(showRemovalConfirmation = false, isRemoving = true) }
        removalJob = viewModelScope.launch {
            when (val result = repository.remove()) {
                is AppResult.Success -> mutableUiState.update {
                    it.copy(
                        inputStatus = TmdbCredentialInputStatus.EMPTY,
                        error = null,
                        isRemoving = false
                    )
                }

                is AppResult.Failure -> mutableUiState.update {
                    it.copy(error = result.error, isRemoving = false)
                }
            }
        }
    }

    private fun cancelCurrentValidation() {
        if (validationJob?.isActive == true) {
            repository.cancelValidation()
            validationJob?.cancel()
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
