package com.cydoniancitizen.bingee.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.LibraryEntry
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.core.result.AppError
import com.cydoniancitizen.bingee.core.result.AppResult
import com.cydoniancitizen.bingee.domain.repository.LibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal enum class LibraryFilter(val mediaType: MediaType?) {
    ALL(null),
    MOVIES(MediaType.MOVIE),
    TV_SERIES(MediaType.SERIES)
}

internal sealed interface LibraryContentState {
    data object Loading : LibraryContentState

    data object Empty : LibraryContentState

    data class Error(val error: AppError) : LibraryContentState

    data class Entries(val items: List<LibraryEntry>) : LibraryContentState
}

internal data class LibraryUiState(
    val filter: LibraryFilter = LibraryFilter.ALL,
    val content: LibraryContentState = LibraryContentState.Loading,
    val pendingRemovals: Set<ExternalMediaRef> = emptySet(),
    val actionError: AppError? = null
)

@HiltViewModel
internal class LibraryViewModel @Inject constructor(private val libraryRepository: LibraryRepository) : ViewModel() {
    private val mutableUiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = mutableUiState.asStateFlow()
    private var observationJob: Job? = null

    init {
        observeEntries()
    }

    fun onFilterChanged(filter: LibraryFilter) {
        if (filter == mutableUiState.value.filter) return
        mutableUiState.update { it.copy(filter = filter) }
        observeEntries()
    }

    fun retry() {
        observeEntries()
    }

    fun remove(entry: LibraryEntry) {
        val ref = entry.mediaRef
        if (ref in mutableUiState.value.pendingRemovals) return
        mutableUiState.update {
            it.copy(pendingRemovals = it.pendingRemovals + ref, actionError = null)
        }
        viewModelScope.launch {
            when (val result = libraryRepository.remove(ref)) {
                is AppResult.Success ->
                    mutableUiState.update { state ->
                        val content = state.content
                        val nextContent =
                            if (content is LibraryContentState.Entries) {
                                val remaining = content.items.filterNot { it.mediaRef == ref }
                                if (remaining.isEmpty()) {
                                    LibraryContentState.Empty
                                } else {
                                    LibraryContentState.Entries(remaining)
                                }
                            } else {
                                content
                            }
                        state.copy(
                            content = nextContent,
                            pendingRemovals = state.pendingRemovals - ref
                        )
                    }

                is AppResult.Failure ->
                    mutableUiState.update {
                        it.copy(
                            pendingRemovals = it.pendingRemovals - ref,
                            actionError = result.error
                        )
                    }
            }
        }
    }

    fun clearActionError() {
        mutableUiState.update { it.copy(actionError = null) }
    }

    private fun observeEntries() {
        observationJob?.cancel()
        mutableUiState.update { it.copy(content = LibraryContentState.Loading) }
        val mediaType = mutableUiState.value.filter.mediaType
        observationJob =
            viewModelScope.launch {
                libraryRepository.observeEntries(mediaType).collectLatest { result ->
                    mutableUiState.update { state ->
                        state.copy(
                            content =
                            when (result) {
                                is AppResult.Success ->
                                    if (result.value.isEmpty()) {
                                        LibraryContentState.Empty
                                    } else {
                                        LibraryContentState.Entries(result.value)
                                    }

                                is AppResult.Failure -> LibraryContentState.Error(result.error)
                            }
                        )
                    }
                }
            }
    }
}
