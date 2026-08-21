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
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.isSelectable
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
import com.cydoniancitizen.bingee.domain.model.PersonalRatingStatistics
import com.cydoniancitizen.bingee.domain.model.RatingHistogramBucket
import com.cydoniancitizen.bingee.domain.model.StatisticsMediaScope
import com.cydoniancitizen.bingee.domain.model.TasteStatistics
import com.cydoniancitizen.bingee.domain.model.WatchedStatistics
import com.cydoniancitizen.bingee.domain.model.calculateTasteStatistics
import com.cydoniancitizen.bingee.domain.model.calculateWatchedStatistics
import com.cydoniancitizen.bingee.testutil.STATISTICS_RATINGS_ITEM
import com.cydoniancitizen.bingee.testutil.scrollListTo
import com.cydoniancitizen.bingee.testutil.scrollListToItem
import com.cydoniancitizen.bingee.testutil.scrollRowTo
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
            viewing(
                "movie",
                MediaType.MOVIE,
                listOf(genre("Drama", 18), genre("Comedy", 35), genre("Thriller", 53))
            ),
            viewing(
                "series",
                MediaType.SERIES,
                listOf(genre("Horror", 27), genre("Crime", 80), genre("Romance", 10749))
            )
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

        composeRule.scrollListTo(
            hasContentDescription("Relative genre taste chart. Genres: Drama, Horror, Comedy, Thriller, Crime, Romance")
        ).assertIsDisplayed()

        composeRule.onNodeWithText("Movies").performClick()

        // Radar and complete ranking are driven by the same scoped ranking, so both drop the series
        // genres. The ranking row reports the title count, not the TMDB genre id.
        composeRule.scrollListTo(hasContentDescription("Relative genre taste chart. Genres: Drama, Comedy, Thriller"))
            .assertIsDisplayed()
        composeRule.scrollListTo(hasContentDescription("Drama: 1 title")).assertIsDisplayed()
        composeRule.onNodeWithText("Horror").assertDoesNotExist()
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
        composeRule.scrollListTo(hasText("Viewing time")).assertIsDisplayed()
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
        // The detail renders below the chart, so the list has to scroll to it in its own right.
        composeRule.scrollListTo(hasText("March 2026")).assertIsDisplayed()
    }

    @Test
    fun compactChartsKeepMinimumTargetsAndReachEndItems() {
        val months = (1..12).map { month -> MonthlyViewingData(year = 2026, month = month) }
        val topRated = rated("top", MediaType.MOVIE, 10, LocalDate.of(2024, 3, 1))
        val statistics = WatchedStatistics(
            personalRatingStatistics = PersonalRatingStatistics(
                averageRating = 10.0,
                histogram = (1..10).map { rating ->
                    RatingHistogramBucket(rating, if (rating == 10) 1 else 0)
                },
                ratedTitles = listOf(topRated)
            ),
            monthlyViewing = MonthlyViewingStatistics(
                currentYear = 2026,
                currentMonth = 12,
                selectedYear = 2026,
                availableYears = listOf(2026),
                months = months
            )
        )

        composeRule.setContent {
            var selectedMonth by remember { mutableStateOf<Int?>(null) }
            BingeeTheme {
                Box(Modifier.width(320.dp).fillMaxHeight()) {
                    StatisticsContent(
                        statistics = statistics,
                        tasteStatistics = TasteStatistics(),
                        onScopeChanged = {},
                        selectedMonth = selectedMonth,
                        onMonthSelected = { selectedMonth = it }
                    )
                }
            }
        }

        composeRule.scrollListTo(hasText("Viewing time"))
        composeRule.onNodeWithContentDescription("January 2026, Movies 0m, Series 0m, Total 0m")
            .assertIsDisplayed()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
        // December is the last slot behind the end padding; it must scroll fully into view and,
        // because the current month is December, still be selectable rather than future-disabled.
        composeRule.onNodeWithContentDescription("December 2026, Movies 0m, Series 0m, Total 0m")
            .performScrollTo()
            .assertIsDisplayed()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        composeRule.onNodeWithContentDescription("December 2026, Movies 0m, Series 0m, Total 0m")
            .assertIsSelected()

        composeRule.scrollListTo(hasText("Your ratings"))
        (1..9).forEach { rating ->
            composeRule.onNodeWithContentDescription("Rating $rating, 0 titles").assertIsNotEnabled()
        }
        composeRule.onNodeWithContentDescription("Rating 1, 0 titles")
            .assertIsDisplayed()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithContentDescription("Rating 10, 1 title")
            .performScrollTo()
            .assertIsDisplayed()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        composeRule.onNodeWithContentDescription("Rating 10, 1 title, selected").assertIsSelected()
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

        composeRule.scrollListTo(hasText("Your ratings")).assertIsDisplayed()
        composeRule.scrollListTo(hasText("Average rating")).assertIsDisplayed()
        composeRule.scrollListTo(hasText("Rated titles")).assertIsDisplayed()
        composeRule.onNodeWithText("movie").assertDoesNotExist()
        // "Movies" is also the viewing legend and a rating shelf title, so target the scope control.
        composeRule.scrollListTo(scopeSelector("Movies")).performClick()

        composeRule.scrollListTo(hasContentDescription("Rating 8, 2 titles")).performClick()
        composeRule.onNodeWithContentDescription("Rating 8, 2 titles, selected").assertIsSelected()
        composeRule.scrollListToItem(STATISTICS_RATINGS_ITEM)
        composeRule.scrollListTo(hasText("Movies") and isHeading()).assertIsDisplayed()
        composeRule.scrollListTo(hasText("movie")).assertIsDisplayed()
        composeRule.scrollListTo(hasText("series")).assertIsDisplayed()
        composeRule.scrollListTo(hasText("Movie · 2024")).assertIsDisplayed()
        composeRule.scrollListTo(hasText("Series · 2022")).assertIsDisplayed()

        composeRule.scrollListTo(hasContentDescription("Rating 9, 1 title")).performClick()
        composeRule.onNodeWithContentDescription("Rating 9, 1 title, selected").assertIsSelected()
        composeRule.scrollListToItem(STATISTICS_RATINGS_ITEM)
        composeRule.scrollListTo(hasText("other")).assertIsDisplayed()
        composeRule.onNodeWithText("movie").assertDoesNotExist()
        composeRule.onNodeWithText("series").assertDoesNotExist()

        composeRule.scrollListTo(hasContentDescription("Rating 9, 1 title, selected")).performClick()
        composeRule.onNodeWithText("other").assertDoesNotExist()
    }

    @Test
    fun ratingShelfKeepsEveryMatchingTitleBeyondAPreviewSizedRow() {
        // Poster rows elsewhere in the app show a preview slice; the rating shelf is the full result
        // set, so a selection of more than seven titles must keep the last one reachable.
        val entries = (1..9).map { index ->
            rated("title-$index", MediaType.MOVIE, 8, LocalDate.of(2024, 3, 1))
        }

        composeRule.setContent {
            BingeeTheme {
                StatisticsContent(
                    statistics = calculateWatchedStatistics(entries),
                    tasteStatistics = TasteStatistics(),
                    onScopeChanged = {}
                )
            }
        }

        composeRule.scrollListTo(hasContentDescription("Rating 8, 9 titles")).performClick()
        composeRule.scrollListToItem(STATISTICS_RATINGS_ITEM)
        composeRule.scrollListTo(hasText("title-1")).assertIsDisplayed()
        composeRule.scrollRowTo(hasContentDescription("title-9, Movie · 2024")).assertIsDisplayed()
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

        composeRule.scrollListTo(hasText("Your ratings")).assertIsDisplayed()
        composeRule.scrollListTo(hasText("You haven't rated any titles yet")).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Rating 10, 0 titles").assertExists()
        composeRule.onNodeWithContentDescription("Rating 1, 0 titles").assertIsNotEnabled()
    }

    @Test
    fun ratingPosterUsesExistingDetailsCallback() {
        // Details navigation is TMDB-first and only a positive numeric TMDB id is navigable, so the
        // poster needs a real provider id even though the title stays "movie".
        val entry = rated("movie", MediaType.MOVIE, 8, LocalDate.of(2024, 3, 1))
            .copy(mediaRef = ExternalMediaRef(MediaSource.TMDB, "550"))
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

        composeRule.scrollListTo(hasContentDescription("Rating 8, 1 title")).performClick()
        composeRule.scrollListToItem(STATISTICS_RATINGS_ITEM)
        composeRule.scrollListTo(hasContentDescription("movie, Movie · 2024")).performClick()

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

        composeRule.scrollListTo(hasContentDescription("Rating 8, 1 title")).performClick()
        composeRule.scrollListToItem(STATISTICS_RATINGS_ITEM)
        composeRule.scrollListTo(hasContentDescription("legacy, Movie · 2024")).assertIsNotEnabled()
    }

    /** The scope control is the only "All / Movies / Series" node carrying a selected state. */
    private fun scopeSelector(label: String) = hasText(label) and isSelectable()

    private fun genre(name: String, genreId: Long) = Genre(name, MediaSource.TMDB, genreId)

    private fun viewing(id: String, type: MediaType, genres: List<Genre>) = PersonalViewingEntry(
        mediaRef = ExternalMediaRef(MediaSource.TMDB, id),
        mediaType = type,
        title = id,
        addedAt = Instant.EPOCH,
        inLibrary = false,
        isFavorite = false,
        movieWatchedAt = if (type == MediaType.MOVIE) Instant.EPOCH else null,
        watchedRegularEpisodes = if (type == MediaType.SERIES) 1 else 0,
        genres = genres
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
