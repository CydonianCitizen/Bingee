package com.cydoniancitizen.bingee.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cydoniancitizen.bingee.core.model.NotificationCapabilityStatus
import com.cydoniancitizen.bingee.core.model.ReleaseNotificationLeadTime
import com.cydoniancitizen.bingee.core.model.ReleaseNotificationPreferences
import com.cydoniancitizen.bingee.core.result.AppError
import com.cydoniancitizen.bingee.domain.background.BackgroundWorkScheduler
import com.cydoniancitizen.bingee.domain.notification.ReleaseNotificationCapability
import com.cydoniancitizen.bingee.domain.repository.ReleaseNotificationPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class ReleaseNotificationSettingsUiState(
    val preferences: ReleaseNotificationPreferences = ReleaseNotificationPreferences(),
    val capability: NotificationCapabilityStatus = NotificationCapabilityStatus.PERMISSION_DENIED,
    val permanentlyDenied: Boolean = false,
    val isUpdating: Boolean = false,
    val error: AppError? = null
)

@HiltViewModel
internal class ReleaseNotificationSettingsViewModel @Inject constructor(
    private val preferencesRepository: ReleaseNotificationPreferencesRepository,
    private val capability: ReleaseNotificationCapability,
    private val scheduler: BackgroundWorkScheduler
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(
        ReleaseNotificationSettingsUiState(capability = capability.status())
    )
    val uiState: StateFlow<ReleaseNotificationSettingsUiState> = mutableUiState.asStateFlow()

    init {
        viewModelScope.launch {
            preferencesRepository.preferences.collect { preferences ->
                mutableUiState.update {
                    it.copy(preferences = preferences, capability = capability.status())
                }
            }
        }
    }

    fun onEnableRequested(): Boolean {
        if (mutableUiState.value.preferences.enabled || mutableUiState.value.isUpdating) return false
        capability.ensureChannel()
        return when (val current = capability.status()) {
            NotificationCapabilityStatus.AVAILABLE -> {
                enableNotifications()
                false
            }
            NotificationCapabilityStatus.PERMISSION_DENIED -> true
            else -> {
                mutableUiState.update { it.copy(capability = current) }
                false
            }
        }
    }

    fun onPermissionResult(granted: Boolean, permanentlyDenied: Boolean) {
        if (granted) {
            mutableUiState.update { it.copy(permanentlyDenied = false) }
            enableNotifications()
        } else {
            mutableUiState.update {
                it.copy(
                    capability = NotificationCapabilityStatus.PERMISSION_DENIED,
                    permanentlyDenied = permanentlyDenied
                )
            }
            runUpdate { scheduler.reconcileNotificationWork(false) }
        }
    }

    fun disableNotifications() {
        runUpdate {
            preferencesRepository.setEnabled(false)
            scheduler.reconcileNotificationWork(false)
        }
    }

    fun setLeadTime(value: ReleaseNotificationLeadTime) = updatePreference {
        preferencesRepository.setLeadTime(value)
    }

    fun setMovieReleases(enabled: Boolean) = updatePreference {
        preferencesRepository.setMovieReleases(enabled)
    }

    fun setSeasonPremieres(enabled: Boolean) = updatePreference {
        preferencesRepository.setSeasonPremieres(enabled)
    }

    fun setEpisodeAirings(enabled: Boolean) = updatePreference {
        preferencesRepository.setEpisodeAirings(enabled)
    }

    fun openSystemSettings() {
        capability.openSystemSettings()
    }

    private fun enableNotifications() {
        mutableUiState.update { it.copy(capability = NotificationCapabilityStatus.AVAILABLE) }
        runUpdate {
            preferencesRepository.setEnabled(true)
            scheduler.reconcileNotificationWork(true)
            scheduler.enqueueImmediateNotificationEvaluation()
        }
    }

    private fun updatePreference(update: suspend () -> Unit) {
        runUpdate {
            update()
            if (mutableUiState.value.preferences.enabled &&
                capability.status() == NotificationCapabilityStatus.AVAILABLE
            ) {
                scheduler.reconcileNotificationWork(true)
                scheduler.enqueueImmediateNotificationEvaluation()
            }
        }
    }

    fun clearError() {
        mutableUiState.update { it.copy(error = null) }
    }

    private fun runUpdate(operation: suspend () -> Unit) {
        if (mutableUiState.value.isUpdating) return
        mutableUiState.update { it.copy(isUpdating = true, error = null) }
        viewModelScope.launch {
            try {
                operation()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                mutableUiState.update { it.copy(error = AppError.LocalStorageFailure) }
            } finally {
                mutableUiState.update { it.copy(isUpdating = false) }
            }
        }
    }
}
