package com.cydoniancitizen.bingee.feature.profile

import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.cydoniancitizen.bingee.R
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

class YourBingeeScreenTest {
    @get:Rule(order = 0)
    val localeRule = TestLocaleRule()

    private val context get() = localizedTestContext

    @get:Rule(order = 1)
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

        composeRule.scrollListTo(hasText(context.getString(R.string.profile_statistics_title))).assertIsDisplayed()
        composeRule.scrollListTo(hasText("42")).assertIsDisplayed()
        composeRule.scrollListTo(hasText("Drama")).assertIsDisplayed()
        composeRule.scrollListTo(hasText(context.getString(R.string.profile_statistics_view_all))).performClick()

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
        composeRule.scrollListTo(hasText(context.getString(R.string.profile_statistics_title))).assertIsDisplayed()
        composeRule.onAllNodesWithText(
            context.getString(R.string.profile_statistics_not_enough_data)
        ).assertCountEquals(2)
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

        composeRule.onNodeWithText(context.getString(R.string.profile_watching_title)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(
            context.resources.getQuantityString(
                R.plurals.profile_watching_accessibility,
                8,
                "Actionable Series",
                3,
                8,
                context.getString(R.string.profile_episode_position, 2, 5)
            )
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

        composeRule.scrollListTo(hasText(context.getString(R.string.profile_favorites_title))).assertIsDisplayed()
        composeRule.scrollListTo(
            hasContentDescription(
                context.getString(
                    R.string.profile_favorite_accessibility,
                    "Favorite Movie",
                    context.getString(R.string.profile_media_type_movie, 2016)
                )
            )
        )
            .performClick()

        assertEquals(ExternalMediaRef(MediaSource.TMDB, "42") to MediaType.MOVIE, opened.get())
    }

    @Test
    fun movieAndSeriesFavoritesWithTheSameProviderIdSurviveStateRestoration() {
        val reference = ExternalMediaRef(MediaSource.TMDB, "42")
        val entries = listOf(MediaType.MOVIE, MediaType.SERIES).map { type ->
            LibraryEntry(
                mediaRef = reference,
                mediaType = type,
                title = "Favorite $type",
                addedAt = Instant.EPOCH,
                isFavorite = true
            )
        }
        val opened = AtomicReference<Pair<ExternalMediaRef, MediaType>>()
        val restorationTester = StateRestorationTester(composeRule)
        restorationTester.setContent {
            BingeeTheme {
                YourBingeeContent(
                    state = ProfileUiState(
                        today = LocalDate.of(2026, 8, 18),
                        isLoading = false,
                        isStatisticsLoading = false,
                        favorites = entries
                    ),
                    onOpenSettings = {},
                    onOpenDetails = { ref, type -> opened.set(ref to type) },
                    onOpenCollection = {},
                    onNavigateToSearch = {},
                    onOpenStatistics = {},
                    onRetryStatistics = {},
                    onRetry = {}
                )
            }
        }

        val context = localizedTestContext
        entries.forEach { entry ->
            val mediaLabel = context.getString(
                if (entry.mediaType == MediaType.MOVIE) {
                    R.string.profile_media_type_movie_no_year
                } else {
                    R.string.profile_media_type_series_no_year
                }
            )
            val description = context.getString(R.string.profile_favorite_accessibility, entry.title, mediaLabel)
            composeRule.scrollListTo(hasContentDescription(description)).performClick()
            assertEquals(reference to entry.mediaType, opened.get())
        }

        restorationTester.emulateSavedInstanceStateRestore()
        entries.forEach { entry ->
            composeRule.scrollListTo(hasText(entry.title)).assertIsDisplayed()
        }
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

        composeRule.onNodeWithContentDescription(context.getString(R.string.nav_settings)).performClick()
        assertTrue(settingsOpened.get())

        // The shortcut carries which collection to open, so the route argument is the assertion.
        composeRule.scrollListTo(
            hasContentDescription(
                context.getString(
                    R.string.profile_collection_shortcut,
                    context.getString(R.string.profile_collection_watch_later),
                    0
                )
            )
        ).performClick()
        assertEquals(ProfileCollectionShortcut.WATCH_LATER, collection.get())

        // Watching and Favorites share the CTA label; Watching is the first section in the list.
        composeRule.onNodeWithText(context.getString(R.string.profile_watching_empty_title)).performScrollTo()
        composeRule.onAllNodesWithText(context.getString(R.string.profile_empty_action_search)).onFirst().performClick()
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
        composeRule.scrollListTo(
            hasContentDescription(
                context.resources.getQuantityString(
                    R.plurals.profile_statistics_podium_accessibility,
                    18,
                    context.getString(R.string.profile_statistics_podium_rank_first),
                    "Drama",
                    18
                )
            )
        ).assertIsDisplayed()
        composeRule.scrollListTo(
            hasContentDescription(
                context.resources.getQuantityString(
                    R.plurals.profile_statistics_podium_accessibility,
                    10,
                    context.getString(R.string.profile_statistics_podium_rank_second),
                    "Comedy",
                    10
                )
            )
        ).assertIsDisplayed()
        composeRule.scrollListTo(
            hasContentDescription(
                context.resources.getQuantityString(
                    R.plurals.profile_statistics_podium_accessibility,
                    8,
                    context.getString(R.string.profile_statistics_podium_rank_third),
                    "Thriller",
                    8
                )
            )
        ).assertIsDisplayed()

        // The visual design stays free of rank labels and medals.
        composeRule.onAllNodesWithText(
            context.getString(R.string.profile_statistics_podium_rank_first)
        ).assertCountEquals(0)
        composeRule.onAllNodesWithText(
            context.getString(R.string.profile_statistics_podium_rank_second)
        ).assertCountEquals(0)
        composeRule.onAllNodesWithText(
            context.getString(R.string.profile_statistics_podium_rank_third)
        ).assertCountEquals(0)
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
        composeRule.scrollListTo(
            hasContentDescription(
                context.getString(
                    R.string.profile_favorite_accessibility,
                    "Favorite Movie",
                    context.getString(R.string.profile_media_type_movie, 2016)
                )
            )
        )
            .assertContentDescriptionEquals(
                context.getString(
                    R.string.profile_favorite_accessibility,
                    "Favorite Movie",
                    context.getString(R.string.profile_media_type_movie, 2016)
                )
            )
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
        composeRule.scrollListTo(hasText(context.getString(R.string.profile_collection_abandoned))).assertIsDisplayed()
        composeRule.scrollListTo(
            hasContentDescription(
                context.getString(
                    R.string.profile_collection_shortcut,
                    context.getString(R.string.profile_collection_abandoned),
                    1
                )
            )
        ).assertIsDisplayed()
        composeRule.scrollListTo(
            hasText(context.getString(R.string.profile_collection_watch_later))
        ).assertIsDisplayed()
        composeRule.scrollListTo(
            hasContentDescription(
                context.getString(
                    R.string.profile_collection_shortcut,
                    context.getString(R.string.profile_collection_watch_later),
                    2
                )
            )
        ).assertIsDisplayed()
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
        assertEquals(
            if (context.resources.configuration.locales[0].language == "it") "0/1 episodio" else "0/1 episode",
            context.resources.getQuantityString(R.plurals.profile_watching_progress_position, 1, 0, 1)
        )
        composeRule.scrollListTo(
            hasContentDescription(
                context.resources.getQuantityString(
                    R.plurals.profile_watching_accessibility,
                    1,
                    "Single Episode Series",
                    0,
                    1,
                    context.resources.getQuantityString(R.plurals.profile_watching_progress_position, 1, 0, 1)
                )
            )
        ).assertIsDisplayed()
    }

    private fun genres() = listOf(
        GenreStatistic(MediaSource.TMDB, 18, "Drama", 18),
        GenreStatistic(MediaSource.TMDB, 35, "Comedy", 10),
        GenreStatistic(MediaSource.TMDB, 53, "Thriller", 8)
    )
}
