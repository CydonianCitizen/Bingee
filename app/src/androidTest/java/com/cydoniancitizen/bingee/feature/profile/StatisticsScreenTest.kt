package com.cydoniancitizen.bingee.feature.profile

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.cydoniancitizen.bingee.core.designsystem.theme.BingeeTheme
import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.Genre
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.core.model.PersonalViewingEntry
import com.cydoniancitizen.bingee.domain.model.GenreStatistic
import com.cydoniancitizen.bingee.domain.model.MonthlyViewingData
import com.cydoniancitizen.bingee.domain.model.MonthlyViewingStatistics
import com.cydoniancitizen.bingee.domain.model.StatisticsMediaScope
import com.cydoniancitizen.bingee.domain.model.TasteStatistics
import com.cydoniancitizen.bingee.domain.model.WatchedStatistics
import com.cydoniancitizen.bingee.domain.model.calculateTasteStatistics
import java.time.Instant
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
            .performClick()
        composeRule.onNodeWithText("March 2026").assertIsDisplayed()
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
}
