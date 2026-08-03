package com.cydoniancitizen.bingee.feature.details

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.cydoniancitizen.bingee.core.designsystem.theme.BingeeTheme
import com.cydoniancitizen.bingee.core.model.CacheFreshness
import com.cydoniancitizen.bingee.core.model.CachedMediaDetails
import com.cydoniancitizen.bingee.core.model.CachedSeason
import com.cydoniancitizen.bingee.core.model.Episode
import com.cydoniancitizen.bingee.core.model.EpisodeWatchState
import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.Genre
import com.cydoniancitizen.bingee.core.model.MediaDetails
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.core.model.MovieWatchState
import com.cydoniancitizen.bingee.core.model.PersonalRating
import com.cydoniancitizen.bingee.core.model.ProductionStatus
import com.cydoniancitizen.bingee.core.model.Season
import com.cydoniancitizen.bingee.core.model.SeasonProgress
import com.cydoniancitizen.bingee.core.model.SeriesProgress
import com.cydoniancitizen.bingee.core.model.TrackedEpisode
import com.cydoniancitizen.bingee.core.result.AppError
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MediaDetailsScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun movieDetailsRenderWatchedActionAndTitleRatingControls() {
        val toggled = AtomicBoolean(false)
        setDetails(
            content(movie()).copy(
                movieProgress = MovieProgressState.Ready(MovieWatchState.Unwatched)
            ),
            onToggleMovie = { toggled.set(true) }
        )

        composeRule.onNodeWithText("Movie title").assertIsDisplayed()
        composeRule.onNodeWithText("Released").assertIsDisplayed()
        composeRule.onNodeWithText("120 min").assertIsDisplayed()
        composeRule.onNodeWithText("Drama, Thriller").assertIsDisplayed()
        composeRule.onNodeWithText("Personal rating").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Mark watched").performScrollTo().performClick()
        assertTrue(toggled.get())
    }

    @Test
    fun tvDetailsShowSeasonsSpecialsEpisodesAndDisableFutureEpisode() {
        val regular = season(1, "Season 1", expanded = true)
        val specials = season(0, "Specials", expanded = false)
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
            ).copy(
                series = SeriesDetailUiState(
                    content = SeriesContentState.Ready(
                        listOf(specials, regular),
                        SeriesProgress(1, 2, 0, 1, false)
                    ),
                    expandedSeasons = setOf(regular.season.externalRef)
                )
            )
        )

        composeRule.onNodeWithText("TV Series").assertIsDisplayed()
        composeRule.onNodeWithText("Season 1").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Specials").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Episode 1 · Watched episode").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Episode 3 · Future episode").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Not aired yet").assertIsNotEnabled()
        composeRule.onNodeWithText("Rate episode").assertDoesNotExist()
        composeRule.onNodeWithText("Rate season").assertDoesNotExist()
    }

    @Test
    fun ratingControlShowsCurrentValueAndExposesSaveAndRemoveActions() {
        val saved = AtomicBoolean(false)
        val removed = AtomicBoolean(false)
        setDetails(
            content(movie()).copy(
                rating = DetailRatingState.Ready(PersonalRating(10), selectedValue = 10)
            ),
            onSaveRating = { saved.set(true) },
            onRemoveRating = { removed.set(true) }
        )

        composeRule.onNodeWithText("10 out of 10").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Save rating").performScrollTo().performClick()
        composeRule.onNodeWithText("Remove personal rating").performScrollTo().performClick()
        assertTrue(saved.get())
        assertTrue(removed.get())
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
        onOpenSettings: () -> Unit = {},
        onToggleMovie: () -> Unit = {},
        onSaveRating: () -> Unit = {},
        onRemoveRating: () -> Unit = {}
    ) {
        composeRule.setContent {
            BingeeTheme {
                MediaDetailsContent(
                    state = state,
                    onBack = {},
                    onRefresh = {},
                    onRetry = onRetry,
                    onToggleLibrary = onToggle,
                    onToggleMovieWatched = onToggleMovie,
                    onSaveRating = onSaveRating,
                    onRemoveRating = onRemoveRating,
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

    private fun season(number: Int, name: String, expanded: Boolean): CachedSeason {
        val seriesRef = ExternalMediaRef(MediaSource.TMDB, "1399")
        val seasonRef = ExternalMediaRef(MediaSource.TMDB, (900 + number).toString())
        val episodes = if (!expanded) {
            emptyList()
        } else {
            listOf(
                tracked(seriesRef, seasonRef, number, 1, "Watched episode", EpisodeWatchState.Watched(Instant.EPOCH)),
                tracked(seriesRef, seasonRef, number, 2, "Unwatched episode", EpisodeWatchState.Unwatched),
                tracked(seriesRef, seasonRef, number, 3, "Future episode", EpisodeWatchState.Unavailable)
            )
        }
        return CachedSeason(
            season = Season(seriesRef, seasonRef, number, name = name, episodeCount = episodes.size),
            metadataUpdatedAt = Instant.EPOCH,
            episodesFetchedAt = if (expanded) Instant.EPOCH else null,
            episodes = episodes,
            progress = if (expanded) SeasonProgress(1, 2, false) else SeasonProgress.EMPTY,
            episodeCacheFreshness = if (expanded) CacheFreshness.STALE else null
        )
    }

    private fun tracked(
        seriesRef: ExternalMediaRef,
        seasonRef: ExternalMediaRef,
        seasonNumber: Int,
        number: Int,
        title: String,
        state: EpisodeWatchState
    ) = TrackedEpisode(
        Episode(
            seriesRef,
            seasonRef,
            ExternalMediaRef(MediaSource.TMDB, (1000 + number).toString()),
            seasonNumber,
            number,
            title,
            airDate = if (state == EpisodeWatchState.Unavailable) LocalDate.of(2027, 1, 1) else null
        ),
        state
    )
}
