package com.cydoniancitizen.bingee.feature.details

import androidx.lifecycle.SavedStateHandle
import com.cydoniancitizen.bingee.core.model.CacheFreshness
import com.cydoniancitizen.bingee.core.model.CachedMediaDetails
import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.LibraryEntry
import com.cydoniancitizen.bingee.core.model.MediaDetails
import com.cydoniancitizen.bingee.core.model.MediaSearchResult
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.core.navigation.DetailRoute
import com.cydoniancitizen.bingee.core.result.AppError
import com.cydoniancitizen.bingee.core.result.AppResult
import com.cydoniancitizen.bingee.domain.repository.LibraryRepository
import com.cydoniancitizen.bingee.domain.repository.MediaDetailsRepository
import com.cydoniancitizen.bingee.testutil.MainDispatcherRule
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MediaDetailsViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()
    private val ref = ExternalMediaRef(MediaSource.TMDB, "550")

    @Test
    fun malformedAndUnsupportedRoutesFailSafelyWithoutRefresh() = runTest(mainDispatcherRule.dispatcher) {
        val remote = FakeDetailsRepository()
        val malformed = viewModel(SavedStateHandle(), remote)
        val unsupported = viewModel(args(MediaSource.JIKAN), remote)
        runCurrent()

        assertEquals(DetailContentState.Error(AppError.InvalidInput), malformed.uiState.value.content)
        assertEquals(DetailContentState.Error(AppError.UnsupportedData), unsupported.uiState.value.content)
        assertTrue(remote.refreshes.isEmpty())
    }

    @Test
    fun cacheMissLoadsThenShowsFullErrorWithoutClearingLibraryState() = runTest(mainDispatcherRule.dispatcher) {
        val remote = FakeDetailsRepository(refreshResult = AppResult.Failure(AppError.NetworkUnavailable))
        val library = FakeLibraryRepository(member = true)
        val viewModel = viewModel(args(), remote, library)
        runCurrent()

        assertEquals(DetailContentState.Error(AppError.NetworkUnavailable), viewModel.uiState.value.content)
        assertEquals(true, viewModel.uiState.value.isInLibrary)
        assertEquals(listOf(false), remote.refreshes.map { it.third })
    }

    @Test
    fun freshCacheDisplaysWithoutAutomaticRefresh() = runTest(mainDispatcherRule.dispatcher) {
        val remote = FakeDetailsRepository(cached(CacheFreshness.FRESH))
        val viewModel = viewModel(args(), remote)
        runCurrent()

        assertTrue(viewModel.uiState.value.content is DetailContentState.Content)
        assertTrue(remote.refreshes.isEmpty())
    }

    @Test
    fun staleCacheRemainsVisibleWhenBackgroundRefreshFails() = runTest(mainDispatcherRule.dispatcher) {
        val remote = FakeDetailsRepository(
            cached(CacheFreshness.STALE),
            AppResult.Failure(AppError.NetworkUnavailable)
        )
        val viewModel = viewModel(args(), remote)
        runCurrent()

        assertTrue(viewModel.uiState.value.content is DetailContentState.Content)
        assertEquals(DetailRefreshState.Error(AppError.NetworkUnavailable), viewModel.uiState.value.refresh)
        assertEquals(listOf(false), remote.refreshes.map { it.third })
    }

    @Test
    fun manualRefreshForcesRemoteAndPreservesVisibleContent() = runTest(mainDispatcherRule.dispatcher) {
        val remote = FakeDetailsRepository(
            cached(CacheFreshness.FRESH),
            AppResult.Failure(AppError.Unauthorized)
        )
        val viewModel = viewModel(args(), remote)
        runCurrent()
        viewModel.refresh()
        viewModel.refresh()
        runCurrent()

        assertTrue(viewModel.uiState.value.content is DetailContentState.Content)
        assertEquals(DetailRefreshState.Error(AppError.Unauthorized), viewModel.uiState.value.refresh)
        assertEquals(listOf(true), remote.refreshes.map { it.third })
    }

    @Test
    fun membershipObservationAddAndRemoveDoNotReplaceContent() = runTest(mainDispatcherRule.dispatcher) {
        val library = FakeLibraryRepository(member = false)
        val viewModel = viewModel(args(), FakeDetailsRepository(cached(CacheFreshness.FRESH)), library)
        runCurrent()

        assertEquals(false, viewModel.uiState.value.isInLibrary)
        viewModel.toggleLibrary()
        runCurrent()
        assertEquals(true, viewModel.uiState.value.isInLibrary)
        assertTrue(viewModel.uiState.value.content is DetailContentState.Content)
        viewModel.toggleLibrary()
        runCurrent()
        assertEquals(false, viewModel.uiState.value.isInLibrary)
        assertEquals(listOf("add", "remove"), library.actions)
    }

    @Test
    fun libraryFailureKeepsContentAndMembership() = runTest(mainDispatcherRule.dispatcher) {
        val library = FakeLibraryRepository(member = false, failure = AppError.LocalStorageFailure)
        val viewModel = viewModel(args(), FakeDetailsRepository(cached(CacheFreshness.FRESH)), library)
        runCurrent()
        viewModel.toggleLibrary()
        runCurrent()

        assertFalse(viewModel.uiState.value.isInLibrary ?: true)
        assertEquals(AppError.LocalStorageFailure, viewModel.uiState.value.libraryError)
        assertTrue(viewModel.uiState.value.content is DetailContentState.Content)
    }

    private fun viewModel(
        state: SavedStateHandle,
        details: FakeDetailsRepository,
        library: FakeLibraryRepository = FakeLibraryRepository()
    ) = MediaDetailsViewModel(state, details, library)

    private fun args(source: MediaSource = MediaSource.TMDB) = SavedStateHandle(
        mapOf(
            DetailRoute.SOURCE_ARG to source.name,
            DetailRoute.MEDIA_TYPE_ARG to MediaType.MOVIE.name,
            DetailRoute.EXTERNAL_ID_ARG to "550"
        )
    )

    private fun cached(freshness: CacheFreshness) = CachedMediaDetails(
        details = MediaDetails(ref, MediaType.MOVIE, "Cached movie"),
        fetchedAt = Instant.parse("2026-08-03T10:00:00Z"),
        freshness = freshness
    )

    private class FakeDetailsRepository(
        initial: CachedMediaDetails? = null,
        var refreshResult: AppResult<Unit> = AppResult.Success(Unit)
    ) : MediaDetailsRepository {
        val observed = MutableStateFlow<AppResult<CachedMediaDetails?>>(AppResult.Success(initial))
        val refreshes = mutableListOf<Triple<ExternalMediaRef, MediaType, Boolean>>()
        override fun observeDetails(reference: ExternalMediaRef): Flow<AppResult<CachedMediaDetails?>> = observed
        override suspend fun refreshDetails(
            reference: ExternalMediaRef,
            mediaType: MediaType,
            force: Boolean
        ): AppResult<Unit> {
            refreshes += Triple(reference, mediaType, force)
            return refreshResult
        }
    }

    private class FakeLibraryRepository(member: Boolean = false, private val failure: AppError? = null) :
        LibraryRepository {
        private val entry = MutableStateFlow(if (member) libraryEntry() else null)
        val actions = mutableListOf<String>()
        override fun observeEntries(mediaType: MediaType?): Flow<AppResult<List<LibraryEntry>>> =
            entry.map { AppResult.Success(listOfNotNull(it)) }
        override fun observeEntry(ref: ExternalMediaRef): Flow<AppResult<LibraryEntry?>> =
            entry.map { AppResult.Success(it) }
        override fun observeMembershipRefs(): Flow<AppResult<Set<ExternalMediaRef>>> =
            entry.map { AppResult.Success(listOfNotNull(it?.mediaRef).toSet()) }
        override suspend fun add(result: MediaSearchResult): AppResult<LibraryEntry> = error("unused")
        override suspend fun add(ref: ExternalMediaRef): AppResult<LibraryEntry> {
            actions += "add"
            failure?.let { return AppResult.Failure(it) }
            return AppResult.Success(libraryEntry()).also { entry.value = it.value }
        }
        override suspend fun remove(ref: ExternalMediaRef): AppResult<Unit> {
            actions += "remove"
            failure?.let { return AppResult.Failure(it) }
            entry.value = null
            return AppResult.Success(Unit)
        }
        override suspend fun isInLibrary(ref: ExternalMediaRef): AppResult<Boolean> =
            AppResult.Success(entry.value != null)

        companion object {
            fun libraryEntry() = LibraryEntry(
                mediaRef = ExternalMediaRef(MediaSource.TMDB, "550"),
                mediaType = MediaType.MOVIE,
                title = "Cached movie",
                addedAt = Instant.parse("2026-08-03T10:00:00Z")
            )
        }
    }
}
