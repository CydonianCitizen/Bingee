package com.cydoniancitizen.bingee.feature.search

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
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
import com.cydoniancitizen.bingee.debug.FakeAnimeData
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AnimeSearchScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun animeCategoryAcceptsQueryWithoutTmdbCredentialRequiredState() {
        val selectedCategory = AtomicReference<MediaSearchCategory>()
        val query = AtomicReference<String>()
        var state by mutableStateOf(
            SearchUiState(
                query = "",
                category = MediaSearchCategory.ANIME,
                credentialAvailability = SearchCredentialAvailability.REQUIRED
            )
        )
        setSearchState(
            state = { state },
            onQueryChanged = {
                query.set(it)
                state = state.copy(query = it)
            },
            onCategoryChanged = {
                selectedCategory.set(it)
                state = state.copy(category = it)
            }
        )

        composeRule.onNodeWithText("Anime").performClick()
        composeRule.onNode(hasSetTextAction()).performTextReplacement("Synthetic Anime")

        assertEquals(MediaSearchCategory.ANIME, selectedCategory.get())
        assertEquals("Synthetic Anime", query.get())
        composeRule.onNodeWithText("Search TMDB")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        composeRule.onNodeWithText("TMDB configuration required").assertDoesNotExist()
        composeRule.onNodeWithText("Remote search needs a valid TMDB credential.").assertDoesNotExist()
    }

    @Test
    fun animeLoadingEmptyAndRateLimitRetryStatesAreVisible() {
        val retried = AtomicReference(false)
        var state by mutableStateOf(
            SearchUiState(
                query = "Synthetic Anime",
                category = MediaSearchCategory.ANIME,
                credentialAvailability = SearchCredentialAvailability.REQUIRED,
                content = SearchContentState.Loading
            )
        )
        setSearchState({ state }, onRetryInitial = { retried.set(true) })

        composeRule.onNode(
            SemanticsMatcher.expectValue(
                SemanticsProperties.StateDescription,
                "Searching TMDB"
            )
        ).assertIsDisplayed()

        composeRule.runOnIdle {
            state = state.copy(content = SearchContentState.Empty)
        }
        composeRule.onNodeWithText("No results").assertIsDisplayed()

        composeRule.runOnIdle {
            state = state.copy(content = SearchContentState.Error(AppError.RateLimited))
        }
        composeRule.onNodeWithText("Too many requests. Try again later.").assertIsDisplayed()
        composeRule.onNodeWithText("Retry").performClick()
        assertTrue(retried.get())
    }

    @Test
    fun animeResultShowsProviderAttributionAndNavigatesWithJikanReference() {
        val opened = AtomicReference<Pair<ExternalMediaRef, MediaType>>()
        val item = FakeAnimeData.searchResult
        setSearchState(
            state = {
                SearchUiState(
                    query = "Synthetic Anime",
                    category = MediaSearchCategory.ANIME,
                    credentialAvailability = SearchCredentialAvailability.REQUIRED,
                    content = SearchContentState.Results(
                        items = listOf(item),
                        currentPage = 1,
                        totalPages = 1,
                        nextPage = NextPageState.End
                    )
                )
            },
            onOpenDetails = { ref, type -> opened.set(ref to type) }
        )

        composeRule.onNodeWithText(item.title).assertIsDisplayed()
        composeRule.onNodeWithText(item.originalTitle!!).assertIsDisplayed()
        composeRule.onNodeWithText("Anime data: Jikan / MyAnimeList").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Open details for ${item.title}").performClick()

        assertEquals(item.externalRef to MediaType.ANIME, opened.get())
    }

    @Test
    fun moviesAndTvSeriesStillRenderTheirExistingSearchResults() {
        val movie = MediaSearchResult(
            externalRef = ExternalMediaRef(MediaSource.TMDB, "550"),
            mediaType = MediaType.MOVIE,
            title = "Synthetic Movie"
        )
        val series = movie.copy(
            externalRef = ExternalMediaRef(MediaSource.TMDB, "1399"),
            mediaType = MediaType.SERIES,
            title = "Synthetic TV Series"
        )
        var state by mutableStateOf(
            SearchUiState(
                query = "Synthetic",
                category = MediaSearchCategory.MOVIES,
                credentialAvailability = SearchCredentialAvailability.AVAILABLE,
                content = results(movie)
            )
        )
        setSearchState({ state })

        composeRule.onNodeWithText("Synthetic Movie").assertIsDisplayed()
        composeRule.runOnIdle {
            state = state.copy(category = MediaSearchCategory.TV_SERIES, content = results(series))
        }
        composeRule.onNodeWithText("Synthetic TV Series").assertIsDisplayed()
        composeRule.onNodeWithText("Anime data: Jikan / MyAnimeList").assertDoesNotExist()
    }

    @Test
    fun staleSearchResultsDisappearWhenNewStateArrives() {
        val old = FakeAnimeData.searchResult.copy(title = "Old Synthetic Anime")
        val current = FakeAnimeData.secondSearchResult.copy(title = "Current Synthetic Anime")
        var state by mutableStateOf(
            SearchUiState(
                query = "Synthetic",
                category = MediaSearchCategory.ANIME,
                credentialAvailability = SearchCredentialAvailability.REQUIRED,
                content = results(old)
            )
        )
        setSearchState({ state })
        composeRule.onNodeWithText("Old Synthetic Anime").assertIsDisplayed()

        composeRule.runOnIdle {
            state = state.copy(content = results(current))
        }
        composeRule.onNodeWithText("Current Synthetic Anime").assertIsDisplayed()
        composeRule.onNodeWithText("Old Synthetic Anime").assertDoesNotExist()
    }

    private fun results(item: MediaSearchResult) = SearchContentState.Results(
        items = listOf(item),
        currentPage = 1,
        totalPages = 1,
        nextPage = NextPageState.End
    )

    private fun setSearchState(
        state: () -> SearchUiState,
        onQueryChanged: (String) -> Unit = {},
        onCategoryChanged: (MediaSearchCategory) -> Unit = {},
        onRetryInitial: () -> Unit = {},
        onOpenDetails: (ExternalMediaRef, MediaType) -> Unit = { _, _ -> }
    ) {
        composeRule.setContent {
            BingeeTheme {
                SearchContent(
                    state = state(),
                    onQueryChanged = onQueryChanged,
                    onClearQuery = {},
                    onCategoryChanged = onCategoryChanged,
                    onRetryInitial = onRetryInitial,
                    onLoadNextPage = {},
                    onRetryNextPage = {},
                    onOpenDetails = onOpenDetails,
                    onOpenSettings = {}
                )
            }
        }
    }
}
