package com.cydoniancitizen.bingee.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cydoniancitizen.bingee.core.credential.TmdbCredentialInputStatus
import com.cydoniancitizen.bingee.core.credential.TmdbCredentialStatus
import com.cydoniancitizen.bingee.core.credential.TmdbCredentialValidation
import com.cydoniancitizen.bingee.core.credential.TmdbCredentialValidator
import com.cydoniancitizen.bingee.core.result.AppError
import com.cydoniancitizen.bingee.core.result.AppResult
import com.cydoniancitizen.bingee.domain.repository.TmdbCredentialRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class OnboardingUiState(
    val credentialStatus: TmdbCredentialStatus = TmdbCredentialStatus.Checking,
    val inputStatus: TmdbCredentialInputStatus = TmdbCredentialInputStatus.EMPTY,
    val error: AppError? = null,
    val configured: Boolean = false
) {
    val isSubmitting: Boolean
        get() = credentialStatus is TmdbCredentialStatus.Validating
}

@HiltViewModel
internal class OnboardingViewModel @Inject constructor(
    private val repository: TmdbCredentialRepository,
    private val validator: TmdbCredentialValidator
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = mutableUiState.asStateFlow()
    private var validationJob: Job? = null

    init {
        viewModelScope.launch {
            repository.status.collect { status ->
                mutableUiState.update { current -> current.copy(credentialStatus = status) }
            }
        }
    }

    fun onInputChanged(input: String) {
        cancelCurrentValidation()
        mutableUiState.update {
            it.copy(inputStatus = validator.inputStatus(input), error = null, configured = false)
        }
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
            when (val result = repository.validateAndSave(input)) {
                is AppResult.Success -> mutableUiState.update {
                    it.copy(error = null, configured = true)
                }

                is AppResult.Failure -> mutableUiState.update {
                    it.copy(error = result.error, configured = false)
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
}
