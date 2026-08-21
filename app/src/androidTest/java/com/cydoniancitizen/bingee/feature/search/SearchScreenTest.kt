package com.cydoniancitizen.bingee.feature.search

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.cydoniancitizen.bingee.core.designsystem.theme.BingeeTheme
import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.MediaSearchCategory
import com.cydoniancitizen.bingee.core.model.MediaSearchResult
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.core.result.AppError
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SearchScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun queryInputClearAndCategoryActionsAreExplicit() {
        val query = AtomicReference("")
        val cleared = AtomicBoolean(false)
        val category = AtomicReference(MediaSearchCategory.MOVIES)
        var state by mutableStateOf(
            SearchUiState(
                query = "Alien",
                credentialAvailability = SearchCredentialAvailability.AVAILABLE
            )
        )
        setSearchState(
            state = { state },
            onQueryChanged = { value ->
                query.set(value)
                state = state.copy(query = value)
            },
            onClearQuery = {
                cleared.set(true)
                state = state.copy(query = "")
            },
            onCategoryChanged = category::set
        )

        composeRule.onNode(hasSetTextAction()).performTextReplacement("Aliens")
        composeRule.onNodeWithContentDescription("Clear search query").performClick()
        composeRule.onNodeWithText("TV Series").performClick()

        assertEquals("Aliens", query.get())
        assertTrue(cleared.get())
        assertEquals(MediaSearchCategory.TV_SERIES, category.get())
    }

    @Test
    fun loadingAndEmptyStatesAreAccessible() {
        var state by mutableStateOf(
            SearchUiState(
                query = "fixed",
                credentialAvailability = SearchCredentialAvailability.AVAILABLE,
                content = SearchContentState.Loading
            )
        )
        setSearchState({ state })
        composeRule
            .onNode(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    "Searching TMDB"
                )
            ).assertIsDisplayed()

        composeRule.runOnIdle {
            state = SearchUiState(
                query = "none",
                credentialAvailability = SearchCredentialAvailability.AVAILABLE,
                content = SearchContentState.Empty
            )
        }
        composeRule.onNodeWithText("No results").assertIsDisplayed()
    }

    @Test
    fun initialErrorRetryAndUnauthorizedSettingsActionsWork() {
        val retried = AtomicBoolean(false)
        val opened = AtomicBoolean(false)
        var state by mutableStateOf(
            SearchUiState(
                query = "fixed",
                credentialAvailability = SearchCredentialAvailability.AVAILABLE,
                content = SearchContentState.Error(AppError.NetworkUnavailable)
            )
        )
        setSearchState(
            state = { state },
            onRetryInitial = { retried.set(true) },
            onOpenSettings = { opened.set(true) }
        )
        composeRule.onNodeWithText("Retry").performClick()
        assertTrue(retried.get())

        composeRule.runOnIdle {
            state = SearchUiState(
                query = "fixed",
                credentialAvailability = SearchCredentialAvailability.AVAILABLE,
                content = SearchContentState.Error(AppError.Unauthorized)
            )
        }
        composeRule.onNodeWithText("Open Settings").performClick()
        assertTrue(opened.get())
    }

    @Test
    fun resultAndMissingPosterFallbackRenderWithoutDetailAction() {
        setSearch(resultsState(NextPageState.End))

        composeRule.onNodeWithText("Fixed Movie").assertIsDisplayed()
        composeRule.onNodeWithText("Original Movie").assertIsDisplayed()
        composeRule.onNodeWithText("Year: 2024").assertIsDisplayed()
        // The result card owns one description; its poster, placeholder included, stays decorative
        // so the title is not announced twice.
        composeRule
            .onNodeWithContentDescription("Open details for Fixed Movie")
            .assertContentDescriptionEquals("Open details for Fixed Movie")
        composeRule.onNodeWithText("End of results").assertIsDisplayed()
    }

    @Test
    fun nextPageLoadingAndRetryRemainBelowExistingResults() {
        val retried = AtomicBoolean(false)
        var state by mutableStateOf(resultsState(NextPageState.Loading))
        setSearchState({ state }, onRetryNextPage = { retried.set(true) })
        composeRule.onNodeWithText("Fixed Movie").assertIsDisplayed()
        composeRule.onNodeWithText("Loading more results").assertIsDisplayed()

        composeRule.runOnIdle {
            state = resultsState(
                NextPageState.Error(AppError.RemoteServiceFailure, failedPage = 2)
            )
        }
        composeRule.onNodeWithText("Fixed Movie").assertIsDisplayed()
        composeRule.onNodeWithText("Retry loading more").performClick()
        assertTrue(retried.get())
    }

    @Test
    fun loadMoreActionIsAccessible() {
        val loaded = AtomicBoolean(false)
        setSearch(
            state = resultsState(NextPageState.Ready),
            onLoadNextPage = { loaded.set(true) }
        )

        composeRule.onNodeWithText("Load more results").performClick()

        assertTrue(loaded.get())
    }

    @Test
    fun libraryActionReflectsObservedMembershipAndUsesExplicitCallback() {
        val toggled = AtomicReference<MediaSearchResult?>(null)
        var state by mutableStateOf(resultsState(NextPageState.End))
        val item = (state.content as SearchContentState.Results).items.single()
        setSearchState(state = { state }, onToggleLibrary = toggled::set)

        composeRule.onNodeWithText("Add to Watch Later").performClick()

        assertEquals(item, toggled.get())

        composeRule.runOnIdle {
            state = state.copy(libraryMembership = setOf(item.externalRef))
        }
        composeRule.onNodeWithText("In Watch Later").assertIsDisplayed()
    }

    @Test
    fun rowOpensDetailsButLibraryActionDoesNotNavigate() {
        val opened = AtomicReference<Pair<ExternalMediaRef, MediaType>?>(null)
        val toggled = AtomicBoolean(false)
        setSearch(
            state = resultsState(NextPageState.End),
            onToggleLibrary = { toggled.set(true) },
            onOpenDetails = { ref, type -> opened.set(ref to type) }
        )

        composeRule.onNodeWithContentDescription("Open details for Fixed Movie").performClick()
        assertEquals(ExternalMediaRef(MediaSource.TMDB, "1") to MediaType.MOVIE, opened.get())
        opened.set(null)
        composeRule.onNodeWithText("Add to Watch Later").performClick()
        assertTrue(toggled.get())
        assertEquals(null, opened.get())
    }

    private fun resultsState(nextPage: NextPageState) = SearchUiState(
        query = "fixed",
        credentialAvailability = SearchCredentialAvailability.AVAILABLE,
        content =
        SearchContentState.Results(
            items =
            listOf(
                MediaSearchResult(
                    externalRef = ExternalMediaRef(MediaSource.TMDB, "1"),
                    mediaType = MediaType.MOVIE,
                    title = "Fixed Movie",
                    originalTitle = "Original Movie",
                    posterUrl = null,
                    releaseDate = LocalDate.of(2024, 1, 2),
                    overview = "Readable overview."
                )
            ),
            currentPage = 1,
            totalPages = 2,
            nextPage = nextPage
        )
    )

    private fun setSearch(
        state: SearchUiState,
        onQueryChanged: (String) -> Unit = {},
        onClearQuery: () -> Unit = {},
        onCategoryChanged: (MediaSearchCategory) -> Unit = {},
        onRetryInitial: () -> Unit = {},
        onLoadNextPage: () -> Unit = {},
        onRetryNextPage: () -> Unit = {},
        onToggleLibrary: (MediaSearchResult) -> Unit = {},
        onOpenDetails: (ExternalMediaRef, MediaType) -> Unit = { _, _ -> },
        onOpenSettings: () -> Unit = {}
    ) = setSearchState(
        { state },
        onQueryChanged,
        onClearQuery,
        onCategoryChanged,
        onRetryInitial,
        onLoadNextPage,
        onRetryNextPage,
        onToggleLibrary,
        onOpenDetails,
        onOpenSettings
    )

    private fun setSearchState(
        state: () -> SearchUiState,
        onQueryChanged: (String) -> Unit = {},
        onClearQuery: () -> Unit = {},
        onCategoryChanged: (MediaSearchCategory) -> Unit = {},
        onRetryInitial: () -> Unit = {},
        onLoadNextPage: () -> Unit = {},
        onRetryNextPage: () -> Unit = {},
        onToggleLibrary: (MediaSearchResult) -> Unit = {},
        onOpenDetails: (ExternalMediaRef, MediaType) -> Unit = { _, _ -> },
        onOpenSettings: () -> Unit = {}
    ) {
        composeRule.setContent {
            BingeeTheme {
                SearchContent(
                    state = state(),
                    onQueryChanged = onQueryChanged,
                    onClearQuery = onClearQuery,
                    onCategoryChanged = onCategoryChanged,
                    onRetryInitial = onRetryInitial,
                    onLoadNextPage = onLoadNextPage,
                    onRetryNextPage = onRetryNextPage,
                    onToggleLibrary = onToggleLibrary,
                    onOpenDetails = onOpenDetails,
                    onOpenSettings = onOpenSettings
                )
            }
        }
    }
}
