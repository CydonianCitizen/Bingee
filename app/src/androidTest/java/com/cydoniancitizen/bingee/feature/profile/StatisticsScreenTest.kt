package com.cydoniancitizen.bingee.feature.profile

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.cydoniancitizen.bingee.R
import com.cydoniancitizen.bingee.core.designsystem.theme.BingeeTheme
import com.cydoniancitizen.bingee.domain.model.MediaTypeDistribution
import com.cydoniancitizen.bingee.domain.model.WatchedStatistics
import org.junit.Rule
import org.junit.Test

class StatisticsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()

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

        composeRule.onNodeWithText(
            context.getString(
                R.string.statistics_distribution_item,
                context.getString(R.string.profile_tab_movies),
                1,
                "50%"
            )
        ).assertIsDisplayed()
        composeRule.onNodeWithText(
            context.getString(
                R.string.statistics_distribution_item,
                context.getString(R.string.profile_tab_tv_series),
                1,
                "50%"
            )
        ).assertIsDisplayed()
    }
}
