package com.cydoniancitizen.bingee.feature.profile

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.cydoniancitizen.bingee.core.designsystem.theme.BingeeTheme
import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.Genre
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.core.model.PersonalRating
import com.cydoniancitizen.bingee.core.model.PersonalViewingEntry
import com.cydoniancitizen.bingee.domain.model.GenreStatistic
import com.cydoniancitizen.bingee.domain.model.MonthlyViewingData
import com.cydoniancitizen.bingee.domain.model.MonthlyViewingStatistics
import com.cydoniancitizen.bingee.domain.model.StatisticsMediaScope
import com.cydoniancitizen.bingee.domain.model.TasteStatistics
import com.cydoniancitizen.bingee.domain.model.WatchedStatistics
import com.cydoniancitizen.bingee.domain.model.calculateTasteStatistics
import com.cydoniancitizen.bingee.domain.model.calculateWatchedStatistics
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class StatisticsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun tasteAndCompleteGenreRankingAreVisible() {
        val genres = listOf(
            GenreStatistic(MediaSource.TMDB, 18, "Drama", 18),
            GenreStatistic(MediaSource.TMDB, 35, "Comedy", 10),
            GenreStatistic(MediaSource.TMDB, 53, "Thriller", 8),
            GenreStatistic(MediaSource.TMDB, 878, "Science Fiction", 6),
            GenreStatistic(MediaSource.TMDB, 27, "Horror", 4),
            GenreStatistic(MediaSource.TMDB, 10749, "Romance", 2),
            GenreStatistic(MediaSource.TMDB, 80, "Crime", 1)
        )

        composeRule.setContent {
            BingeeTheme {
                StatisticsContent(
                    tasteStatistics = TasteStatistics(rankedGenres = genres),
                    onScopeChanged = {}
                )
            }
        }

        composeRule.onNodeWithText("Your taste").assertIsDisplayed()
        composeRule.onNodeWithText("Your genres").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Crime").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithContentDescription(
            "Relative genre taste chart. Genres: Drama, Comedy, Thriller, Science Fiction, Horror, Romance"
        ).assertIsDisplayed()
    }

    @Test
    fun changingScopeUpdatesRadarAndCompleteRankingTogether() {
        val entries = listOf(
            viewing("movie", MediaType.MOVIE, "Drama", 18),
            viewing("series", MediaType.SERIES, "Comedy", 35)
        )

        composeRule.setContent {
            var scope by remember { mutableStateOf(StatisticsMediaScope.ALL) }
            BingeeTheme {
                StatisticsContent(
                    tasteStatistics = calculateTasteStatistics(entries, scope),
                    onScopeChanged = { scope = it }
                )
            }
        }

        composeRule.onNodeWithText("Movies").performClick()
        composeRule.onNodeWithText("Drama").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("18").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Relative genre taste chart. Genres: Drama")
            .assertIsDisplayed()
    }

    @Test
    fun insufficientDataKeepsRadarFrameAndShowsEmptyMessage() {
        composeRule.setContent {
            BingeeTheme {
                StatisticsContent(
                    tasteStatistics = TasteStatistics(
                        rankedGenres = listOf(GenreStatistic(MediaSource.TMDB, 18, "Drama", 1))
                    ),
                    onScopeChanged = {}
                )
            }
        }

        composeRule.onNodeWithText("Not enough data yet").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Relative genre taste chart. Not enough data yet")
            .assertIsDisplayed()
    }

    @Test
    fun viewingChartShowsTwelveMonthsAndSelectedMonthDetail() {
        val months = (1..12).map { month ->
            MonthlyViewingData(
                year = 2026,
                month = month,
                movieMinutes = if (month == 3) 120 else 0,
                seriesMinutes = if (month == 3) 60 else 0
            )
        }
        val statistics = WatchedStatistics(
            moviesWatchedCount = 1,
            tvSeriesCompletedCount = 1,
            episodesWatchedCount = 2,
            movieWatchTimeMinutes = 120,
            seriesWatchTimeMinutes = 60,
            monthlyViewing = MonthlyViewingStatistics(
                currentYear = 2026,
                currentMonth = 8,
                selectedYear = 2026,
                availableYears = listOf(2026, 2025),
                months = months
            )
        )

        composeRule.setContent {
            var selectedMonth by remember { mutableStateOf<Int?>(null) }
            BingeeTheme {
                StatisticsContent(
                    statistics = statistics,
                    tasteStatistics = TasteStatistics(),
                    onScopeChanged = {},
                    selectedMonth = selectedMonth,
                    onMonthSelected = { selectedMonth = it }
                )
            }
        }

        composeRule.onNodeWithText("Your viewing").assertIsDisplayed()
        composeRule.onNodeWithText("Viewing time").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithContentDescription("March 2026, Movies 2h, Series 1h, Total 3h")
            .performScrollTo()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        composeRule.onNodeWithContentDescription("March 2026, Movies 2h, Series 1h, Total 3h")
            .assertIsSelected()
        composeRule.onNodeWithContentDescription("September 2026, Movies 0m, Series 0m, Total 0m")
            .performScrollTo()
            .assertIsNotEnabled()
        composeRule.onNodeWithText("March 2026").assertIsDisplayed()
    }

    @Test
    fun compactChartsKeepMinimumTargetsAndReachEndItems() {
        val months = (1..12).map { month -> MonthlyViewingData(year = 2026, month = month) }
        val statistics = WatchedStatistics(
            monthlyViewing = MonthlyViewingStatistics(
                currentYear = 2026,
                currentMonth = 12,
                selectedYear = 2026,
                availableYears = listOf(2026),
                months = months
            )
        )

        composeRule.setContent {
            BingeeTheme {
                Box(Modifier.width(320.dp).fillMaxHeight()) {
                    StatisticsContent(
                        statistics = statistics,
                        tasteStatistics = TasteStatistics(),
                        onScopeChanged = {}
                    )
                }
            }
        }

        composeRule.onNodeWithText("Viewing time").performScrollTo()
        composeRule.onNodeWithContentDescription("January 2026, Movies 0m, Series 0m, Total 0m")
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithContentDescription("December 2026, Movies 0m, Series 0m, Total 0m")
            .performScrollTo()
            .assertIsDisplayed()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)

        composeRule.onNodeWithText("Your ratings").performScrollTo()
        (1..10).forEach { rating ->
            composeRule.onNodeWithContentDescription("Rating $rating, 0 titles").assertIsNotEnabled()
        }
        composeRule.onNodeWithContentDescription("Rating 1, 0 titles")
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithContentDescription("Rating 10, 0 titles")
            .performScrollTo()
            .assertIsDisplayed()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun ratingsShowCombinedSummaryAndExpandAllMatchingShelves() {
        val entries = listOf(
            rated("movie", MediaType.MOVIE, 8, LocalDate.of(2024, 3, 1)),
            rated("series", MediaType.SERIES, 8, LocalDate.of(2022, 9, 1)),
            rated("other", MediaType.MOVIE, 9, LocalDate.of(2025, 1, 1))
        )
        val statistics = calculateWatchedStatistics(entries)

        composeRule.setContent {
            var scope by remember { mutableStateOf(StatisticsMediaScope.ALL) }
            BingeeTheme {
                StatisticsContent(
                    statistics = statistics,
                    tasteStatistics = calculateTasteStatistics(entries, scope),
                    onScopeChanged = { scope = it }
                )
            }
        }

        composeRule.onNodeWithText("Your ratings").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Average rating").assertIsDisplayed()
        composeRule.onNodeWithText("Rated titles").assertIsDisplayed()
        composeRule.onNodeWithText("movie").assertDoesNotExist()
        composeRule.onNodeWithText("Movies").performClick()

        composeRule.onNodeWithContentDescription("Rating 8, 2 titles")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithContentDescription("Rating 8, 2 titles, selected").assertIsSelected()
        composeRule.onNodeWithText("Movies").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("movie").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("series").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Movie · 2024").assertIsDisplayed()
        composeRule.onNodeWithText("Series · 2022").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Rating 9, 1 title")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithContentDescription("Rating 9, 1 title, selected").assertIsSelected()
        composeRule.onNodeWithText("other").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("movie").assertDoesNotExist()
        composeRule.onNodeWithText("series").assertDoesNotExist()

        composeRule.onNodeWithContentDescription("Rating 9, 1 title, selected")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithText("other").assertDoesNotExist()
    }

    @Test
    fun ratingChartKeepsAllBucketsAndEmptyState() {
        composeRule.setContent {
            BingeeTheme {
                StatisticsContent(
                    tasteStatistics = TasteStatistics(),
                    onScopeChanged = {}
                )
            }
        }

        composeRule.onNodeWithText("Your ratings").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("You haven't rated any titles yet").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Rating 10, 0 titles").assertExists()
        composeRule.onNodeWithContentDescription("Rating 1, 0 titles").assertIsNotEnabled()
    }

    @Test
    fun ratingPosterUsesExistingDetailsCallback() {
        val entry = rated("movie", MediaType.MOVIE, 8, LocalDate.of(2024, 3, 1))
        var opened: Pair<ExternalMediaRef, MediaType>? = null

        composeRule.setContent {
            BingeeTheme {
                StatisticsContent(
                    statistics = calculateWatchedStatistics(listOf(entry)),
                    tasteStatistics = TasteStatistics(),
                    onScopeChanged = {},
                    onOpenDetails = { reference, mediaType -> opened = reference to mediaType }
                )
            }
        }

        composeRule.onNodeWithContentDescription("Rating 8, 1 title")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithContentDescription("movie, Movie · 2024")
            .performScrollTo()
            .performClick()

        assertEquals(entry.mediaRef, opened?.first)
        assertEquals(MediaType.MOVIE, opened?.second)
    }

    @Test
    fun imdbOnlyRatingPosterIsNotClickable() {
        val entry = rated("legacy", MediaType.MOVIE, 8, LocalDate.of(2024, 3, 1)).copy(
            mediaRef = ExternalMediaRef(MediaSource.IMDB, "tt1234567")
        )

        composeRule.setContent {
            BingeeTheme {
                StatisticsContent(
                    statistics = calculateWatchedStatistics(listOf(entry)),
                    tasteStatistics = TasteStatistics(),
                    onScopeChanged = {}
                )
            }
        }

        composeRule.onNodeWithContentDescription("Rating 8, 1 title")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithContentDescription("legacy, Movie · 2024")
            .performScrollTo()
            .assertIsNotEnabled()
    }

    private fun viewing(id: String, type: MediaType, genreName: String, genreId: Long) = PersonalViewingEntry(
        mediaRef = ExternalMediaRef(MediaSource.TMDB, id),
        mediaType = type,
        title = id,
        addedAt = Instant.EPOCH,
        inLibrary = false,
        isFavorite = false,
        movieWatchedAt = if (type == MediaType.MOVIE) Instant.EPOCH else null,
        watchedRegularEpisodes = if (type == MediaType.SERIES) 1 else 0,
        genres = listOf(Genre(genreName, MediaSource.TMDB, genreId))
    )

    private fun rated(id: String, type: MediaType, rating: Int, releaseDate: LocalDate) = PersonalViewingEntry(
        mediaRef = ExternalMediaRef(MediaSource.TMDB, id),
        mediaType = type,
        title = id,
        addedAt = Instant.EPOCH,
        inLibrary = false,
        isFavorite = false,
        personalRating = PersonalRating(rating),
        personalRatingUpdatedAt = Instant.parse("2026-08-01T00:00:00Z"),
        movieWatchedAt = if (type == MediaType.MOVIE) Instant.EPOCH else null,
        watchedRegularEpisodes = if (type == MediaType.SERIES) 1 else 0,
        releaseDate = releaseDate
    )
}
