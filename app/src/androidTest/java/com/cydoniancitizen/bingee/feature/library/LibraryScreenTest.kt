package com.cydoniancitizen.bingee.feature.library

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.cydoniancitizen.bingee.core.designsystem.theme.BingeeTheme
import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.LibraryEntry
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class LibraryScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun emptyLibraryExplainsOfflineLocalWorkflow() {
        setLibrary(LibraryUiState(content = LibraryContentState.Empty))

        composeRule.onNodeWithText("Your library is empty").assertIsDisplayed()
        composeRule
            .onNodeWithText("Add movies or TV series from Search. Saved items remain available offline.")
            .assertIsDisplayed()
    }

    @Test
    fun filtersAndRemoveAreExplicitActionsWithoutDetailNavigation() {
        val selectedFilter = AtomicReference<LibraryFilter?>(null)
        val removed = AtomicReference<LibraryEntry?>(null)
        val entry = entry()
        setLibrary(
            state = LibraryUiState(content = LibraryContentState.Entries(listOf(entry))),
            onFilterChanged = selectedFilter::set,
            onRemove = removed::set
        )

        composeRule.onNodeWithText("Arrival").assertIsDisplayed()
        composeRule.onNodeWithText("Movie").assertIsDisplayed()
        composeRule.onNodeWithText("Movies").performClick()
        composeRule.onNodeWithText("Remove from library").performClick()

        assertEquals(LibraryFilter.MOVIES, selectedFilter.get())
        assertEquals(entry, removed.get())
    }

    private fun setLibrary(
        state: LibraryUiState,
        onFilterChanged: (LibraryFilter) -> Unit = {},
        onRemove: (LibraryEntry) -> Unit = {}
    ) {
        composeRule.setContent {
            BingeeTheme {
                LibraryContent(
                    state = state,
                    onFilterChanged = onFilterChanged,
                    onRetry = {},
                    onRemove = onRemove,
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
        addedAt = Instant.parse("2026-08-01T10:00:00Z")
    )
}
