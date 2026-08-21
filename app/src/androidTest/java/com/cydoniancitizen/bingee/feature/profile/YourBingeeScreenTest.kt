package com.cydoniancitizen.bingee.feature.profile

import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.cydoniancitizen.bingee.core.designsystem.theme.BingeeTheme
import com.cydoniancitizen.bingee.core.model.ContinueWatchingItem
import com.cydoniancitizen.bingee.core.model.EpisodePosition
import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.LibraryEntry
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.core.model.SeriesProgress
import com.cydoniancitizen.bingee.domain.model.GenreStatistic
import com.cydoniancitizen.bingee.domain.model.WatchedStatistics
import com.cydoniancitizen.bingee.testutil.scrollListTo
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class YourBingeeScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun statisticsPreviewShowsMetricsPodiumAndExistingStatisticsAction() {
        val opened = AtomicBoolean(false)
        composeRule.setContent {
            BingeeTheme {
                YourBingeeContent(
                    state = ProfileUiState(
                        today = LocalDate.of(2026, 8, 18),
                        isLoading = false,
                        isStatisticsLoading = false,
                        statistics = WatchedStatistics(
                            moviesWatchedCount = 42,
                            movieWatchTimeMinutes = 4_880,
                            movieGenres = genres()
                        )
                    ),
                    onOpenSettings = {},
                    onOpenDetails = { _, _ -> },
                    onOpenCollection = {},
                    onNavigateToSearch = {},
                    onOpenStatistics = { opened.set(true) },
                    onRetryStatistics = {},
                    onRetry = {}
                )
            }
        }

        composeRule.scrollListTo(hasText("Your statistics")).assertIsDisplayed()
        composeRule.scrollListTo(hasText("42")).assertIsDisplayed()
        composeRule.scrollListTo(hasText("Drama")).assertIsDisplayed()
        composeRule.scrollListTo(hasText("View all statistics")).performClick()

        assertTrue(opened.get())
    }

    @Test
    fun statisticsPreviewUsesRestrainedEmptyStateBelowThreeGenres() {
        composeRule.setContent {
            BingeeTheme {
                YourBingeeContent(
                    state = ProfileUiState(
                        today = LocalDate.of(2026, 8, 18),
                        isLoading = false,
                        isStatisticsLoading = false,
                        statistics = WatchedStatistics(
                            moviesWatchedCount = 1,
                            movieGenres = genres().take(2)
                        )
                    ),
                    onOpenSettings = {},
                    onOpenDetails = { _, _ -> },
                    onOpenCollection = {},
                    onNavigateToSearch = {},
                    onOpenStatistics = {},
                    onRetryStatistics = {},
                    onRetry = {}
                )
            }
        }

        // Movies has two genres and Series has none, and each block falls back on its own count,
        // so the restrained empty state is expected once per block rather than once per screen.
        composeRule.scrollListTo(hasText("Your statistics")).assertIsDisplayed()
        composeRule.onAllNodesWithText("Not enough data yet").assertCountEquals(2)
    }

    @Test
    fun watchingShelfShowsActionableProgressAndOpensDetails() {
        val opened = AtomicReference<Pair<ExternalMediaRef, MediaType>>()
        composeRule.setContent {
            BingeeTheme {
                YourBingeeContent(
                    state = ProfileUiState(
                        today = LocalDate.of(2026, 8, 18),
                        isLoading = false,
                        isStatisticsLoading = false,
                        watching = listOf(
                            ContinueWatchingItem(
                                mediaRef = ExternalMediaRef(MediaSource.TMDB, "100"),
                                mediaType = MediaType.SERIES,
                                title = "Actionable Series",
                                posterUrl = null,
                                progress = SeriesProgress(3, 8, 0, 1, false),
                                nextEpisode = EpisodePosition(2, 5),
                                updatedAt = Instant.parse("2026-08-03T12:00:00Z")
                            )
                        )
                    ),
                    onOpenSettings = {},
                    onOpenDetails = { reference, mediaType -> opened.set(reference to mediaType) },
                    onOpenCollection = {},
                    onNavigateToSearch = {},
                    onOpenStatistics = {},
                    onRetryStatistics = {},
                    onRetry = {}
                )
            }
        }

        composeRule.onNodeWithText("Watching").assertIsDisplayed()
        composeRule.onNodeWithContentDescription(
            "Open Actionable Series. 3 of 8 episodes watched. Next position S2 E5."
        ).performClick()

        assertEquals(ExternalMediaRef(MediaSource.TMDB, "100") to MediaType.SERIES, opened.get())
    }

    @Test
    fun favoritesShelfOpensDetailsForItsEntry() {
        val opened = AtomicReference<Pair<ExternalMediaRef, MediaType>>()
        composeRule.setContent {
            BingeeTheme {
                YourBingeeContent(
                    state = ProfileUiState(
                        today = LocalDate.of(2026, 8, 18),
                        isLoading = false,
                        isStatisticsLoading = false,
                        favorites = listOf(
                            LibraryEntry(
                                mediaRef = ExternalMediaRef(MediaSource.TMDB, "42"),
                                mediaType = MediaType.MOVIE,
                                title = "Favorite Movie",
                                addedAt = Instant.EPOCH,
                                releaseDate = LocalDate.of(2016, 11, 11),
                                isFavorite = true
                            )
                        )
                    ),
                    onOpenSettings = {},
                    onOpenDetails = { reference, mediaType -> opened.set(reference to mediaType) },
                    onOpenCollection = {},
                    onNavigateToSearch = {},
                    onOpenStatistics = {},
                    onRetryStatistics = {},
                    onRetry = {}
                )
            }
        }

        composeRule.scrollListTo(hasText("Your favorites")).assertIsDisplayed()
        composeRule.scrollListTo(hasContentDescription("Open Favorite Movie. Movie · 2016."))
            .performClick()

        assertEquals(ExternalMediaRef(MediaSource.TMDB, "42") to MediaType.MOVIE, opened.get())
    }

    @Test
    fun shellActionsReachSettingsSearchAndTheRoutedCollectionShortcut() {
        val settingsOpened = AtomicBoolean(false)
        val searchOpened = AtomicBoolean(false)
        val collection = AtomicReference<ProfileCollectionShortcut>()
        composeRule.setContent {
            BingeeTheme {
                YourBingeeContent(
                    // Empty shelves are the first-run shell, where the empty states offer the CTA.
                    state = ProfileUiState(
                        today = LocalDate.of(2026, 8, 18),
                        isLoading = false,
                        isStatisticsLoading = false
                    ),
                    onOpenSettings = { settingsOpened.set(true) },
                    onOpenDetails = { _, _ -> },
                    onOpenCollection = collection::set,
                    onNavigateToSearch = { searchOpened.set(true) },
                    onOpenStatistics = {},
                    onRetryStatistics = {},
                    onRetry = {}
                )
            }
        }

        composeRule.onNodeWithContentDescription("Settings").performClick()
        assertTrue(settingsOpened.get())

        // The shortcut carries which collection to open, so the route argument is the assertion.
        composeRule.scrollListTo(hasContentDescription("Watch later · 0")).performClick()
        assertEquals(ProfileCollectionShortcut.WATCH_LATER, collection.get())

        // Watching and Favorites share the CTA label; Watching is the first section in the list.
        composeRule.onNodeWithText("No series in progress").performScrollTo()
        composeRule.onAllNodesWithText("Explore in Search").onFirst().performClick()
        assertTrue(searchOpened.get())
    }

    @Test
    fun podiumExposesRankWithoutShowingItAndWithoutRelyingOnColour() {
        composeRule.setContent {
            BingeeTheme {
                YourBingeeContent(
                    state = ProfileUiState(
                        today = LocalDate.of(2026, 8, 18),
                        isLoading = false,
                        isStatisticsLoading = false,
                        statistics = WatchedStatistics(
                            moviesWatchedCount = 36,
                            movieGenres = genres()
                        )
                    ),
                    onOpenSettings = {},
                    onOpenDetails = { _, _ -> },
                    onOpenCollection = {},
                    onNavigateToSearch = {},
                    onOpenStatistics = {},
                    onRetryStatistics = {},
                    onRetry = {}
                )
            }
        }

        // Height, podium position, and the gold/silver/bronze surfaces are the only visual carriers
        // of rank, so each step has to speak its own place, genre, and count.
        composeRule.scrollListTo(hasContentDescription("1st place, Drama, 18 titles")).assertIsDisplayed()
        composeRule.scrollListTo(hasContentDescription("2nd place, Comedy, 10 titles")).assertIsDisplayed()
        composeRule.scrollListTo(hasContentDescription("3rd place, Thriller, 8 titles")).assertIsDisplayed()

        // The visual design stays free of rank labels and medals.
        composeRule.onAllNodesWithText("1st place").assertCountEquals(0)
        composeRule.onAllNodesWithText("2nd place").assertCountEquals(0)
        composeRule.onAllNodesWithText("3rd place").assertCountEquals(0)
    }

    @Test
    fun parentOwnedPosterItemDoesNotRepeatTheTitleThroughThePosterImage() {
        composeRule.setContent {
            BingeeTheme {
                YourBingeeContent(
                    state = ProfileUiState(
                        today = LocalDate.of(2026, 8, 18),
                        isLoading = false,
                        isStatisticsLoading = false,
                        favorites = listOf(
                            LibraryEntry(
                                mediaRef = ExternalMediaRef(MediaSource.TMDB, "42"),
                                mediaType = MediaType.MOVIE,
                                title = "Favorite Movie",
                                addedAt = Instant.EPOCH,
                                releaseDate = LocalDate.of(2016, 11, 11),
                                isFavorite = true
                            )
                        )
                    ),
                    onOpenSettings = {},
                    onOpenDetails = { _, _ -> },
                    onOpenCollection = {},
                    onNavigateToSearch = {},
                    onOpenStatistics = {},
                    onRetryStatistics = {},
                    onRetry = {}
                )
            }
        }

        // A merged node concatenates the descriptions of its children, so the decorative poster has
        // to contribute nothing: exactly one description reaches TalkBack.
        composeRule.scrollListTo(hasContentDescription("Open Favorite Movie. Movie · 2016."))
            .assertContentDescriptionEquals("Open Favorite Movie. Movie · 2016.")
    }

    @Test
    fun collectionShortcutKeepsBothLabelAndCountWhenTheLabelHasToWrap() {
        composeRule.setContent {
            BingeeTheme {
                YourBingeeContent(
                    state = ProfileUiState(
                        today = LocalDate.of(2026, 8, 18),
                        isLoading = false,
                        isStatisticsLoading = false,
                        collectionCounts = ProfileCollectionCounts(
                            watchLater = 2,
                            watched = 16,
                            favorites = 6,
                            abandoned = 1
                        )
                    ),
                    onOpenSettings = {},
                    onOpenDetails = { _, _ -> },
                    onOpenCollection = {},
                    onNavigateToSearch = {},
                    onOpenStatistics = {},
                    onRetryStatistics = {},
                    onRetry = {}
                )
            }
        }

        // The label and the count are separate nodes so a wrapping label cannot take the count with it,
        // while the merged description still reads as one phrase.
        composeRule.scrollListTo(hasText("Abandoned")).assertIsDisplayed()
        composeRule.scrollListTo(hasContentDescription("Abandoned · 1")).assertIsDisplayed()
        composeRule.scrollListTo(hasText("Watch later")).assertIsDisplayed()
        composeRule.scrollListTo(hasContentDescription("Watch later · 2")).assertIsDisplayed()
    }

    @Test
    fun watchingProgressFallbackUsesLocaleAwareEpisodePlurals() {
        composeRule.setContent {
            BingeeTheme {
                YourBingeeContent(
                    state = ProfileUiState(
                        today = LocalDate.of(2026, 8, 18),
                        isLoading = false,
                        isStatisticsLoading = false,
                        watching = listOf(
                            ContinueWatchingItem(
                                mediaRef = ExternalMediaRef(MediaSource.TMDB, "200"),
                                mediaType = MediaType.SERIES,
                                title = "Single Episode Series",
                                posterUrl = null,
                                progress = SeriesProgress(0, 1, 0, 1, false),
                                nextEpisode = null,
                                updatedAt = Instant.parse("2026-08-03T12:00:00Z")
                            )
                        )
                    ),
                    onOpenSettings = {},
                    onOpenDetails = { _, _ -> },
                    onOpenCollection = {},
                    onNavigateToSearch = {},
                    onOpenStatistics = {},
                    onRetryStatistics = {},
                    onRetry = {}
                )
            }
        }

        // A one-episode series used to read "0/1 episodes"; the plural resource has to say "episode".
        composeRule.scrollListTo(
            hasContentDescription("Open Single Episode Series. 0 of 1 episode watched. Next position 0/1 episode.")
        ).assertIsDisplayed()
    }

    private fun genres() = listOf(
        GenreStatistic(MediaSource.TMDB, 18, "Drama", 18),
        GenreStatistic(MediaSource.TMDB, 35, "Comedy", 10),
        GenreStatistic(MediaSource.TMDB, 53, "Thriller", 8)
    )
}
