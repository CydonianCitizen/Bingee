package com.cydoniancitizen.bingee.feature.library

import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.LibraryEntry
import com.cydoniancitizen.bingee.core.model.LibraryMediaFilter
import com.cydoniancitizen.bingee.core.model.LibraryProgress
import com.cydoniancitizen.bingee.core.model.LibrarySort
import com.cydoniancitizen.bingee.core.model.LibraryStateFilter
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.core.model.MovieWatchState
import com.cydoniancitizen.bingee.core.model.PersonalRating
import com.cydoniancitizen.bingee.core.result.AppError
import com.cydoniancitizen.bingee.debug.FakeLibraryRepository
import com.cydoniancitizen.bingee.testutil.MainDispatcherRule
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun queryFilterAndSortChangesCombineWithoutNetworkOrDelay() = runTest(mainDispatcherRule.dispatcher) {
        val movie = entry("1", MediaType.MOVIE, "Arrival", rating = 8)
        val series = entry("2", MediaType.SERIES, "Dark")
        val viewModel = LibraryViewModel(FakeLibraryRepository(listOf(series, movie)))
        runCurrent()

        viewModel.onSearchQueryChanged(" arrival ")
        viewModel.onMediaFilterChanged(LibraryMediaFilter.MOVIES)
        viewModel.onStateFilterChanged(LibraryStateFilter.NOT_STARTED)
        viewModel.onSortChanged(LibrarySort.PERSONAL_RATING)
        runCurrent()

        assertEquals(" arrival ", viewModel.uiState.value.query.searchQuery)
        assertEquals(LibraryMediaFilter.MOVIES, viewModel.uiState.value.query.mediaFilter)
        assertEquals(LibraryStateFilter.NOT_STARTED, viewModel.uiState.value.query.stateFilter)
        assertEquals(LibrarySort.PERSONAL_RATING, viewModel.uiState.value.query.sort)
        assertEquals(listOf(movie), (viewModel.uiState.value.content as LibraryContentState.Entries).items)
        assertEquals(1, viewModel.uiState.value.resultCount)

        viewModel.clearSearch()
        runCurrent()
        assertEquals("", viewModel.uiState.value.query.searchQuery)
    }

    @Test
    fun impossibleMovieStateFilterResetsPredictably() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = LibraryViewModel(FakeLibraryRepository(listOf(entry("1", MediaType.MOVIE, "Movie"))))
        runCurrent()
        viewModel.onStateFilterChanged(LibraryStateFilter.IN_PROGRESS)
        viewModel.onMediaFilterChanged(LibraryMediaFilter.MOVIES)
        runCurrent()

        assertEquals(LibraryStateFilter.ALL, viewModel.uiState.value.query.stateFilter)
    }

    @Test
    fun noMatchDiffersFromEmptyLibrary() = runTest(mainDispatcherRule.dispatcher) {
        val populated = LibraryViewModel(FakeLibraryRepository(listOf(entry("1", MediaType.MOVIE, "Movie"))))
        populated.onSearchQueryChanged("missing")
        runCurrent()
        assertEquals(LibraryContentState.NoResults, populated.uiState.value.content)

        val empty = LibraryViewModel(FakeLibraryRepository())
        runCurrent()
        assertEquals(LibraryContentState.Empty, empty.uiState.value.content)
    }

    @Test
    fun removePreservesControlsAndFailureIsSafe() = runTest(mainDispatcherRule.dispatcher) {
        val movie = entry("1", MediaType.MOVIE, "Movie")
        val repository = FakeLibraryRepository(listOf(movie))
        val viewModel = LibraryViewModel(repository)
        viewModel.onSortChanged(LibrarySort.TITLE)
        runCurrent()
        viewModel.remove(movie)
        runCurrent()

        assertEquals(LibraryContentState.Empty, viewModel.uiState.value.content)
        assertEquals(LibrarySort.TITLE, viewModel.uiState.value.query.sort)
        assertTrue(viewModel.uiState.value.pendingRemovals.isEmpty())

        val failing = FakeLibraryRepository(listOf(movie), writeFailure = AppError.LocalStorageFailure)
        val failingViewModel = LibraryViewModel(failing)
        runCurrent()
        failingViewModel.remove(movie)
        runCurrent()
        assertEquals(AppError.LocalStorageFailure, failingViewModel.uiState.value.actionError)
        assertFalse(failingViewModel.uiState.value.pendingRemovals.contains(movie.mediaRef))
    }

    private fun entry(id: String, type: MediaType, title: String, rating: Int? = null) = LibraryEntry(
        mediaRef = ExternalMediaRef(MediaSource.TMDB, id),
        mediaType = type,
        title = title,
        addedAt = Instant.parse("2026-08-01T10:00:00Z"),
        progress = if (type == MediaType.MOVIE) {
            LibraryProgress.Movie(MovieWatchState.Unwatched)
        } else {
            LibraryProgress.Unavailable
        },
        personalRating = rating?.let(::PersonalRating)
    )
}
