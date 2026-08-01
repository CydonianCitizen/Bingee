package com.cydoniancitizen.bingee.feature.search

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
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
        setSearch(
            state =
            SearchUiState(
                query = "Alien",
                credentialAvailability = SearchCredentialAvailability.AVAILABLE
            ),
            onQueryChanged = query::set,
            onClearQuery = { cleared.set(true) },
            onCategoryChanged = category::set
        )

        composeRule.onNode(hasSetTextAction()).performTextInput("s")
        composeRule.onNodeWithContentDescription("Clear search query").performClick()
        composeRule.onNodeWithText("TV Series").performClick()

        assertEquals("Aliens", query.get())
        assertTrue(cleared.get())
        assertEquals(MediaSearchCategory.TV_SERIES, category.get())
    }

    @Test
    fun loadingAndEmptyStatesAreAccessible() {
        setSearch(
            SearchUiState(
                query = "fixed",
                credentialAvailability = SearchCredentialAvailability.AVAILABLE,
                content = SearchContentState.Loading
            )
        )
        composeRule
            .onNode(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    "Searching TMDB"
                )
            ).assertIsDisplayed()

        setSearch(
            SearchUiState(
                query = "none",
                credentialAvailability = SearchCredentialAvailability.AVAILABLE,
                content = SearchContentState.Empty
            )
        )
        composeRule.onNodeWithText("No results").assertIsDisplayed()
    }

    @Test
    fun initialErrorRetryAndUnauthorizedSettingsActionsWork() {
        val retried = AtomicBoolean(false)
        setSearch(
            state =
            SearchUiState(
                query = "fixed",
                credentialAvailability = SearchCredentialAvailability.AVAILABLE,
                content = SearchContentState.Error(AppError.NetworkUnavailable)
            ),
            onRetryInitial = { retried.set(true) }
        )
        composeRule.onNodeWithText("Retry").performClick()
        assertTrue(retried.get())

        val opened = AtomicBoolean(false)
        setSearch(
            state =
            SearchUiState(
                query = "fixed",
                credentialAvailability = SearchCredentialAvailability.AVAILABLE,
                content = SearchContentState.Error(AppError.Unauthorized)
            ),
            onOpenSettings = { opened.set(true) }
        )
        composeRule.onNodeWithText("Open Settings").performClick()
        assertTrue(opened.get())
    }

    @Test
    fun resultAndMissingPosterFallbackRenderWithoutDetailAction() {
        setSearch(resultsState(NextPageState.End))

        composeRule.onNodeWithText("Fixed Movie").assertIsDisplayed()
        composeRule.onNodeWithText("Original Movie").assertIsDisplayed()
        composeRule.onNodeWithText("Year: 2024").assertIsDisplayed()
        composeRule
            .onNodeWithContentDescription("No poster available for Fixed Movie")
            .assertIsDisplayed()
        composeRule.onNodeWithText("End of results").assertIsDisplayed()
    }

    @Test
    fun nextPageLoadingAndRetryRemainBelowExistingResults() {
        setSearch(resultsState(NextPageState.Loading))
        composeRule.onNodeWithText("Fixed Movie").assertIsDisplayed()
        composeRule.onNodeWithText("Loading more results").assertIsDisplayed()

        val retried = AtomicBoolean(false)
        setSearch(
            state =
            resultsState(
                NextPageState.Error(AppError.RemoteServiceFailure, failedPage = 2)
            ),
            onRetryNextPage = { retried.set(true) }
        )
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
        onOpenSettings: () -> Unit = {}
    ) {
        composeRule.setContent {
            BingeeTheme {
                SearchContent(
                    state = state,
                    onQueryChanged = onQueryChanged,
                    onClearQuery = onClearQuery,
                    onCategoryChanged = onCategoryChanged,
                    onRetryInitial = onRetryInitial,
                    onLoadNextPage = onLoadNextPage,
                    onRetryNextPage = onRetryNextPage,
                    onOpenSettings = onOpenSettings
                )
            }
        }
    }
}
