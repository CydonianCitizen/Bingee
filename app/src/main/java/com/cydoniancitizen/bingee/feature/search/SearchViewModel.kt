package com.cydoniancitizen.bingee.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cydoniancitizen.bingee.core.credential.TmdbCredentialStatus
import com.cydoniancitizen.bingee.core.model.MediaSearchCategory
import com.cydoniancitizen.bingee.core.model.MediaSearchQuery
import com.cydoniancitizen.bingee.core.model.MediaSearchResult
import com.cydoniancitizen.bingee.core.result.AppError
import com.cydoniancitizen.bingee.core.result.AppResult
import com.cydoniancitizen.bingee.domain.repository.MediaRepository
import com.cydoniancitizen.bingee.domain.repository.TmdbCredentialRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal enum class SearchCredentialAvailability {
    CHECKING,
    AVAILABLE,
    REQUIRED
}

internal sealed interface SearchContentState {
    data object Idle : SearchContentState

    data object Loading : SearchContentState

    data object Empty : SearchContentState

    data class Error(val error: AppError) : SearchContentState

    data class Results(
        val items: List<MediaSearchResult>,
        val currentPage: Int,
        val totalPages: Int,
        val nextPage: NextPageState
    ) : SearchContentState
}

internal sealed interface NextPageState {
    data object Ready : NextPageState

    data object Loading : NextPageState

    data object End : NextPageState

    data class Error(val error: AppError, val failedPage: Int) : NextPageState
}

internal data class SearchUiState(
    val query: String = "",
    val category: MediaSearchCategory = MediaSearchCategory.MOVIES,
    val credentialAvailability: SearchCredentialAvailability = SearchCredentialAvailability.CHECKING,
    val content: SearchContentState = SearchContentState.Idle
)

@HiltViewModel
internal class SearchViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    credentialRepository: TmdbCredentialRepository
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = mutableUiState.asStateFlow()

    private var requestGeneration = 0L
    private var initialSearchJob: Job? = null
    private var nextPageJob: Job? = null

    init {
        viewModelScope.launch {
            credentialRepository.status.collectLatest(::onCredentialStatus)
        }
    }

    fun onQueryChanged(input: String) {
        val previousNormalized = normalizedQuery()
        mutableUiState.update { it.copy(query = input) }
        val normalized = normalizedQuery()
        if (normalized == previousNormalized) return

        resetRequests()
        mutableUiState.update { it.copy(content = SearchContentState.Idle) }
        if (normalized != null && hasCredential()) {
            startInitialSearch(SEARCH_DEBOUNCE_MILLIS)
        }
    }

    fun clearQuery() {
        onQueryChanged("")
    }

    fun onCategoryChanged(category: MediaSearchCategory) {
        if (category == mutableUiState.value.category) return
        resetRequests()
        mutableUiState.update {
            it.copy(category = category, content = SearchContentState.Idle)
        }
        if (normalizedQuery() != null && hasCredential()) {
            startInitialSearch(0)
        }
    }

    fun retryInitialSearch() {
        if (hasCredential() && normalizedQuery() != null) {
            startInitialSearch(0)
        }
    }

    fun loadNextPage() {
        val results = mutableUiState.value.content as? SearchContentState.Results ?: return
        if (results.nextPage != NextPageState.Ready) return
        loadPage(results.currentPage + 1)
    }

    fun retryNextPage() {
        val results = mutableUiState.value.content as? SearchContentState.Results ?: return
        val failed = results.nextPage as? NextPageState.Error ?: return
        loadPage(failed.failedPage)
    }

    private fun onCredentialStatus(status: TmdbCredentialStatus) {
        val availability = status.toSearchAvailability()
        if (availability == mutableUiState.value.credentialAvailability) return

        resetRequests()
        mutableUiState.update {
            it.copy(
                credentialAvailability = availability,
                content = SearchContentState.Idle
            )
        }
        if (availability == SearchCredentialAvailability.AVAILABLE && normalizedQuery() != null) {
            startInitialSearch(SEARCH_DEBOUNCE_MILLIS)
        }
    }

    private fun startInitialSearch(debounceMillis: Long) {
        val snapshot = mutableUiState.value
        val request = MediaSearchQuery.from(snapshot.query, snapshot.category) ?: return
        resetRequests()
        val generation = requestGeneration
        initialSearchJob = viewModelScope.launch {
            if (debounceMillis > 0) delay(debounceMillis)
            if (!isCurrent(generation, request)) return@launch

            mutableUiState.update { it.copy(content = SearchContentState.Loading) }
            when (val result = mediaRepository.search(request)) {
                is AppResult.Success -> {
                    if (!isCurrent(generation, request)) return@launch
                    val unique = result.value.results.distinctBy { it.externalRef }
                    mutableUiState.update {
                        it.copy(
                            content =
                            if (unique.isEmpty()) {
                                SearchContentState.Empty
                            } else {
                                SearchContentState.Results(
                                    items = unique,
                                    currentPage = result.value.page,
                                    totalPages = result.value.totalPages,
                                    nextPage =
                                    if (
                                        result.value.page >= result.value.totalPages ||
                                        result.value.results.isEmpty()
                                    ) {
                                        NextPageState.End
                                    } else {
                                        NextPageState.Ready
                                    }
                                )
                            }
                        )
                    }
                }

                is AppResult.Failure -> {
                    if (isCurrent(generation, request)) {
                        mutableUiState.update {
                            it.copy(content = SearchContentState.Error(result.error))
                        }
                    }
                }
            }
        }
    }

    private fun loadPage(page: Int) {
        val current = mutableUiState.value
        val results = current.content as? SearchContentState.Results ?: return
        if (
            page !in MediaSearchQuery.FIRST_PAGE..minOf(results.totalPages, MediaSearchQuery.MAX_PAGE) ||
            nextPageJob?.isActive == true ||
            !hasCredential()
        ) {
            return
        }
        val request = MediaSearchQuery.from(current.query, current.category, page) ?: return
        val generation = requestGeneration
        mutableUiState.update {
            it.copy(content = results.copy(nextPage = NextPageState.Loading))
        }
        nextPageJob = viewModelScope.launch {
            when (val result = mediaRepository.search(request)) {
                is AppResult.Success -> {
                    if (!isCurrent(generation, request)) return@launch
                    val latest = mutableUiState.value.content as? SearchContentState.Results ?: return@launch
                    val known = latest.items.mapTo(mutableSetOf()) { it.externalRef }
                    val additional = result.value.results.filter { known.add(it.externalRef) }
                    val ended =
                        additional.isEmpty() ||
                            result.value.page >= result.value.totalPages ||
                            result.value.page >= MediaSearchQuery.MAX_PAGE
                    mutableUiState.update {
                        it.copy(
                            content =
                            latest.copy(
                                items = latest.items + additional,
                                currentPage = result.value.page,
                                totalPages = result.value.totalPages,
                                nextPage = if (ended) NextPageState.End else NextPageState.Ready
                            )
                        )
                    }
                }

                is AppResult.Failure -> {
                    if (!isCurrent(generation, request)) return@launch
                    val latest = mutableUiState.value.content as? SearchContentState.Results ?: return@launch
                    mutableUiState.update {
                        it.copy(
                            content =
                            latest.copy(
                                nextPage = NextPageState.Error(result.error, page)
                            )
                        )
                    }
                }
            }
        }
    }

    private fun normalizedQuery(): String? = MediaSearchQuery.from(
        input = mutableUiState.value.query,
        category = mutableUiState.value.category
    )?.query

    private fun hasCredential(): Boolean =
        mutableUiState.value.credentialAvailability == SearchCredentialAvailability.AVAILABLE

    private fun isCurrent(generation: Long, request: MediaSearchQuery): Boolean {
        val current = mutableUiState.value
        return requestGeneration == generation &&
            current.credentialAvailability == SearchCredentialAvailability.AVAILABLE &&
            current.category == request.category &&
            normalizedQuery() == request.query
    }

    private fun resetRequests() {
        requestGeneration++
        initialSearchJob?.cancel()
        nextPageJob?.cancel()
        initialSearchJob = null
        nextPageJob = null
    }

    internal companion object {
        const val SEARCH_DEBOUNCE_MILLIS = 350L
    }
}

private fun TmdbCredentialStatus.toSearchAvailability(): SearchCredentialAvailability = when (this) {
    TmdbCredentialStatus.Checking -> SearchCredentialAvailability.CHECKING
    TmdbCredentialStatus.Valid -> SearchCredentialAvailability.AVAILABLE
    TmdbCredentialStatus.NotConfigured,
    TmdbCredentialStatus.StorageUnreadable ->
        SearchCredentialAvailability.REQUIRED

    is TmdbCredentialStatus.Validating ->
        if (hasStoredCredential) {
            SearchCredentialAvailability.AVAILABLE
        } else {
            SearchCredentialAvailability.CHECKING
        }

    is TmdbCredentialStatus.Rejected ->
        if (hasStoredCredential) {
            SearchCredentialAvailability.AVAILABLE
        } else {
            SearchCredentialAvailability.REQUIRED
        }

    is TmdbCredentialStatus.TemporarilyUnverifiable ->
        if (hasStoredCredential) {
            SearchCredentialAvailability.AVAILABLE
        } else {
            SearchCredentialAvailability.REQUIRED
        }
}
