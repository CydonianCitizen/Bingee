package com.cydoniancitizen.bingee.feature.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.cydoniancitizen.bingee.core.designsystem.theme.BingeeTheme
import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.LibraryEntry
import com.cydoniancitizen.bingee.core.model.LibraryMediaFilter
import com.cydoniancitizen.bingee.core.model.LibraryQuery
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.debug.FakeAnimeData
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AnimeLibraryScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun mixedLibraryFiltersAnimeAndKeepsEqualNumericProviderIdsSeparate() {
        val sourceEntries = FakeAnimeData.mixedLibraryEntries
        val tmdbCollision = sourceEntries.single { it.mediaRef == FakeAnimeData.collisionTmdbRef }
        val jikanCollision = sourceEntries.single {
            it.mediaRef == FakeAnimeData.collisionJikanRef
        }.copy(title = "Jikan Anime Collision")
        val animeMovie = sourceEntries.single { it.title == "Synthetic Anime Movie" }
        val all = listOf(tmdbCollision, jikanCollision, animeMovie)
        val anime = all.filter { it.mediaType == MediaType.ANIME }
        val selected = AtomicReference<LibraryMediaFilter>()
        var state by mutableStateOf(libraryState(all))
        setLibraryState(
            state = { state },
            onMedia = {
                selected.set(it)
                state = libraryState(anime, LibraryQuery(mediaFilter = it))
            }
        )

        composeRule.onNodeWithText(tmdbCollision.title).assertIsDisplayed()
        composeRule.onNodeWithText(jikanCollision.title).assertIsDisplayed()
        composeRule.onNode(
            hasText("Anime") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)
        ).performClick()

        assertEquals(LibraryMediaFilter.ANIME, selected.get())
        composeRule.onNodeWithText("Library")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        composeRule.onNodeWithText(jikanCollision.title).assertIsDisplayed()
        composeRule.onNodeWithText("Synthetic Anime Movie").assertIsDisplayed()
        composeRule.onNodeWithText(tmdbCollision.title).assertDoesNotExist()
        composeRule.onNode(
            hasText("Anime") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)
        ).assertIsDisplayed()
        composeRule.onNodeWithText("5 of 12 episodes watched").assertIsDisplayed()
        composeRule.onNodeWithText("Personal rating: 8 out of 10").assertIsDisplayed()
    }

    @Test
    fun removingAndReaddingAnimeKeepsProgressAndRatingVisible() {
        val entry = FakeAnimeData.mixedLibraryEntries.first { it.mediaType == MediaType.ANIME }
        val removed = AtomicReference<LibraryEntry>()
        var state by mutableStateOf(libraryState(listOf(entry)))
        setLibraryState(
            state = { state },
            onRemove = {
                removed.set(it)
                state = libraryState(emptyList(), totalCount = 0)
            }
        )

        composeRule.onNodeWithText("Remove from library").performScrollTo().performClick()
        assertEquals(entry, removed.get())
        composeRule.onNodeWithText("Your library is empty").assertIsDisplayed()

        composeRule.runOnIdle {
            state = libraryState(listOf(entry))
        }
        composeRule.onNodeWithText("5 of 12 episodes watched").assertIsDisplayed()
        composeRule.onNodeWithText("Personal rating: 8 out of 10").assertIsDisplayed()
        composeRule.onNode(
            hasText("Anime") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)
        ).assertIsDisplayed()
    }

    @Test
    fun animeRowRoutesWithJikanReferenceAndMembershipActionIsSeparate() {
        val entry = FakeAnimeData.mixedLibraryEntries.first { it.mediaType == MediaType.ANIME }
        val opened = AtomicReference<Pair<ExternalMediaRef, MediaType>>()
        val removed = AtomicReference<ExternalMediaRef>()
        setLibraryState(
            state = { libraryState(listOf(entry)) },
            onRemove = { removed.set(it.mediaRef) },
            onOpenDetails = { ref, type -> opened.set(ref to type) }
        )

        composeRule.onNodeWithContentDescription("Open details for ${entry.title}")
            .performClick()
        composeRule.onNodeWithText("Remove from library").performClick()

        assertEquals(entry.mediaRef to MediaType.ANIME, opened.get())
        assertTrue(removed.get() == entry.mediaRef)
        assertEquals(MediaSource.JIKAN, entry.mediaRef.source)
    }

    private fun libraryState(
        entries: List<LibraryEntry>,
        query: LibraryQuery = LibraryQuery(),
        totalCount: Int = entries.size
    ) = LibraryUiState(
        query = query,
        availableMediaFilters = LibraryMediaFilter.entries,
        content = if (entries.isEmpty()) {
            LibraryContentState.Empty
        } else {
            LibraryContentState.Entries(entries)
        },
        resultCount = entries.size,
        totalEntryCount = totalCount
    )

    private fun setLibraryState(
        state: () -> LibraryUiState,
        onMedia: (LibraryMediaFilter) -> Unit = {},
        onRemove: (LibraryEntry) -> Unit = {},
        onOpenDetails: (ExternalMediaRef, MediaType) -> Unit = { _, _ -> }
    ) {
        composeRule.setContent {
            BingeeTheme {
                LibraryContent(
                    state = state(),
                    onSearchQueryChanged = {},
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
}
