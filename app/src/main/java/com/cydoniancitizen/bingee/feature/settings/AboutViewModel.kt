package com.cydoniancitizen.bingee.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cydoniancitizen.bingee.BuildConfig
import com.cydoniancitizen.bingee.core.result.AppError
import com.cydoniancitizen.bingee.domain.repository.AppUpdateRepository
import com.cydoniancitizen.bingee.domain.repository.AppUpdateResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface UpdateCheckUiState {
    data object Idle : UpdateCheckUiState
    data object Checking : UpdateCheckUiState
    data class UpToDate(val installedVersion: String) : UpdateCheckUiState
    data class UpdateAvailable(val installedVersion: String, val latestVersion: String, val releaseUrl: String) :
        UpdateCheckUiState
    data class Error(val error: AppError) : UpdateCheckUiState
}

data class AboutUiState(
    val installedVersion: String = BuildConfig.VERSION_NAME,
    val updateState: UpdateCheckUiState = UpdateCheckUiState.Idle
)

@HiltViewModel
internal class AboutViewModel @Inject constructor(private val appUpdateRepository: AppUpdateRepository) : ViewModel() {

    private val mutableUiState = MutableStateFlow(AboutUiState())
    val uiState: StateFlow<AboutUiState> = mutableUiState.asStateFlow()

    fun checkForUpdates() {
        if (mutableUiState.value.updateState is UpdateCheckUiState.Checking) return

        mutableUiState.update { it.copy(updateState = UpdateCheckUiState.Checking) }

        viewModelScope.launch {
            val result = appUpdateRepository.checkForUpdates(mutableUiState.value.installedVersion)
            val nextState = when (result) {
                is AppUpdateResult.UpToDate -> UpdateCheckUiState.UpToDate(result.installedVersion)
                is AppUpdateResult.UpdateAvailable -> UpdateCheckUiState.UpdateAvailable(
                    installedVersion = result.installedVersion,
                    latestVersion = result.latestVersion,
                    releaseUrl = result.releaseUrl
                )
                is AppUpdateResult.Error -> UpdateCheckUiState.Error(result.error)
            }
            mutableUiState.update { it.copy(updateState = nextState) }
        }
    }
}
