package com.cydoniancitizen.bingee.feature.profile

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.cydoniancitizen.bingee.core.designsystem.theme.BingeeTheme
import com.cydoniancitizen.bingee.domain.model.MediaTypeDistribution
import com.cydoniancitizen.bingee.domain.model.WatchedStatistics
import org.junit.Rule
import org.junit.Test

class StatisticsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun distributionUsesLocalizedMovieAndTvLabels() {
        composeRule.setContent {
            BingeeTheme {
                StatisticsContent(
                    statistics = WatchedStatistics(
                        moviesWatchedCount = 1,
                        mediaTypeDistribution = MediaTypeDistribution(movieCount = 1, tvSeriesCount = 1)
                    ),
                    onOpenDetails = { _, _ -> }
                )
            }
        }

        composeRule.onNodeWithText("Movies: 1 (50%)").assertIsDisplayed()
        composeRule.onNodeWithText("TV Series: 1 (50%)").assertIsDisplayed()
    }
}
