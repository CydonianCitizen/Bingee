package com.cydoniancitizen.bingee.feature.details

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
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
import com.cydoniancitizen.bingee.core.model.deriveSeasonProgress
import com.cydoniancitizen.bingee.core.model.deriveSeriesProgress
import com.cydoniancitizen.bingee.core.result.AppError
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
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
        // The runtime now lives inside the hero's meta line ("Movie · 120 min") instead of its own
        // labelled field, so it is asserted as a substring of that line.
        composeRule.onNode(hasText("120 min", substring = true)).assertIsDisplayed()
        // Status and genres are individual chips now, not a status Text plus one joined string.
        scrollTo(hasText("Released"))
        composeRule.onNodeWithText("Released").assertIsDisplayed()
        composeRule.onNodeWithText("Drama").assertIsDisplayed()
        composeRule.onNodeWithText("Thriller").assertIsDisplayed()
        scrollTo(hasText("Personal rating"))
        composeRule.onNodeWithText("Personal rating").assertIsDisplayed()
        // The hero meta line only carries the year, so the full date has to survive further down.
        scrollTo(hasText("Release date"))
        composeRule.onNodeWithText("Release date").assertIsDisplayed()
        scrollTo(hasText("Mark watched"))
        composeRule.onNode(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Not watched")
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Mark watched").performClick()
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

        // The hero meta line reads "TV Series · 3 seasons", so the type is a substring of it.
        composeRule.onNode(hasText("TV Series", substring = true)).assertIsDisplayed()
        scrollTo(hasText("Season 1"))
        composeRule.onNodeWithText("Season 1").assertIsDisplayed()
        composeRule.onAllNodesWithText("Specials")[0].performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Episode 1 · Watched episode").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Episode 3 · Future episode").performScrollTo().assertIsDisplayed()
        composeRule.onNode(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Not aired yet")
        ).assertIsNotEnabled()
        composeRule.onNodeWithText("Rate episode").assertDoesNotExist()
        composeRule.onNodeWithText("Rate season").assertDoesNotExist()
    }

    @Test
    fun episodeRowTogglesFromItsBodyAndExposesOneTargetPerEpisode() {
        val toggled = AtomicBoolean(false)
        val regular = season(1, "Season 1", expanded = true)
        setDetails(
            content(
                movie().copy(
                    mediaType = MediaType.SERIES,
                    runtime = null,
                    episodeRuntime = Duration.ofMinutes(50),
                    numberOfSeasons = 1,
                    numberOfEpisodes = 3,
                    productionStatus = ProductionStatus.RETURNING_SERIES
                )
            ).copy(
                series = SeriesDetailUiState(
                    content = SeriesContentState.Ready(listOf(regular), SeriesProgress(1, 2, 0, 1, false)),
                    expandedSeasons = setOf(regular.season.externalRef)
                )
            ),
            onToggleEpisode = { toggled.set(true) }
        )

        scrollTo(hasText("Season 1"))
        // Three episodes plus the season's own checkbox. Each episode publishes exactly one target:
        // its trailing Checkbox must not add a second, or TalkBack would offer every episode twice.
        composeRule.onAllNodes(isToggleable()).assertCountEquals(4)
        // The tap lands on the title, not on the checkbox: the whole row is the target.
        composeRule.onNodeWithText("Episode 2 · Unwatched episode").performScrollTo().performClick()
        assertTrue(toggled.get())
    }

    @Test
    fun seasonHeaderCheckboxReportsEveryStateAndTogglesTheSeason() {
        val toggled = AtomicInteger()
        val expandTaps = AtomicInteger()
        val complete = fetchedSeason(1, "Season 1", watched = 2, trackable = 2)
        val partial = fetchedSeason(2, "Season 2", watched = 1, trackable = 2)
        val untouched = fetchedSeason(3, "Season 3", watched = 0, trackable = 2)
        setDetails(
            seriesState(listOf(complete, partial, untouched)),
            onToggleSeasonWatched = { toggled.incrementAndGet() },
            onToggleSeasonExpanded = { expandTaps.incrementAndGet() }
        )

        scrollTo(hasText("Season 1"))
        // The season action left the overflow, which went with its last item.
        composeRule.onNodeWithContentDescription("Actions for Season 1").assertDoesNotExist()
        assertSeasonState("Season 1", "All trackable episodes watched")
        assertSeasonState("Season 2", "Partly watched")
        assertSeasonState("Season 3", "Not watched")

        // A complete season offers the inverse action, and the checkbox is its only home now.
        scrollTo(hasText("Season 1"))
        composeRule.onNodeWithContentDescription("Mark season unwatched").performClick()
        assertEquals(1, toggled.get())
        // A press the checkbox handled must not also expand the card behind it.
        assertEquals(0, expandTaps.get())
    }

    @Test
    fun seasonCheckboxIsDisabledWhilePendingAndWhenNothingIsTrackable() {
        val pending = fetchedSeason(1, "Season 1", watched = 1, trackable = 2)
        val untrackable = fetchedSeason(2, "Season 2", watched = 0, trackable = 0, unaired = 2)
        val state = seriesState(listOf(pending, untrackable))
        setDetails(
            state.copy(series = state.series.copy(pendingSeasons = setOf(pending.season.externalRef))),
            onToggleSeasonWatched = { throw AssertionError("A disabled checkbox must not toggle the season") }
        )

        // The checkbox cannot say why it is disabled, so the card body still carries the reason.
        scrollTo(hasText("Updating…"))
        composeRule.onNodeWithText("Updating…").assertIsDisplayed()
        seasonCheckbox("Partly watched").assertIsNotEnabled().performClick()
        scrollTo(hasText("Season 2"))
        seasonCheckbox("Not watched").assertIsNotEnabled().performClick()
    }

    @Test
    fun seasonHeaderTargetsAreSeparateAndAcceptTouchesOnEveryEdge() {
        val toggled = AtomicInteger()
        val refreshed = AtomicInteger()
        val expandTaps = AtomicInteger()
        setDetails(
            seriesState(listOf(fetchedSeason(1, "Season 1", watched = 1, trackable = 2))),
            onToggleSeasonWatched = { toggled.incrementAndGet() },
            onRetrySeason = { refreshed.incrementAndGet() },
            onToggleSeasonExpanded = { expandTaps.incrementAndGet() }
        )
        scrollTo(hasText("Season 1"))

        // A checkbox lays out at 24dp and gets its 48dp target from minimumInteractiveComponentSize,
        // which widens the touch bounds without widening the layout bounds. Only touchBoundsInRoot
        // describes what a finger can actually hit.
        val checkboxTouch = composeRule.onNodeWithContentDescription("Mark trackable episodes watched")
            .fetchSemanticsNode().touchBoundsInRoot
        val refreshTouch = composeRule.onNodeWithContentDescription("Refresh season metadata")
            .fetchSemanticsNode().touchBoundsInRoot
        with(composeRule.density) {
            assertTrue(
                "Checkbox target is ${checkboxTouch.width.toDp()} by ${checkboxTouch.height.toDp()}",
                checkboxTouch.width.toDp() >= 48.dp && checkboxTouch.height.toDp() >= 48.dp
            )
            assertTrue(
                "Refresh target is ${refreshTouch.width.toDp()} by ${refreshTouch.height.toDp()}",
                refreshTouch.width.toDp() >= 48.dp && refreshTouch.height.toDp() >= 48.dp
            )
            // Overlapping targets let the later sibling swallow presses meant for its neighbour's
            // edge, and the hit test gives no warning when it happens.
            assertTrue(
                "Refresh and checkbox targets overlap by ${(refreshTouch.right - checkboxTouch.left).toDp()}",
                refreshTouch.right <= checkboxTouch.left
            )
        }

        // Every edge, right and bottom included: a target clipped by a neighbour loses the side
        // that faces it, which a press at the top left corner would never reveal. The presses are
        // injected in root coordinates because the touch bounds reach past the node's own bounds.
        listOf(checkboxTouch, refreshTouch).forEach { target ->
            listOf(
                Offset(target.left + 1f, target.center.y),
                Offset(target.center.x, target.top + 1f),
                Offset(target.right - 1f, target.center.y),
                Offset(target.center.x, target.bottom - 1f)
            ).forEach { point -> composeRule.onRoot().performTouchInput { click(point) } }
        }
        assertEquals(4, toggled.get())
        assertEquals(4, refreshed.get())
        // Neither control may let its press fall through to the header's expand action.
        assertEquals(0, expandTaps.get())
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

        scrollTo(hasText("10 out of 10"))
        composeRule.onNodeWithText("10 out of 10").assertIsDisplayed()
        composeRule.onNode(
            SemanticsMatcher.expectValue(
                SemanticsProperties.StateDescription,
                "Personal rating, 10 out of 10"
            )
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Save rating").performScrollTo().performClick()
        composeRule.onNodeWithText("Remove personal rating").performScrollTo().performClick()
        assertTrue(saved.get())
        assertTrue(removed.get())
    }

    @Test
    fun loadingFullErrorRetryAndUnauthorizedSettingsAreActionable() {
        val retried = AtomicBoolean(false)
        val settings = AtomicBoolean(false)
        var state by mutableStateOf(
            MediaDetailsUiState(
                today = LocalDate.of(2026, 8, 18),
                content = DetailContentState.Loading
            )
        )
        setDetailsState(
            state = { state },
            onRetry = { retried.set(true) },
            onOpenSettings = { settings.set(true) }
        )
        composeRule.onNodeWithText("Loading title details").assertIsDisplayed()

        composeRule.runOnIdle {
            state = MediaDetailsUiState(
                today = LocalDate.of(2026, 8, 18),
                content = DetailContentState.Error(AppError.NetworkUnavailable)
            )
        }
        composeRule.onNodeWithText("Retry").performClick()
        assertTrue(retried.get())

        composeRule.runOnIdle {
            state = MediaDetailsUiState(
                today = LocalDate.of(2026, 8, 18),
                content = DetailContentState.Error(AppError.Unauthorized)
            )
        }
        composeRule.onNodeWithText("Open Settings").performClick()
        assertTrue(settings.get())
    }

    @Test
    fun staleRefreshErrorAndRefreshIndicatorKeepCachedContentVisible() {
        var state by mutableStateOf(
            content(movie()).copy(refresh = DetailRefreshState.Error(AppError.NetworkUnavailable))
        )
        setDetailsState({ state })
        composeRule.onNodeWithText("Movie title").assertIsDisplayed()
        composeRule.onNodeWithText("Saved details remain available.", substring = true).assertIsDisplayed()

        composeRule.runOnIdle {
            state = content(movie()).copy(refresh = DetailRefreshState.Refreshing)
        }
        composeRule.onNodeWithText("Movie title").assertIsDisplayed()
    }

    @Test
    fun libraryActionAndMissingImageSemanticsAreAccessible() {
        val toggled = AtomicBoolean(false)
        setDetails(content(movie().copy(posterUrl = null, backdropUrl = null)), onToggle = { toggled.set(true) })

        // Asserted before the library click, which can scroll the hero artwork out of view.
        composeRule.onNodeWithContentDescription("No poster available for Movie title").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("No backdrop available for Movie title").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Back").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Refresh details").assertIsDisplayed()
        scrollTo(hasText("Add to library"))
        composeRule.onNodeWithText("Add to library").performClick()
        assertTrue(toggled.get())
    }

    /**
     * Scrolls the screen's single lazy list until [matcher] matches. Items below the hero are not
     * composed until scrolled to, so [performScrollTo] alone cannot reach them.
     */
    private fun scrollTo(matcher: SemanticsMatcher) {
        composeRule.onNode(hasScrollAction()).performScrollToNode(matcher)
    }

    private fun setDetails(
        state: MediaDetailsUiState,
        onRetry: () -> Unit = {},
        onToggle: () -> Unit = {},
        onOpenSettings: () -> Unit = {},
        onToggleMovie: () -> Unit = {},
        onSaveRating: () -> Unit = {},
        onRemoveRating: () -> Unit = {},
        onToggleEpisode: (TrackedEpisode) -> Unit = {},
        onToggleSeasonWatched: (CachedSeason) -> Unit = {},
        onToggleSeasonExpanded: (CachedSeason) -> Unit = {},
        onRetrySeason: (CachedSeason) -> Unit = {}
    ) = setDetailsState(
        { state },
        onRetry,
        onToggle,
        onOpenSettings,
        onToggleMovie,
        onSaveRating,
        onRemoveRating,
        onToggleEpisode,
        onToggleSeasonWatched,
        onToggleSeasonExpanded,
        onRetrySeason
    )

    private fun setDetailsState(
        state: () -> MediaDetailsUiState,
        onRetry: () -> Unit = {},
        onToggle: () -> Unit = {},
        onOpenSettings: () -> Unit = {},
        onToggleMovie: () -> Unit = {},
        onSaveRating: () -> Unit = {},
        onRemoveRating: () -> Unit = {},
        onToggleEpisode: (TrackedEpisode) -> Unit = {},
        onToggleSeasonWatched: (CachedSeason) -> Unit = {},
        onToggleSeasonExpanded: (CachedSeason) -> Unit = {},
        onRetrySeason: (CachedSeason) -> Unit = {}
    ) {
        composeRule.setContent {
            BingeeTheme {
                MediaDetailsContent(
                    state = state(),
                    onBack = {},
                    onRefresh = {},
                    onRetry = onRetry,
                    onToggleLibrary = onToggle,
                    onToggleMovieWatched = onToggleMovie,
                    onSaveRating = onSaveRating,
                    onRemoveRating = onRemoveRating,
                    onToggleEpisode = onToggleEpisode,
                    onToggleSeasonWatched = onToggleSeasonWatched,
                    onToggleSeasonExpanded = onToggleSeasonExpanded,
                    onRetrySeason = onRetrySeason,
                    onDismissLibraryError = {},
                    onOpenSettings = onOpenSettings
                )
            }
        }
    }

    private fun content(details: MediaDetails) = MediaDetailsUiState(
        today = LocalDate.of(2026, 8, 18),
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
        releaseDate = LocalDate.of(2024, 1, 15),
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

    /**
     * A series whose seasons are all already fetched, so every season header renders its refresh
     * and mark-watched controls while the cards stay collapsed.
     */
    private fun seriesState(seasons: List<CachedSeason>) = content(
        movie().copy(
            mediaType = MediaType.SERIES,
            runtime = null,
            episodeRuntime = Duration.ofMinutes(50),
            numberOfSeasons = seasons.size,
            numberOfEpisodes = seasons.sumOf { it.season.episodeCount },
            productionStatus = ProductionStatus.RETURNING_SERIES
        )
    ).copy(
        series = SeriesDetailUiState(content = SeriesContentState.Ready(seasons, deriveSeriesProgress(seasons)))
    )

    private fun fetchedSeason(number: Int, name: String, watched: Int, trackable: Int, unaired: Int = 0): CachedSeason {
        val seriesRef = ExternalMediaRef(MediaSource.TMDB, "1399")
        val seasonRef = ExternalMediaRef(MediaSource.TMDB, (900 + number).toString())
        val episodes = (1..trackable).map { index ->
            tracked(
                seriesRef,
                seasonRef,
                number,
                index,
                "$name episode $index",
                if (index <= watched) EpisodeWatchState.Watched(Instant.EPOCH) else EpisodeWatchState.Unwatched
            )
        } + (1..unaired).map { index ->
            tracked(
                seriesRef,
                seasonRef,
                number,
                trackable + index,
                "$name future $index",
                EpisodeWatchState.Unavailable
            )
        }
        return CachedSeason(
            season = Season(seriesRef, seasonRef, number, name = name, episodeCount = episodes.size),
            metadataUpdatedAt = Instant.EPOCH,
            episodesFetchedAt = Instant.EPOCH,
            episodes = episodes,
            progress = deriveSeasonProgress(episodes),
            episodeCacheFreshness = CacheFreshness.FRESH
        )
    }

    private fun seasonCheckbox(watchState: String) =
        composeRule.onNode(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, watchState))

    private fun assertSeasonState(seasonTitle: String, watchState: String) {
        scrollTo(hasText(seasonTitle))
        seasonCheckbox(watchState).assertIsDisplayed()
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
