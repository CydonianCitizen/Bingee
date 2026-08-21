package com.cydoniancitizen.bingee.feature.home

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import com.cydoniancitizen.bingee.core.designsystem.theme.BingeeTheme
import com.cydoniancitizen.bingee.core.model.CalendarRefreshOutcome
import com.cydoniancitizen.bingee.core.model.CalendarRefreshSummary
import com.cydoniancitizen.bingee.core.model.ContinueWatchingItem
import com.cydoniancitizen.bingee.core.model.EpisodePosition
import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.core.model.ReleaseDateCategory
import com.cydoniancitizen.bingee.core.model.ReleaseDateGroup
import com.cydoniancitizen.bingee.core.model.ReleaseEvent
import com.cydoniancitizen.bingee.core.model.ReleaseEventType
import com.cydoniancitizen.bingee.core.model.ReleaseSubjectIdentity
import com.cydoniancitizen.bingee.core.model.ReleaseSubjectType
import com.cydoniancitizen.bingee.core.model.SeriesProgress
import com.cydoniancitizen.bingee.core.result.AppError
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HomeScreenTest {
    @get:Rule val composeRule = createComposeRule()
    private val today = LocalDate.of(2026, 8, 3)

    @Test
    fun emptyLoadingAndLastUpdateStatesAreVisible() {
        var state by mutableStateOf(HomeUiState(content = HomeContentState.Loading, today = today))
        setHomeState({ state })
        composeRule.onNodeWithText("Loading saved release events").assertIsDisplayed()

        composeRule.runOnIdle {
            state = HomeUiState(
                content = HomeContentState.Empty,
                lastSuccessfulRefreshAt = Instant.parse("2026-08-03T12:00:00Z"),
                today = today
            )
        }
        composeRule.onNodeWithText("No releases to show").assertIsDisplayed()
        composeRule.onNodeWithText("Last successful update:", substring = true).assertIsDisplayed()
    }

    @Test
    fun groupedMovieSeasonEpisodeRowsRenderAndOpenParentDetails() {
        val opened = AtomicReference<Pair<ExternalMediaRef, MediaType>>()
        val events = listOf(
            event("movie", ReleaseSubjectType.MEDIA, ReleaseEventType.MOVIE_RELEASE, MediaType.MOVIE),
            event("season", ReleaseSubjectType.SEASON, ReleaseEventType.SEASON_PREMIERE, MediaType.SERIES),
            event("episode", ReleaseSubjectType.EPISODE, ReleaseEventType.EPISODE_AIRING, MediaType.SERIES)
        )
        setHome(
            state = HomeUiState(
                content = HomeContentState.Events(
                    listOf(ReleaseDateGroup(today, ReleaseDateCategory.TODAY, events))
                ),
                today = today
            ),
            onOpenDetails = { ref, type -> opened.set(ref to type) }
        )

        composeRule.onNodeWithText("Movie release").assertIsDisplayed()
        composeRule.onNodeWithText("Season premiere · S1", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Season 1, episode 1", substring = true).assertIsDisplayed()
        // The card owns one description for the whole row; its poster stays decorative so the
        // title is not announced a second time.
        composeRule.onNodeWithContentDescription("Open title details for Title movie")
            .assertContentDescriptionEquals("Open title details for Title movie")
        composeRule.onNodeWithText("Remind me").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Open title details for Title episode").performClick()
        assertEquals(events.last().mediaRef to MediaType.SERIES, opened.get())
    }

    @Test
    fun continueWatchingCardShowsProgressNextEpisodeAndOpensSeriesDetails() {
        val opened = AtomicReference<Pair<ExternalMediaRef, MediaType>>()
        val item = ContinueWatchingItem(
            mediaRef = ExternalMediaRef(MediaSource.TMDB, "continue-1"),
            mediaType = MediaType.SERIES,
            title = "Continuing Series",
            posterUrl = null,
            progress = SeriesProgress(3, 8, 0, 1, false),
            nextEpisode = EpisodePosition(2, 5),
            updatedAt = Instant.parse("2026-08-03T12:00:00Z")
        )
        setHome(
            HomeUiState(
                content = HomeContentState.Empty,
                continueWatching = listOf(item),
                today = today
            ),
            onOpenDetails = { ref, type -> opened.set(ref to type) }
        )

        composeRule.onNodeWithText("Continue Watching").assertIsDisplayed()
        composeRule.onNodeWithText("3 of 8 episodes").assertIsDisplayed()
        composeRule.onNodeWithText("Next: Season 2 • Episode 5").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Open details for Continuing Series").performClick()
        assertEquals(item.mediaRef to MediaType.SERIES, opened.get())
    }

    @Test
    fun continueWatchingSectionIsAbsentWhenEmpty() {
        setHome(HomeUiState(content = HomeContentState.Empty, today = today))

        composeRule.onNodeWithText("Continue Watching").assertDoesNotExist()
    }

    @Test
    fun refreshPartialFailureRetryAndCredentialSettingsActionsRemainNonDestructive() {
        val refreshed = AtomicBoolean()
        val settings = AtomicBoolean()
        val partial = CalendarRefreshSummary(
            CalendarRefreshOutcome.PARTIAL_SUCCESS,
            2,
            1,
            1,
            0,
            AppError.NetworkUnavailable
        )
        var state by mutableStateOf(
            HomeUiState(
                content = HomeContentState.Events(
                    listOf(
                        ReleaseDateGroup(
                            today,
                            ReleaseDateCategory.TODAY,
                            listOf(
                                event(
                                    "movie",
                                    ReleaseSubjectType.MEDIA,
                                    ReleaseEventType.MOVIE_RELEASE,
                                    MediaType.MOVIE
                                )
                            )
                        )
                    )
                ),
                refresh = HomeRefreshState.Partial(partial),
                today = today
            )
        )
        setHomeState(
            state = { state },
            onRefresh = { refreshed.set(true) },
            onOpenSettings = { settings.set(true) }
        )

        composeRule.onNodeWithText("Succeeded: 1 · Failed: 1.", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Retry").performClick()
        assertTrue(refreshed.get())
        composeRule.onNodeWithText("Title movie").assertIsDisplayed()

        composeRule.runOnIdle {
            state = HomeUiState(
                content = HomeContentState.Empty,
                refresh = HomeRefreshState.CredentialRequired,
                today = today
            )
        }
        composeRule.onNodeWithText("Open Settings").performClick()
        assertTrue(settings.get())
    }

    @Test
    fun refreshButtonHasAccessibleLabel() {
        val notificationsClicked = AtomicBoolean()
        setHome(
            HomeUiState(content = HomeContentState.Empty, today = today),
            onOpenNotifications = { notificationsClicked.set(true) }
        )

        composeRule.onNodeWithContentDescription("Refresh release calendar").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Notifications").assertIsDisplayed().performClick()
        assertTrue(notificationsClicked.get())
    }

    @Test
    fun recentTodayAndUpcomingWordingIsDeterministic() {
        val groups = listOf(
            ReleaseDateGroup(
                today.minusDays(1),
                ReleaseDateCategory.RECENT,
                listOf(
                    event("recent", ReleaseSubjectType.MEDIA, ReleaseEventType.MOVIE_RELEASE, MediaType.MOVIE)
                        .copy(eventDate = today.minusDays(1))
                )
            ),
            ReleaseDateGroup(
                today,
                ReleaseDateCategory.TODAY,
                listOf(event("today", ReleaseSubjectType.MEDIA, ReleaseEventType.MOVIE_RELEASE, MediaType.MOVIE))
            ),
            ReleaseDateGroup(
                today.plusDays(1),
                ReleaseDateCategory.UPCOMING,
                listOf(
                    event("future", ReleaseSubjectType.MEDIA, ReleaseEventType.MOVIE_RELEASE, MediaType.MOVIE)
                        .copy(eventDate = today.plusDays(1))
                )
            )
        )
        setHome(HomeUiState(content = HomeContentState.Events(groups), today = today))

        composeRule.onNodeWithText("Recently released").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Releases today").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Upcoming release").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun darkThemeLargeFontAndLongMissingPosterTitleRenderAccessibly() {
        val longEvent = event(
            "long",
            ReleaseSubjectType.MEDIA,
            ReleaseEventType.MOVIE_RELEASE,
            MediaType.MOVIE
        ).copy(title = "A deliberately long release title that must remain readable")
        val state = HomeUiState(
            content = HomeContentState.Events(
                listOf(ReleaseDateGroup(today, ReleaseDateCategory.TODAY, listOf(longEvent)))
            ),
            today = today
        )
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = 2f)) {
                BingeeTheme(darkTheme = true) {
                    HomeContent(
                        state = state,
                        onRefresh = {},
                        onRetryLocal = {},
                        onDismissFeedback = {},
                        onOpenNotifications = {},
                        onOpenSettings = {},
                        onOpenDetails = { _, _ -> }
                    )
                }
            }
        }

        composeRule.onNodeWithText(longEvent.title).assertIsDisplayed()
        // A missing poster falls back to the placeholder without adding a second description.
        composeRule.onNodeWithContentDescription("Open title details for ${longEvent.title}")
            .assertContentDescriptionEquals("Open title details for ${longEvent.title}")
    }

    private fun setHome(
        state: HomeUiState,
        onRefresh: () -> Unit = {},
        onOpenNotifications: () -> Unit = {},
        onOpenSettings: () -> Unit = {},
        onOpenDetails: (ExternalMediaRef, MediaType) -> Unit = { _, _ -> }
    ) = setHomeState({ state }, onRefresh, onOpenNotifications, onOpenSettings, onOpenDetails)

    private fun setHomeState(
        state: () -> HomeUiState,
        onRefresh: () -> Unit = {},
        onOpenNotifications: () -> Unit = {},
        onOpenSettings: () -> Unit = {},
        onOpenDetails: (ExternalMediaRef, MediaType) -> Unit = { _, _ -> }
    ) {
        composeRule.setContent {
            BingeeTheme {
                HomeContent(
                    state = state(),
                    onRefresh = onRefresh,
                    onRetryLocal = {},
                    onDismissFeedback = {},
                    onOpenNotifications = onOpenNotifications,
                    onOpenSettings = onOpenSettings,
                    onOpenDetails = onOpenDetails
                )
            }
        }
    }

    private fun event(id: String, subjectType: ReleaseSubjectType, eventType: ReleaseEventType, mediaType: MediaType) =
        ReleaseEvent(
            mediaRef = ExternalMediaRef(MediaSource.TMDB, "parent-$id"),
            subject = ReleaseSubjectIdentity(MediaSource.TMDB, subjectType, id, eventType),
            mediaType = mediaType,
            eventDate = today,
            title = "Title $id",
            seasonNumber = 1.takeIf { subjectType != ReleaseSubjectType.MEDIA },
            episodeNumber = 1.takeIf { subjectType == ReleaseSubjectType.EPISODE },
            subjectTitle = "Subject $id"
        )
}
