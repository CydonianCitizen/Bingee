package com.cydoniancitizen.bingee.feature.search

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.cydoniancitizen.bingee.R
import com.cydoniancitizen.bingee.core.designsystem.theme.BingeeTheme
import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.MediaSearchCategory
import com.cydoniancitizen.bingee.core.model.MediaSearchResult
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.core.result.AppError
import com.cydoniancitizen.bingee.testutil.TestLocaleRule
import com.cydoniancitizen.bingee.testutil.localizedTestContext
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SearchScreenTest {
    @get:Rule(order = 0)
    val localeRule = TestLocaleRule()

    private val context get() = localizedTestContext

    @get:Rule(order = 1)
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
        composeRule.onNodeWithContentDescription(context.getString(R.string.search_clear_query)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.search_category_tv)).performClick()

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
                    context.getString(R.string.search_loading)
                )
            ).assertIsDisplayed()

        composeRule.runOnIdle {
            state = SearchUiState(
                query = "none",
                credentialAvailability = SearchCredentialAvailability.AVAILABLE,
                content = SearchContentState.Empty
            )
        }
        composeRule.onNodeWithText(context.getString(R.string.search_empty_title)).assertIsDisplayed()
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
        composeRule.onNodeWithText(context.getString(R.string.action_retry)).performClick()
        assertTrue(retried.get())

        composeRule.runOnIdle {
            state = SearchUiState(
                query = "fixed",
                credentialAvailability = SearchCredentialAvailability.AVAILABLE,
                content = SearchContentState.Error(AppError.Unauthorized)
            )
        }
        composeRule.onNodeWithText(context.getString(R.string.search_open_settings)).performClick()
        assertTrue(opened.get())
    }

    @Test
    fun resultAndMissingPosterFallbackRenderWithoutDetailAction() {
        setSearch(resultsState(NextPageState.End))

        composeRule.onNodeWithText("Fixed Movie").assertIsDisplayed()
        composeRule.onNodeWithText("Original Movie").assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.search_release_year, 2024)).assertIsDisplayed()
        // The result card owns one description; its poster, placeholder included, stays decorative
        // so the title is not announced twice.
        composeRule
            .onNodeWithContentDescription(context.getString(R.string.open_details, "Fixed Movie"))
            .assertContentDescriptionEquals(context.getString(R.string.open_details, "Fixed Movie"))
        composeRule.onNodeWithText(context.getString(R.string.search_end_of_results)).assertIsDisplayed()
    }

    @Test
    fun nextPageLoadingAndRetryRemainBelowExistingResults() {
        val retried = AtomicBoolean(false)
        var state by mutableStateOf(resultsState(NextPageState.Loading))
        setSearchState({ state }, onRetryNextPage = { retried.set(true) })
        composeRule.onNodeWithText("Fixed Movie").assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.search_loading_more)).assertIsDisplayed()

        composeRule.runOnIdle {
            state = resultsState(
                NextPageState.Error(AppError.RemoteServiceFailure, failedPage = 2)
            )
        }
        composeRule.onNodeWithText("Fixed Movie").assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.search_retry_more)).performClick()
        assertTrue(retried.get())
    }

    @Test
    fun loadMoreActionIsAccessible() {
        val loaded = AtomicBoolean(false)
        setSearch(
            state = resultsState(NextPageState.Ready),
            onLoadNextPage = { loaded.set(true) }
        )

        composeRule.onNodeWithText(context.getString(R.string.search_load_more)).performClick()

        assertTrue(loaded.get())
    }

    @Test
    fun libraryActionReflectsObservedMembershipAndUsesExplicitCallback() {
        val toggled = AtomicReference<MediaSearchResult?>(null)
        var state by mutableStateOf(resultsState(NextPageState.End))
        val results = state.content as SearchContentState.Results
        val context = localizedTestContext
        val addLabel = context.getString(R.string.collection_action_add)
        val removeLabel = context.getString(R.string.collection_action_remove)
        setSearchState(state = { state }, onToggleLibrary = toggled::set)

        for (type in listOf(MediaType.MOVIE, MediaType.SERIES)) {
            val item = results.items.single().copy(mediaType = type)
            composeRule.runOnIdle {
                state = state.copy(content = results.copy(items = listOf(item)), libraryMembership = emptySet())
            }
            composeRule.onNodeWithText(addLabel).assertIsEnabled().performClick()
            assertEquals(item, toggled.get())
            composeRule.runOnIdle {
                state = state.copy(libraryMembership = setOf(item.externalRef to item.mediaType))
                toggled.set(null)
            }
            composeRule.onNodeWithText(removeLabel)
                .assertIsDisplayed()
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
                .performClick()
            assertEquals(item, toggled.get())
            composeRule.onNodeWithText(addLabel).assertDoesNotExist()
        }
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

        composeRule.onNodeWithContentDescription(context.getString(R.string.open_details, "Fixed Movie")).performClick()
        assertEquals(ExternalMediaRef(MediaSource.TMDB, "1") to MediaType.MOVIE, opened.get())
        opened.set(null)
        composeRule.onNodeWithText(
            localizedTestContext.getString(R.string.collection_action_add)
        ).performClick()
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
