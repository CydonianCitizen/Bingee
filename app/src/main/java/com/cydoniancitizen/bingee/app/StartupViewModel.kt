package com.cydoniancitizen.bingee.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cydoniancitizen.bingee.core.credential.TmdbCredentialStatus
import com.cydoniancitizen.bingee.data.settings.FirstRunPreferences
import com.cydoniancitizen.bingee.domain.repository.TmdbCredentialRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal enum class StartupDestination {
    ONBOARDING,
    SHELL
}

internal sealed interface StartupUiState {
    data object Checking : StartupUiState

    data class Ready(val destination: StartupDestination) : StartupUiState
}

@HiltViewModel
internal class StartupViewModel @Inject constructor(
    private val credentialRepository: TmdbCredentialRepository,
    private val firstRunPreferences: FirstRunPreferences
) : ViewModel() {
    private val mutableUiState = MutableStateFlow<StartupUiState>(StartupUiState.Checking)
    val uiState: StateFlow<StartupUiState> = mutableUiState.asStateFlow()

    init {
        viewModelScope.launch {
            credentialRepository.refreshLocalStatus()
            val onboardingComplete = readOnboardingCompleteSafely()
            val hasUsableCredential = credentialRepository.status.value == TmdbCredentialStatus.Valid
            mutableUiState.value =
                StartupUiState.Ready(
                    if (hasUsableCredential || onboardingComplete) {
                        StartupDestination.SHELL
                    } else {
                        StartupDestination.ONBOARDING
                    }
                )
        }
    }

    fun completeOnboarding() {
        mutableUiState.value = StartupUiState.Ready(StartupDestination.SHELL)
        viewModelScope.launch {
            try {
                firstRunPreferences.markOnboardingComplete()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Navigation remains available even if the non-sensitive preference cannot be written.
            }
        }
    }

    private suspend fun readOnboardingCompleteSafely(): Boolean = try {
        firstRunPreferences.isOnboardingComplete()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        false
    }
}
