package com.cydoniancitizen.bingee.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cydoniancitizen.bingee.core.common.AnimeFeatureAvailability
import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.LibraryEntry
import com.cydoniancitizen.bingee.core.model.LibraryMediaFilter
import com.cydoniancitizen.bingee.core.model.LibraryQuery
import com.cydoniancitizen.bingee.core.model.LibrarySort
import com.cydoniancitizen.bingee.core.model.LibraryStateFilter
import com.cydoniancitizen.bingee.core.result.AppError
import com.cydoniancitizen.bingee.core.result.AppResult
import com.cydoniancitizen.bingee.domain.equivalence.MediaEquivalenceCandidate
import com.cydoniancitizen.bingee.domain.repository.LibraryRepository
import com.cydoniancitizen.bingee.domain.repository.MediaEquivalenceCandidateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal sealed interface LibraryContentState {
    data object Loading : LibraryContentState

    data object Empty : LibraryContentState

    data object NoResults : LibraryContentState

    data class Error(val error: AppError) : LibraryContentState

    data class Entries(val items: List<LibraryEntry>) : LibraryContentState
}

internal data class LibraryUiState(
    val query: LibraryQuery = LibraryQuery(),
    val content: LibraryContentState = LibraryContentState.Loading,
    val presentationItems: List<LibraryPresentationItem> = emptyList(),
    val candidates: List<MediaEquivalenceCandidate> = emptyList(),
    val availableMediaFilters: List<LibraryMediaFilter> =
        listOf(LibraryMediaFilter.ALL, LibraryMediaFilter.MOVIES, LibraryMediaFilter.TV_SERIES),
    val resultCount: Int = 0,
    val totalEntryCount: Int? = null,
    val pendingRemovals: Set<ExternalMediaRef> = emptySet(),
    val actionError: AppError? = null
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
internal class LibraryViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
    private val candidateRepository: MediaEquivalenceCandidateRepository,
    private val animeAvailability: AnimeFeatureAvailability
) : ViewModel() {
    private val mutableUiState: MutableStateFlow<LibraryUiState>
    val uiState: StateFlow<LibraryUiState>
    private val query = MutableStateFlow(LibraryQuery())
    private val retryTrigger = MutableStateFlow(0)

    init {
        val filters = if (animeAvailability.isAvailable) {
            LibraryMediaFilter.entries
        } else {
            listOf(LibraryMediaFilter.ALL, LibraryMediaFilter.MOVIES, LibraryMediaFilter.TV_SERIES)
        }
        mutableUiState = MutableStateFlow(LibraryUiState(availableMediaFilters = filters))
        uiState = mutableUiState.asStateFlow()

        observeEntries()
        observeEntryCount()
        observeCandidates()
    }

    fun onSearchQueryChanged(value: String) = updateQuery { copy(searchQuery = value) }

    fun clearSearch() = updateQuery { copy(searchQuery = "") }

    fun onMediaFilterChanged(filter: LibraryMediaFilter) = updateQuery {
        val targetFilter = if (!animeAvailability.isAvailable && filter == LibraryMediaFilter.ANIME) {
            LibraryMediaFilter.ALL
        } else {
            filter
        }
        val nextState = if (
            targetFilter == LibraryMediaFilter.MOVIES &&
            stateFilter in setOf(LibraryStateFilter.IN_PROGRESS, LibraryStateFilter.PROGRESS_UNAVAILABLE)
        ) {
            LibraryStateFilter.ALL
        } else {
            stateFilter
        }
        copy(mediaFilter = targetFilter, stateFilter = nextState)
    }

    fun onStateFilterChanged(filter: LibraryStateFilter) = updateQuery { copy(stateFilter = filter) }

    fun onSortChanged(sort: LibrarySort) = updateQuery { copy(sort = sort) }

    fun retry() {
        retryTrigger.value += 1
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
                        state.copy(
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
        viewModelScope.launch {
            combine(query, retryTrigger) { currentQuery, _ -> currentQuery }
                .flatMapLatest(libraryRepository::observeEntries)
                .collectLatest { result ->
                    mutableUiState.update { state ->
                        state.copy(
                            content =
                            when (result) {
                                is AppResult.Success -> result.value.toContent(state.totalEntryCount)

                                is AppResult.Failure -> LibraryContentState.Error(result.error)
                            },
                            resultCount = (result as? AppResult.Success)?.value?.size ?: state.resultCount
                        )
                    }
                }
        }
    }

    private fun observeEntryCount() {
        viewModelScope.launch {
            libraryRepository.observeEntryCount().collectLatest { result ->
                if (result is AppResult.Success) {
                    mutableUiState.update { state ->
                        state.copy(
                            totalEntryCount = result.value,
                            content = when {
                                result.value == 0 -> LibraryContentState.Empty
                                state.content == LibraryContentState.Empty -> LibraryContentState.NoResults
                                else -> state.content
                            }
                        )
                    }
                }
            }
        }
    }

    private fun observeCandidates() {
        if (!animeAvailability.isAvailable) {
            mutableUiState.update { it.copy(candidates = emptyList()) }
            return
        }
        viewModelScope.launch {
            candidateRepository.observeLibraryCandidates().collectLatest { candidates ->
                mutableUiState.update { it.copy(candidates = candidates) }
            }
        }
    }

    private fun updateQuery(transform: LibraryQuery.() -> LibraryQuery) {
        val updated = query.value.transform()
        if (updated == query.value) return
        query.value = updated
        mutableUiState.update { it.copy(query = updated, content = LibraryContentState.Loading) }
    }
}

private fun List<LibraryEntry>.toContent(totalEntryCount: Int?): LibraryContentState = when {
    isNotEmpty() -> LibraryContentState.Entries(this)
    totalEntryCount == 0 -> LibraryContentState.Empty
    else -> LibraryContentState.NoResults
}
