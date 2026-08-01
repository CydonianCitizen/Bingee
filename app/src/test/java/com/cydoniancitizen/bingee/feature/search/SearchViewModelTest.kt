package com.cydoniancitizen.bingee.feature.search

import com.cydoniancitizen.bingee.core.credential.TmdbCredentialStatus
import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.MediaSearchCategory
import com.cydoniancitizen.bingee.core.model.MediaSearchPage
import com.cydoniancitizen.bingee.core.model.MediaSearchQuery
import com.cydoniancitizen.bingee.core.model.MediaSearchResult
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.core.result.AppError
import com.cydoniancitizen.bingee.core.result.AppResult
import com.cydoniancitizen.bingee.domain.repository.MediaRepository
import com.cydoniancitizen.bingee.testutil.FakeCredentialRepository
import com.cydoniancitizen.bingee.testutil.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun initialStatesRespectCredentialAndBlankQuery() = runTest(mainDispatcherRule.dispatcher) {
        val missingRepository = RecordingMediaRepository()
        val missing =
            SearchViewModel(
                missingRepository,
                FakeCredentialRepository(TmdbCredentialStatus.NotConfigured)
            )
        runCurrent()
        assertEquals(SearchCredentialAvailability.REQUIRED, missing.uiState.value.credentialAvailability)
        assertEquals(SearchContentState.Idle, missing.uiState.value.content)

        val validRepository = RecordingMediaRepository()
        val valid =
            SearchViewModel(
                validRepository,
                FakeCredentialRepository(TmdbCredentialStatus.Valid)
            )
        runCurrent()
        valid.onQueryChanged("   ")
        advanceUntilIdle()
        assertEquals(SearchContentState.Idle, valid.uiState.value.content)
        assertTrue(validRepository.requests.isEmpty())
    }

    @Test
    fun queryIsTrimmedAtBoundaryAndDebounced() = runTest(mainDispatcherRule.dispatcher) {
        val repository = RecordingMediaRepository()
        val viewModel = validViewModel(repository)

        viewModel.onQueryChanged("  Star   Wars  ")
        advanceTimeBy(SearchViewModel.SEARCH_DEBOUNCE_MILLIS - 1)
        runCurrent()
        assertTrue(repository.requests.isEmpty())

        advanceTimeBy(1)
        runCurrent()

        assertEquals("Star   Wars", repository.requests.single().query)
        assertTrue(viewModel.uiState.value.content is SearchContentState.Results)
    }

    @Test
    fun obsoleteQueryCannotOverwriteNewerResults() = runTest(mainDispatcherRule.dispatcher) {
        val oldResult = CompletableDeferred<AppResult<MediaSearchPage>>()
        val repository =
            RecordingMediaRepository { query ->
                if (query.query == "old") {
                    withContext(NonCancellable) { oldResult.await() }
                } else {
                    AppResult.Success(page("new", query.page, 1))
                }
            }
        val viewModel = validViewModel(repository)

        viewModel.onQueryChanged("old")
        advanceTimeBy(SearchViewModel.SEARCH_DEBOUNCE_MILLIS)
        runCurrent()
        viewModel.onQueryChanged("new")
        advanceTimeBy(SearchViewModel.SEARCH_DEBOUNCE_MILLIS)
        runCurrent()
        oldResult.complete(AppResult.Success(page("old", 1, 1)))
        advanceUntilIdle()

        val content = viewModel.uiState.value.content as SearchContentState.Results
        assertEquals(listOf("new"), content.items.map { it.title })
        assertEquals(listOf("old", "new"), repository.requests.map { it.query })
    }

    @Test
    fun movieAndTvSearchResetStateOnCategoryChange() = runTest(mainDispatcherRule.dispatcher) {
        val repository =
            RecordingMediaRepository { query ->
                AppResult.Success(
                    page(
                        id = query.category.name,
                        page = 1,
                        totalPages = 1,
                        mediaType =
                        if (query.category == MediaSearchCategory.MOVIES) {
                            MediaType.MOVIE
                        } else {
                            MediaType.SERIES
                        }
                    )
                )
            }
        val viewModel = validViewModel(repository)
        performDebouncedSearch(viewModel, "fixed")

        viewModel.onCategoryChanged(MediaSearchCategory.TV_SERIES)
        runCurrent()

        assertEquals(
            listOf(MediaSearchCategory.MOVIES, MediaSearchCategory.TV_SERIES),
            repository.requests.map { it.category }
        )
        val result = (viewModel.uiState.value.content as SearchContentState.Results).items.single()
        assertEquals(MediaType.SERIES, result.mediaType)
    }

    @Test
    fun emptyErrorRetryUnauthorizedAndClearAreDistinct() = runTest(mainDispatcherRule.dispatcher) {
        var result: AppResult<MediaSearchPage> =
            AppResult.Failure(AppError.NetworkUnavailable)
        val repository = RecordingMediaRepository { result }
        val viewModel = validViewModel(repository)
        performDebouncedSearch(viewModel, "fixed")
        assertEquals(
            SearchContentState.Error(AppError.NetworkUnavailable),
            viewModel.uiState.value.content
        )

        result = AppResult.Success(MediaSearchPage(emptyList(), 1, 1, 0))
        viewModel.retryInitialSearch()
        runCurrent()
        assertEquals(SearchContentState.Empty, viewModel.uiState.value.content)

        result = AppResult.Failure(AppError.Unauthorized)
        viewModel.retryInitialSearch()
        runCurrent()
        assertEquals(
            SearchContentState.Error(AppError.Unauthorized),
            viewModel.uiState.value.content
        )

        viewModel.clearQuery()
        assertEquals("", viewModel.uiState.value.query)
        assertEquals(SearchContentState.Idle, viewModel.uiState.value.content)
    }

    @Test
    fun nextPageAppendsDeduplicatesAndEnds() = runTest(mainDispatcherRule.dispatcher) {
        val repository =
            RecordingMediaRepository { query ->
                if (query.page == 1) {
                    AppResult.Success(
                        MediaSearchPage(
                            results = listOf(result("1"), result("2")),
                            page = 1,
                            totalPages = 2,
                            totalResults = 3
                        )
                    )
                } else {
                    AppResult.Success(
                        MediaSearchPage(
                            results = listOf(result("2"), result("3")),
                            page = 2,
                            totalPages = 2,
                            totalResults = 3
                        )
                    )
                }
            }
        val viewModel = validViewModel(repository)
        performDebouncedSearch(viewModel, "fixed")

        viewModel.loadNextPage()
        runCurrent()

        val content = viewModel.uiState.value.content as SearchContentState.Results
        assertEquals(listOf("1", "2", "3"), content.items.map { it.externalRef.externalId })
        assertEquals(2, content.currentPage)
        assertEquals(NextPageState.End, content.nextPage)
    }

    @Test
    fun duplicateNextPageRequestsAreSuppressed() = runTest(mainDispatcherRule.dispatcher) {
        val pageGate = CompletableDeferred<Unit>()
        val repository =
            RecordingMediaRepository { query ->
                if (query.page == 2) pageGate.await()
                AppResult.Success(page(query.page.toString(), query.page, 2))
            }
        val viewModel = validViewModel(repository)
        performDebouncedSearch(viewModel, "fixed")

        viewModel.loadNextPage()
        runCurrent()
        viewModel.loadNextPage()
        runCurrent()

        assertEquals(1, repository.requests.count { it.page == 2 })
        assertEquals(
            NextPageState.Loading,
            (viewModel.uiState.value.content as SearchContentState.Results).nextPage
        )
        pageGate.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun paginationFailureRetainsResultsAndRetriesFailedPage() = runTest(mainDispatcherRule.dispatcher) {
        var failSecondPage = true
        val repository =
            RecordingMediaRepository { query ->
                if (query.page == 2 && failSecondPage) {
                    AppResult.Failure(AppError.RemoteServiceFailure)
                } else {
                    AppResult.Success(page(query.page.toString(), query.page, 2))
                }
            }
        val viewModel = validViewModel(repository)
        performDebouncedSearch(viewModel, "fixed")

        viewModel.loadNextPage()
        runCurrent()
        var content = viewModel.uiState.value.content as SearchContentState.Results
        assertEquals(listOf("1"), content.items.map { it.externalRef.externalId })
        assertEquals(
            NextPageState.Error(AppError.RemoteServiceFailure, 2),
            content.nextPage
        )

        failSecondPage = false
        viewModel.retryNextPage()
        runCurrent()
        content = viewModel.uiState.value.content as SearchContentState.Results
        assertEquals(listOf("1", "2"), content.items.map { it.externalRef.externalId })
        assertEquals(listOf(1, 2, 2), repository.requests.map { it.page })
    }

    @Test
    fun noUsableAdditionalRowsEndsPagination() = runTest(mainDispatcherRule.dispatcher) {
        val repository =
            RecordingMediaRepository { query ->
                AppResult.Success(
                    if (query.page == 1) {
                        MediaSearchPage(listOf(result("1")), 1, 3, 3)
                    } else {
                        MediaSearchPage(listOf(result("1")), 2, 3, 3)
                    }
                )
            }
        val viewModel = validViewModel(repository)
        performDebouncedSearch(viewModel, "fixed")

        viewModel.loadNextPage()
        runCurrent()

        val content = viewModel.uiState.value.content as SearchContentState.Results
        assertEquals(NextPageState.End, content.nextPage)
        assertEquals(listOf("1"), content.items.map { it.externalRef.externalId })
    }

    @Test
    fun queryChangeResetsPagination() = runTest(mainDispatcherRule.dispatcher) {
        val repository =
            RecordingMediaRepository { query ->
                AppResult.Success(page("${query.query}-${query.page}", query.page, 2))
            }
        val viewModel = validViewModel(repository)
        performDebouncedSearch(viewModel, "first")
        viewModel.loadNextPage()
        runCurrent()

        performDebouncedSearch(viewModel, "second")

        val content = viewModel.uiState.value.content as SearchContentState.Results
        assertEquals(1, content.currentPage)
        assertEquals(listOf("second-1"), content.items.map { it.externalRef.externalId })
    }

    @Test
    fun credentialRemovalCancelsActiveSearchAndClearsResults() = runTest(mainDispatcherRule.dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val credentialRepository = FakeCredentialRepository(TmdbCredentialStatus.Valid)
        val repository =
            RecordingMediaRepository { query ->
                gate.await()
                AppResult.Success(page(query.query, 1, 1))
            }
        val viewModel = SearchViewModel(repository, credentialRepository)
        runCurrent()
        viewModel.onQueryChanged("fixed")
        advanceTimeBy(SearchViewModel.SEARCH_DEBOUNCE_MILLIS)
        runCurrent()

        credentialRepository.emit(TmdbCredentialStatus.NotConfigured)
        runCurrent()
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(SearchCredentialAvailability.REQUIRED, viewModel.uiState.value.credentialAvailability)
        assertEquals(SearchContentState.Idle, viewModel.uiState.value.content)
        assertFalse(viewModel.uiState.value.toString().contains("credential_not_secret"))
    }

    private fun validViewModel(repository: MediaRepository): SearchViewModel {
        val viewModel =
            SearchViewModel(
                repository,
                FakeCredentialRepository(TmdbCredentialStatus.Valid)
            )
        return viewModel
    }

    private suspend fun kotlinx.coroutines.test.TestScope.performDebouncedSearch(
        viewModel: SearchViewModel,
        query: String
    ) {
        runCurrent()
        viewModel.onQueryChanged(query)
        advanceTimeBy(SearchViewModel.SEARCH_DEBOUNCE_MILLIS)
        runCurrent()
    }

    private class RecordingMediaRepository(
        var responder: suspend (MediaSearchQuery) -> AppResult<MediaSearchPage> = { query ->
            AppResult.Success(page(query.query, query.page, query.page))
        }
    ) : MediaRepository {
        val requests = mutableListOf<MediaSearchQuery>()

        override suspend fun search(query: MediaSearchQuery): AppResult<MediaSearchPage> {
            requests += query
            return responder(query)
        }
    }

    private companion object {
        fun page(id: String, page: Int, totalPages: Int, mediaType: MediaType = MediaType.MOVIE) = MediaSearchPage(
            results = listOf(result(id, mediaType)),
            page = page,
            totalPages = totalPages,
            totalResults = totalPages
        )

        fun result(id: String, mediaType: MediaType = MediaType.MOVIE) = MediaSearchResult(
            externalRef = ExternalMediaRef(MediaSource.TMDB, id),
            mediaType = mediaType,
            title = id
        )
    }
}
