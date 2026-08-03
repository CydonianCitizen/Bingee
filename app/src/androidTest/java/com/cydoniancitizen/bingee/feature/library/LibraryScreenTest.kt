package com.cydoniancitizen.bingee.feature.library

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.cydoniancitizen.bingee.core.designsystem.theme.BingeeTheme
import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.LibraryEntry
import com.cydoniancitizen.bingee.core.model.LibraryMediaFilter
import com.cydoniancitizen.bingee.core.model.LibraryProgress
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.core.model.MovieWatchState
import com.cydoniancitizen.bingee.core.model.PersonalRating
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class LibraryScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun emptyAndNoMatchingStatesAreDistinct() {
        setLibrary(LibraryUiState(content = LibraryContentState.Empty))
        composeRule.onNodeWithText("Your library is empty").assertIsDisplayed()

        setLibrary(LibraryUiState(content = LibraryContentState.NoResults, totalEntryCount = 1))
        composeRule.onNodeWithText("No matching titles").assertIsDisplayed()
    }

    @Test
    fun searchMediaFilterRatingAndRemoveAreAccessible() {
        val search = AtomicReference("")
        val media = AtomicReference<LibraryMediaFilter>()
        val removed = AtomicReference<LibraryEntry>()
        val entry = entry()
        setLibrary(
            state = LibraryUiState(
                content = LibraryContentState.Entries(listOf(entry)),
                resultCount = 1,
                totalEntryCount = 1
            ),
            onSearch = search::set,
            onMedia = media::set,
            onRemove = removed::set
        )

        composeRule.onNode(hasSetTextAction()).performTextInput("arrival")
        composeRule.onNodeWithText("Movies").performClick()
        composeRule.onNodeWithText("Personal rating: 10 out of 10").assertIsDisplayed()
        composeRule.onNodeWithText("Remove from library").performClick()

        assertEquals("arrival", search.get())
        assertEquals(LibraryMediaFilter.MOVIES, media.get())
        assertEquals(entry, removed.get())
    }

    @Test
    fun rowOpensDetailsButRemoveDoesNotNavigate() {
        val opened = AtomicBoolean(false)
        val removed = AtomicBoolean(false)
        setLibrary(
            state = LibraryUiState(
                content = LibraryContentState.Entries(listOf(entry())),
                resultCount = 1,
                totalEntryCount = 1
            ),
            onRemove = { removed.set(true) },
            onOpenDetails = { _, _ -> opened.set(true) }
        )

        composeRule.onNodeWithContentDescription("Open details for Arrival").performClick()
        assertEquals(true, opened.get())
        opened.set(false)
        composeRule.onNodeWithText("Remove from library").performClick()
        assertEquals(true, removed.get())
        assertEquals(false, opened.get())
    }

    private fun setLibrary(
        state: LibraryUiState,
        onSearch: (String) -> Unit = {},
        onMedia: (LibraryMediaFilter) -> Unit = {},
        onRemove: (LibraryEntry) -> Unit = {},
        onOpenDetails: (ExternalMediaRef, MediaType) -> Unit = { _, _ -> }
    ) {
        composeRule.setContent {
            BingeeTheme {
                LibraryContent(
                    state = state,
                    onSearchQueryChanged = onSearch,
                    onClearSearch = {},
                    onMediaFilterChanged = onMedia,
                    onStateFilterChanged = {},
                    onSortChanged = {},
                    onRetry = {},
                    onRemove = onRemove,
                    onOpenDetails = onOpenDetails,
                    onDismissActionError = {}
                )
            }
        }
    }

    private fun entry() = LibraryEntry(
        mediaRef = ExternalMediaRef(MediaSource.TMDB, "42"),
        mediaType = MediaType.MOVIE,
        title = "Arrival",
        overview = "First contact.",
        addedAt = Instant.parse("2026-08-01T10:00:00Z"),
        progress = LibraryProgress.Movie(MovieWatchState.Unwatched),
        personalRating = PersonalRating(10)
    )
}
