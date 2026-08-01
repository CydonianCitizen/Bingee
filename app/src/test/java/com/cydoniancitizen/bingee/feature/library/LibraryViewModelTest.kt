package com.cydoniancitizen.bingee.feature.library

import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.LibraryEntry
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
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
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun localEntriesLoadAndFilterWithoutCredentialOrMediaRepository() = runTest(mainDispatcherRule.dispatcher) {
        val movie = entry("1", MediaType.MOVIE, "Movie")
        val series = entry("2", MediaType.SERIES, "Series")
        val viewModel = LibraryViewModel(FakeLibraryRepository(listOf(movie, series)))
        runCurrent()

        assertEquals(
            listOf(movie, series),
            (viewModel.uiState.value.content as LibraryContentState.Entries).items
        )

        viewModel.onFilterChanged(LibraryFilter.MOVIES)
        runCurrent()

        assertEquals(
            listOf(movie),
            (viewModel.uiState.value.content as LibraryContentState.Entries).items
        )
    }

    @Test
    fun removeUpdatesVisibleStateAndIsIdempotent() = runTest(mainDispatcherRule.dispatcher) {
        val movie = entry("1", MediaType.MOVIE, "Movie")
        val repository = FakeLibraryRepository(listOf(movie))
        val viewModel = LibraryViewModel(repository)
        runCurrent()

        viewModel.remove(movie)
        runCurrent()

        assertEquals(LibraryContentState.Empty, viewModel.uiState.value.content)
        assertTrue(viewModel.uiState.value.pendingRemovals.isEmpty())
        assertEquals(
            com.cydoniancitizen.bingee.core.result.AppResult.Success(false),
            repository.isInLibrary(movie.mediaRef)
        )
    }

    @Test
    fun removeFailureKeepsEntryAndExposesSafeStructuredError() = runTest(mainDispatcherRule.dispatcher) {
        val movie = entry("1", MediaType.MOVIE, "Movie")
        val repository = FakeLibraryRepository(listOf(movie), writeFailure = AppError.LocalStorageFailure)
        val viewModel = LibraryViewModel(repository)
        runCurrent()

        viewModel.remove(movie)
        runCurrent()

        assertEquals(AppError.LocalStorageFailure, viewModel.uiState.value.actionError)
        assertFalse(viewModel.uiState.value.pendingRemovals.contains(movie.mediaRef))
        assertEquals(
            listOf(movie),
            (viewModel.uiState.value.content as LibraryContentState.Entries).items
        )
    }

    private fun entry(id: String, type: MediaType, title: String) = LibraryEntry(
        mediaRef = ExternalMediaRef(MediaSource.TMDB, id),
        mediaType = type,
        title = title,
        addedAt = Instant.parse("2026-08-01T10:00:00Z")
    )
}
