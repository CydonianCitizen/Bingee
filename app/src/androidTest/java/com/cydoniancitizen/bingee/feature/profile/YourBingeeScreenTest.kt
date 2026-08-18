package com.cydoniancitizen.bingee.feature.profile

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.cydoniancitizen.bingee.core.designsystem.theme.BingeeTheme
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.domain.model.GenreStatistic
import com.cydoniancitizen.bingee.domain.model.WatchedStatistics
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicBoolean
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

        composeRule.onNodeWithText("Your statistics").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("42").assertIsDisplayed()
        composeRule.onNodeWithText("Drama").assertIsDisplayed()
        composeRule.onNodeWithText("View all statistics").performClick()

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

        composeRule.onNodeWithText("Not enough data yet").performScrollTo().assertIsDisplayed()
    }

    private fun genres() = listOf(
        GenreStatistic(MediaSource.TMDB, 18, "Drama", 18),
        GenreStatistic(MediaSource.TMDB, 35, "Comedy", 10),
        GenreStatistic(MediaSource.TMDB, 53, "Thriller", 8)
    )
}
