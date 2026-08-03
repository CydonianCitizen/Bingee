package com.cydoniancitizen.bingee.feature.details

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.cydoniancitizen.bingee.core.designsystem.theme.BingeeTheme
import com.cydoniancitizen.bingee.core.model.CacheFreshness
import com.cydoniancitizen.bingee.core.model.CachedMediaDetails
import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.Genre
import com.cydoniancitizen.bingee.core.model.MediaDetails
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.core.model.ProductionStatus
import com.cydoniancitizen.bingee.core.result.AppError
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MediaDetailsScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun movieDetailsRenderWithoutTrackingOrRatingControls() {
        setDetails(content(movie()))

        composeRule.onNodeWithText("Movie title").assertIsDisplayed()
        composeRule.onNodeWithText("Released").assertIsDisplayed()
        composeRule.onNodeWithText("120 min").assertIsDisplayed()
        composeRule.onNodeWithText("Drama, Thriller").assertIsDisplayed()
        composeRule.onNodeWithText("Rating").assertDoesNotExist()
        composeRule.onNodeWithText("Mark watched").assertDoesNotExist()
    }

    @Test
    fun tvDetailsShowOnlyHighLevelCounts() {
        setDetails(
            content(
                movie().copy(
                    mediaType = MediaType.SERIES,
                    runtime = null,
                    episodeRuntime = Duration.ofMinutes(50),
                    numberOfSeasons = 3,
                    numberOfEpisodes = 24,
                    productionStatus = ProductionStatus.RETURNING_SERIES
                )
            )
        )

        composeRule.onNodeWithText("TV Series").assertIsDisplayed()
        composeRule.onNodeWithText("Typical episode runtime").assertIsDisplayed()
        composeRule.onNodeWithText("Number of seasons").assertIsDisplayed()
        composeRule.onNodeWithText("24").assertIsDisplayed()
    }

    @Test
    fun loadingFullErrorRetryAndUnauthorizedSettingsAreActionable() {
        setDetails(MediaDetailsUiState(content = DetailContentState.Loading))
        composeRule.onNodeWithText("Loading title details").assertIsDisplayed()

        val retried = AtomicBoolean(false)
        setDetails(
            MediaDetailsUiState(content = DetailContentState.Error(AppError.NetworkUnavailable)),
            onRetry = { retried.set(true) }
        )
        composeRule.onNodeWithText("Retry").performClick()
        assertTrue(retried.get())

        val settings = AtomicBoolean(false)
        setDetails(
            MediaDetailsUiState(content = DetailContentState.Error(AppError.Unauthorized)),
            onOpenSettings = { settings.set(true) }
        )
        composeRule.onNodeWithText("Open Settings").performClick()
        assertTrue(settings.get())
    }

    @Test
    fun staleRefreshErrorAndRefreshIndicatorKeepCachedContentVisible() {
        setDetails(
            content(movie()).copy(refresh = DetailRefreshState.Error(AppError.NetworkUnavailable))
        )
        composeRule.onNodeWithText("Movie title").assertIsDisplayed()
        composeRule.onNodeWithText("Saved details remain available.", substring = true).assertIsDisplayed()

        setDetails(content(movie()).copy(refresh = DetailRefreshState.Refreshing))
        composeRule.onNodeWithText("Movie title").assertIsDisplayed()
    }

    @Test
    fun libraryActionAndMissingImageSemanticsAreAccessible() {
        val toggled = AtomicBoolean(false)
        setDetails(content(movie().copy(posterUrl = null, backdropUrl = null)), onToggle = { toggled.set(true) })

        composeRule.onNodeWithText("Add to library").performClick()
        assertTrue(toggled.get())
        composeRule.onNodeWithContentDescription("No poster available for Movie title").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("No backdrop available for Movie title").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Back").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Refresh details").assertIsDisplayed()
    }

    private fun setDetails(
        state: MediaDetailsUiState,
        onRetry: () -> Unit = {},
        onToggle: () -> Unit = {},
        onOpenSettings: () -> Unit = {}
    ) {
        composeRule.setContent {
            BingeeTheme {
                MediaDetailsContent(
                    state = state,
                    onBack = {},
                    onRefresh = {},
                    onRetry = onRetry,
                    onToggleLibrary = onToggle,
                    onDismissLibraryError = {},
                    onOpenSettings = onOpenSettings
                )
            }
        }
    }

    private fun content(details: MediaDetails) = MediaDetailsUiState(
        content = DetailContentState.Content(
            CachedMediaDetails(details, Instant.parse("2026-08-01T10:00:00Z"), CacheFreshness.STALE)
        ),
        isInLibrary = false
    )

    private fun movie() = MediaDetails(
        externalRef = ExternalMediaRef(MediaSource.TMDB, "550"),
        mediaType = MediaType.MOVIE,
        title = "Movie title",
        overview = "Overview",
        runtime = Duration.ofMinutes(120),
        productionStatus = ProductionStatus.RELEASED,
        genres = listOf(Genre("Drama"), Genre("Thriller"))
    )
}
