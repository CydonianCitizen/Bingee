package com.cydoniancitizen.bingee.feature.details

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import com.cydoniancitizen.bingee.core.designsystem.theme.BingeeTheme
import com.cydoniancitizen.bingee.core.model.AnimeDetails
import com.cydoniancitizen.bingee.core.model.AnimeWatchProgress
import com.cydoniancitizen.bingee.core.model.CacheFreshness
import com.cydoniancitizen.bingee.core.model.CachedAnimeDetails
import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.PersonalRating
import com.cydoniancitizen.bingee.core.result.AppError
import com.cydoniancitizen.bingee.debug.FakeAnimeData
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AnimeDetailsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun cachedDetailShowsTitlesMetadataScoreRatingAndAttribution() {
        val details = FakeAnimeData.cachedDetails.details.copy(
            title = "Synthetic Main Title",
            englishTitle = FakeAnimeData.longEnglishTitle
        )
        setDetails(
            state = detailState(
                details = details,
                progress = FakeAnimeData.knownTotalProgress,
                rating = DetailRatingState.Ready(FakeAnimeData.localRating)
            )
        )

        assertVisible("Synthetic Main Title")
        assertVisible(FakeAnimeData.longEnglishTitle)
        assertVisible(FakeAnimeData.longJapaneseTitle)
        assertVisible("TV")
        assertVisible("FINISHED")
        assertVisible("Episodes: 12")
        assertVisible("Started: 2025-01-08")
        assertVisible("Ended: 2025-03-26")
        assertVisible("Duration")
        assertVisible("24 min per ep")
        assertVisible("Synthetic synopsis for offline preview and deterministic test state.")
        assertVisible("MyAnimeList score (via Jikan)")
        assertVisible("8.7 / 10")
        assertVisible("Anime metadata from MyAnimeList via the unofficial Jikan API.")
        assertVisible("8 out of 10")
        composeRule.onNode(
            SemanticsMatcher.expectValue(
                SemanticsProperties.StateDescription,
                "Personal rating, 8 out of 10"
            )
        ).assertIsDisplayed()
        composeRule.onNodeWithText("MyAnimeList score (via Jikan)").assertHasNoClickAction()
        composeRule.onNodeWithContentDescription("No poster available for Synthetic Main Title")
            .performScrollTo().assertIsDisplayed()
    }

    @Test
    fun staleCachedDetailKeepsOfflineContentAndRefreshActionVisible() {
        val refreshed = AtomicBoolean(false)
        var state by mutableStateOf(
            detailState(
                cached = FakeAnimeData.staleDetails,
                progress = FakeAnimeData.unknownTotalProgress
            )
        )
        setDetailsState(state = { state }, onRefresh = { refreshed.set(true) })

        assertVisible("Showing saved details while checking for updates.")
        composeRule.onNodeWithText(FakeAnimeData.staleDetails.details.title)
            .performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Refresh details").performClick()
        assertTrue(refreshed.get())

        composeRule.runOnIdle {
            state = state.copy(refreshError = AppError.NetworkUnavailable)
        }
        assertVisible("Saved details remain available. Refresh failed:", substring = true)
    }

    @Test
    fun missingOptionalValuesUseUnknownLabelsAndPosterPlaceholder() {
        val details = FakeAnimeData.missingOptionalFields
        setDetails(state = detailState(details = details))

        assertVisible(details.title)
        composeRule.onAllNodesWithText("UNKNOWN")[0].assertIsDisplayed()
        composeRule.onAllNodesWithText("UNKNOWN")[1].assertIsDisplayed()
        assertVisible("Anime metadata from MyAnimeList via the unofficial Jikan API.")
        composeRule.onNodeWithContentDescription("No poster available for ${details.title}")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Episodes:", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("Duration").assertDoesNotExist()
        composeRule.onNodeWithText("MyAnimeList score (via Jikan)").assertDoesNotExist()
        composeRule.onNodeWithText("Overview").assertDoesNotExist()
    }

    @Test
    fun loadingAndErrorStatesExposeLiveRegionsAndRetry() {
        val retried = AtomicBoolean(false)
        var state by mutableStateOf(
            AnimeDetailsUiState(content = AnimeDetailContentState.Loading)
        )
        setDetailsState(state = { state }, onRetry = { retried.set(true) })

        composeRule.onNode(
            SemanticsMatcher.expectValue(
                SemanticsProperties.StateDescription,
                "Loading title details"
            )
        ).assertIsDisplayed()
        composeRule.onNode(
            SemanticsMatcher.expectValue(
                SemanticsProperties.LiveRegion,
                LiveRegionMode.Polite
            )
        ).assertIsDisplayed()

        composeRule.runOnIdle {
            state = AnimeDetailsUiState(
                content = AnimeDetailContentState.Error(AppError.RateLimited)
            )
        }
        composeRule.onNodeWithText("Too many requests. Try again later.").assertIsDisplayed()
        composeRule.onNode(
            SemanticsMatcher.expectValue(
                SemanticsProperties.LiveRegion,
                LiveRegionMode.Polite
            )
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Retry").performClick()
        assertTrue(retried.get())
    }

    @Test
    fun knownProgressSupportsIncrementDecrementDirectEditAndCompletion() {
        val incremented = AtomicBoolean(false)
        val decremented = AtomicBoolean(false)
        val completed = AtomicBoolean(false)
        val setCount = AtomicReference<Int>()
        setDetails(
            state = detailState(progress = FakeAnimeData.knownTotalProgress),
            onIncrement = { incremented.set(true) },
            onDecrement = { decremented.set(true) },
            onSetCount = setCount::set,
            onComplete = { completed.set(true) }
        )

        assertVisible("5 of 12 episodes watched")
        composeRule.onNodeWithText("+").performScrollTo().performClick()
        composeRule.onNodeWithText("−").performScrollTo().performClick()
        composeRule.onNode(hasSetTextAction()).performTextReplacement("12")
        composeRule.onNodeWithText("Save").performScrollTo().performClick()
        composeRule.onNodeWithText("Mark completed").performScrollTo().performClick()

        assertTrue(incremented.get())
        assertTrue(decremented.get())
        assertEquals(12, setCount.get())
        assertTrue(completed.get())
    }

    @Test
    fun unknownTotalUsesExplicitCompletionAndBoundsDirectInput() {
        val completed = AtomicBoolean(false)
        val setCount = AtomicReference<Int>()
        setDetails(
            state = detailState(
                details = FakeAnimeData.unknownTotalDetails,
                progress = FakeAnimeData.unknownTotalProgress,
                progressError = AppError.InvalidInput
            ),
            onComplete = { completed.set(true) },
            onSetCount = setCount::set
        )

        assertVisible("3 episodes watched · total unknown")
        assertVisible("Completion for an unknown or ongoing total is set only by the explicit action.")
        assertVisible("Check the entered information and try again.")
        composeRule.onNode(hasSetTextAction()).performTextReplacement("1234567")
        composeRule.onNodeWithText("Save").performScrollTo().performClick()
        composeRule.onNodeWithText("Mark completed").performScrollTo().performClick()

        assertEquals(123456, setCount.get())
        assertTrue(completed.get())
    }

    @Test
    fun inferredAndExplicitCompletionExposeProgressStateAndIncompleteAction() {
        val incomplete = AtomicBoolean(false)
        var state by mutableStateOf(
            detailState(
                progress = AnimeWatchProgress(12, null, null, FakeAnimeData.fixedNow)
            )
        )
        setDetailsState(
            state = { state },
            onIncomplete = {
                incomplete.set(true)
                state = state.copy(progress = FakeAnimeData.completedProgress)
            }
        )

        composeRule.onNode(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Completed")
        ).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Mark incomplete").performScrollTo().performClick()
        assertTrue(incomplete.get())
    }

    @Test
    fun movieWatchedAndUnwatchedActionsAreDistinct() {
        val watched = AtomicBoolean(false)
        val unwatched = AtomicBoolean(false)
        var state by mutableStateOf(detailState(details = FakeAnimeData.movieAnime))
        setDetailsState(
            state = { state },
            onComplete = {
                watched.set(true)
                state = state.copy(progress = FakeAnimeData.movieProgress)
            },
            onIncomplete = {
                unwatched.set(true)
                state = state.copy(progress = null)
            }
        )

        composeRule.onNodeWithText("Mark watched").performScrollTo().performClick()
        composeRule.onNodeWithText("Mark unwatched").performScrollTo().performClick()
        assertTrue(watched.get())
        assertTrue(unwatched.get())
    }

    @Test
    fun zeroProgressDisablesDecrementWithAnAccessibleReason() {
        setDetails(
            state = detailState(
                progress = AnimeWatchProgress(0, null, null, FakeAnimeData.fixedNow)
            )
        )

        composeRule.onNodeWithContentDescription("Decrease watched episode count")
            .assertIsNotEnabled()
        composeRule.onNode(
            SemanticsMatcher.expectValue(
                SemanticsProperties.StateDescription,
                "No watched episodes to decrease"
            )
        ).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun animeHeadingsMembershipAndRelatedButtonsExposeSemantics() {
        setDetails(state = detailState().copy(isInLibrary = true))

        composeRule.onNodeWithText(FakeAnimeData.longEnglishTitle)
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        composeRule.onNodeWithText("Anime progress")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        composeRule.onNodeWithText("Related anime")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        composeRule.onNodeWithText("Remove from library").assertHasClickAction()
        composeRule.onNodeWithText("Sequel · Synthetic Anime Sequel").assertHasClickAction()
    }

    private fun detailState(
        details: AnimeDetails = FakeAnimeData.cachedDetails.details,
        cached: CachedAnimeDetails = CachedAnimeDetails(
            details = details,
            fetchedAt = FakeAnimeData.fixedNow,
            freshness = CacheFreshness.FRESH
        ),
        progress: AnimeWatchProgress? = null,
        progressError: AppError? = null,
        rating: DetailRatingState = DetailRatingState.Ready(null)
    ) = AnimeDetailsUiState(
        content = AnimeDetailContentState.Content(cached),
        isInLibrary = false,
        progress = progress,
        progressError = progressError,
        rating = rating
    )

    private fun setDetails(
        state: AnimeDetailsUiState,
        onRefresh: () -> Unit = {},
        onRetry: () -> Unit = {},
        onIncrement: () -> Unit = {},
        onDecrement: () -> Unit = {},
        onSetCount: (Int) -> Unit = {},
        onComplete: () -> Unit = {},
        onIncomplete: () -> Unit = {},
        onOpenRelated: (com.cydoniancitizen.bingee.core.model.AnimeRelation) -> Unit = {},
        onSelectRating: (Int) -> Unit = {},
        onSaveRating: () -> Unit = {},
        onRemoveRating: () -> Unit = {},
        onDismissRatingError: () -> Unit = {}
    ) = setDetailsState(
        state = { state },
        onRefresh = onRefresh,
        onRetry = onRetry,
        onIncrement = onIncrement,
        onDecrement = onDecrement,
        onSetCount = onSetCount,
        onComplete = onComplete,
        onIncomplete = onIncomplete,
        onOpenRelated = onOpenRelated,
        onSelectRating = onSelectRating,
        onSaveRating = onSaveRating,
        onRemoveRating = onRemoveRating,
        onDismissRatingError = onDismissRatingError
    )

    private fun setDetailsState(
        state: () -> AnimeDetailsUiState,
        onRefresh: () -> Unit = {},
        onRetry: () -> Unit = {},
        onIncrement: () -> Unit = {},
        onDecrement: () -> Unit = {},
        onSetCount: (Int) -> Unit = {},
        onComplete: () -> Unit = {},
        onIncomplete: () -> Unit = {},
        onOpenRelated: (com.cydoniancitizen.bingee.core.model.AnimeRelation) -> Unit = {},
        onSelectRating: (Int) -> Unit = {},
        onSaveRating: () -> Unit = {},
        onRemoveRating: () -> Unit = {},
        onDismissRatingError: () -> Unit = {}
    ) {
        composeRule.setContent {
            BingeeTheme {
                AnimeDetailsContent(
                    state = state(),
                    onBack = {},
                    onRefresh = onRefresh,
                    onRetry = onRetry,
                    onToggleLibrary = {},
                    onIncrement = onIncrement,
                    onDecrement = onDecrement,
                    onSetCount = onSetCount,
                    onComplete = onComplete,
                    onIncomplete = onIncomplete,
                    onOpenRelated = onOpenRelated,
                    onSelectRating = onSelectRating,
                    onSaveRating = onSaveRating,
                    onRemoveRating = onRemoveRating,
                    onDismissRatingError = onDismissRatingError
                )
            }
        }
    }

    private fun assertVisible(value: String, substring: Boolean = false) {
        composeRule.onNodeWithText(value, substring = substring).performScrollTo().assertIsDisplayed()
    }
}
