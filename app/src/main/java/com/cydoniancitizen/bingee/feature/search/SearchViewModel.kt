package com.cydoniancitizen.bingee.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cydoniancitizen.bingee.core.credential.TmdbCredentialStatus
import com.cydoniancitizen.bingee.domain.repository.TmdbCredentialRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

internal enum class SearchShellState {
    CHECKING,
    CONFIGURATION_REQUIRED,
    COMING_SOON
}

@HiltViewModel
internal class SearchViewModel @Inject constructor(repository: TmdbCredentialRepository) : ViewModel() {
    val uiState: StateFlow<SearchShellState> =
        repository.status
            .map { status ->
                when (status) {
                    TmdbCredentialStatus.Checking,
                    is TmdbCredentialStatus.Validating -> SearchShellState.CHECKING

                    TmdbCredentialStatus.Valid -> SearchShellState.COMING_SOON
                    is TmdbCredentialStatus.TemporarilyUnverifiable ->
                        if (status.hasStoredCredential) {
                            SearchShellState.COMING_SOON
                        } else {
                            SearchShellState.CONFIGURATION_REQUIRED
                        }

                    TmdbCredentialStatus.NotConfigured,
                    TmdbCredentialStatus.StorageUnreadable ->
                        SearchShellState.CONFIGURATION_REQUIRED

                    is TmdbCredentialStatus.Rejected ->
                        if (status.hasStoredCredential) {
                            SearchShellState.COMING_SOON
                        } else {
                            SearchShellState.CONFIGURATION_REQUIRED
                        }
                }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = SearchShellState.CHECKING
            )
}
