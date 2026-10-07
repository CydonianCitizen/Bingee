package com.cydoniancitizen.bingee.feature.home

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import com.cydoniancitizen.bingee.R
import com.cydoniancitizen.bingee.core.designsystem.theme.BingeeTheme
import com.cydoniancitizen.bingee.core.model.CalendarRefreshOutcome
import com.cydoniancitizen.bingee.core.model.CalendarRefreshSummary
import com.cydoniancitizen.bingee.core.model.ContinueWatchingItem
import com.cydoniancitizen.bingee.core.model.EpisodePosition
import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.MediaSearchResult
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
import com.cydoniancitizen.bingee.testutil.TestLocaleRule
import com.cydoniancitizen.bingee.testutil.localizedTestContext
import com.cydoniancitizen.bingee.testutil.scrollListTo
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HomeScreenTest {
    @get:Rule(order = 0)
    val localeRule = TestLocaleRule()

    private val context get() = localizedTestContext

    @get:Rule(order = 1)
    val composeRule = createComposeRule()
    private val today = LocalDate.of(2026, 8, 3)

    @Test
    fun featuredMembershipActionAndSavedStateDescribeCollectionForMoviesAndSeries() {
        val context = localizedTestContext
        val addLabel = context.getString(R.string.collection_action_add)
        val savedLabel = context.getString(R.string.collection_state_in)
        val added = AtomicReference<MediaSearchResult?>(null)
        var state by mutableStateOf(HomeUiState(content = HomeContentState.Empty, today = today))
        setHomeState(
            state = { state },
            onAddToWatchlist = { item ->
                added.set(item)
                state = state.copy(libraryMemberships = setOf(item.externalRef to item.mediaType))
            }
        )

        for (type in listOf(MediaType.MOVIE, MediaType.SERIES)) {
            val item = MediaSearchResult(ExternalMediaRef(MediaSource.TMDB, "42"), type, "Featured title")
            composeRule.runOnIdle {
                state = state.copy(
                    featuredMovies = listOf(item).filter { type == MediaType.MOVIE },
                    featuredSeries = listOf(item).filter { type == MediaType.SERIES },
                    libraryMemberships = emptySet()
                )
                added.set(null)
            }
            composeRule.onNodeWithContentDescription(addLabel)
                .assertIsDisplayed().assertIsEnabled()
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
                .performClick()
            assertEquals(item, added.get())
            composeRule.onNodeWithContentDescription(savedLabel)
                .assertContentDescriptionEquals(savedLabel).assertIsDisplayed().assertIsNotEnabled()
            composeRule.onNodeWithContentDescription(addLabel).assertDoesNotExist()
        }
    }

    @Test
    fun emptyLoadingAndLastUpdateStatesAreVisible() {
        var state by mutableStateOf(HomeUiState(content = HomeContentState.Loading, today = today))
        setHomeState({ state })
        composeRule.onNodeWithText(context.getString(R.string.home_loading)).assertIsDisplayed()

        composeRule.runOnIdle {
            state = HomeUiState(
                content = HomeContentState.Empty,
                lastSuccessfulRefreshAt = Instant.parse("2026-08-03T12:00:00Z"),
                today = today
            )
        }
        composeRule.onNodeWithText(context.getString(R.string.home_empty_title)).assertIsDisplayed()
        composeRule.onNodeWithText(
            context.getString(R.string.home_last_updated, "").trim(),
            substring = true
        ).assertIsDisplayed()
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

        composeRule.onNodeWithText(context.getString(R.string.home_event_movie_release)).assertIsDisplayed()
        composeRule.onNodeWithText(
            context.getString(R.string.home_event_season, 1, "Subject season"),
            substring = true
        ).assertIsDisplayed()
        composeRule.onNodeWithText(
            context.getString(R.string.home_event_episode, 1, 1, "Subject episode"),
            substring = true
        ).assertIsDisplayed()
        // The card owns one description for the whole row; its poster stays decorative so the
        // title is not announced a second time.
        composeRule.onNodeWithContentDescription(context.getString(R.string.home_open_event_details, "Title movie"))
            .assertContentDescriptionEquals(context.getString(R.string.home_open_event_details, "Title movie"))
        composeRule.onNodeWithText("Remind me").assertDoesNotExist()
        composeRule.onNodeWithContentDescription(
            context.getString(R.string.home_open_event_details, "Title episode")
        ).performClick()
        assertEquals(events.last().mediaRef to MediaType.SERIES, opened.get())
    }

    @Test
    fun continueWatchingCardShowsProgressNextEpisodeAndOpensSeriesDetails() {
        val opened = AtomicReference<Pair<ExternalMediaRef, MediaType>>()
        val marked = AtomicReference<ContinueWatchingItem>()
        val item = continueItem()
        setHomeState(
            {
                HomeUiState(
                    content = HomeContentState.Empty,
                    continueWatching = listOf(item),
                    today = today
                )
            },
            onOpenDetails = { ref, type -> opened.set(ref to type) },
            onMarkNextEpisode = marked::set
        )

        composeRule.onNodeWithText(context.getString(R.string.home_continue_watching)).assertIsDisplayed()
        // The card spans the row, so it keeps the same margin to both screen edges.
        val root = composeRule.onRoot().getBoundsInRoot()
        val card = composeRule.onNodeWithContentDescription(
            context.getString(R.string.home_open_continue_details, "Continuing Series")
        ).getBoundsInRoot()
        assertEquals(card.left.value, (root.right - card.right).value, 1f)
        composeRule.onNodeWithText(
            context.resources.getQuantityString(R.plurals.home_continue_watching_progress, 8, 3, 8)
        ).assertIsDisplayed()
        composeRule.onNodeWithText(
            context.getString(R.string.home_continue_watching_last_episode, 2, 4)
        ).assertIsDisplayed()
        composeRule.onNodeWithText(
            context.getString(R.string.home_continue_watching_next_episode, 2, 5)
        ).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(
            context.getString(R.string.home_continue_watching_mark_watched, 2, 5)
        ).performClick()
        assertEquals(item, marked.get())
        composeRule.onNodeWithContentDescription(
            context.getString(R.string.home_open_continue_details, "Continuing Series")
        ).performClick()
        assertEquals(item.mediaRef to MediaType.SERIES, opened.get())
    }

    @Test
    fun continueWatchingHidesShortcutWithoutEpisodeIdentityAndMarkedFeedbackOffersUndo() {
        val undone = AtomicBoolean()
        val item = continueItem().copy(nextEpisodeRef = null)
        setHomeState(
            {
                HomeUiState(
                    content = HomeContentState.Empty,
                    continueWatching = listOf(item),
                    today = today,
                    snackbar = HomeSnackbar.EpisodeMarked(
                        "Continuing Series",
                        EpisodePosition(2, 5),
                        ExternalMediaRef(MediaSource.TMDB, "episode-205")
                    )
                )
            },
            onUndoMarkedEpisode = { undone.set(true) }
        )

        composeRule.onNodeWithContentDescription(
            context.getString(R.string.home_continue_watching_mark_watched, 2, 5)
        ).assertDoesNotExist()
        composeRule.onNodeWithText(
            context.getString(R.string.home_continue_watching_marked, "Continuing Series", 2, 5)
        ).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.action_undo)).performClick()
        composeRule.waitForIdle()
        assertTrue(undone.get())
    }

    private fun continueItem() = ContinueWatchingItem(
        mediaRef = ExternalMediaRef(MediaSource.TMDB, "continue-1"),
        mediaType = MediaType.SERIES,
        title = "Continuing Series",
        posterUrl = null,
        progress = SeriesProgress(3, 8, 0, 1, false),
        nextEpisode = EpisodePosition(2, 5),
        updatedAt = Instant.parse("2026-08-03T12:00:00Z"),
        lastWatchedEpisode = EpisodePosition(2, 4),
        nextEpisodeRef = ExternalMediaRef(MediaSource.TMDB, "episode-205")
    )

    @Test
    fun continueWatchingSectionIsAbsentWhenEmpty() {
        setHome(HomeUiState(content = HomeContentState.Empty, today = today))

        composeRule.onNodeWithText(context.getString(R.string.home_continue_watching)).assertDoesNotExist()
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

        composeRule.onNodeWithText(
            context.getString(R.string.home_refresh_partial, 1, 1),
            substring = true
        ).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.action_retry)).performClick()
        assertTrue(refreshed.get())
        composeRule.onNodeWithText("Title movie").assertIsDisplayed()

        composeRule.runOnIdle {
            state = HomeUiState(
                content = HomeContentState.Empty,
                refresh = HomeRefreshState.CredentialRequired,
                today = today
            )
        }
        composeRule.onNodeWithText(context.getString(R.string.search_open_settings)).performClick()
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
        composeRule.onNodeWithContentDescription(
            context.getString(R.string.notifications_title)
        ).assertIsDisplayed().performClick()
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

        composeRule.scrollListTo(hasText(context.getString(R.string.home_event_recent))).assertIsDisplayed()
        composeRule.scrollListTo(hasText(context.getString(R.string.home_event_today))).assertIsDisplayed()
        composeRule.scrollListTo(hasText(context.getString(R.string.home_event_upcoming))).assertIsDisplayed()
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
        composeRule.onNodeWithContentDescription(context.getString(R.string.home_open_event_details, longEvent.title))
            .assertContentDescriptionEquals(context.getString(R.string.home_open_event_details, longEvent.title))
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
        onOpenDetails: (ExternalMediaRef, MediaType) -> Unit = { _, _ -> },
        onMarkNextEpisode: (ContinueWatchingItem) -> Unit = {},
        onUndoMarkedEpisode: () -> Unit = {},
        onAddToWatchlist: (MediaSearchResult) -> Unit = {}
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
                    onOpenDetails = onOpenDetails,
                    onMarkNextEpisode = onMarkNextEpisode,
                    onUndoMarkedEpisode = onUndoMarkedEpisode,
                    onAddToWatchlist = onAddToWatchlist
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
