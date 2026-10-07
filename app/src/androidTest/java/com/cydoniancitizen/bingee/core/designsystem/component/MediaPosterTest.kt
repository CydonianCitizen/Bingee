package com.cydoniancitizen.bingee.core.designsystem.component

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import com.cydoniancitizen.bingee.R
import com.cydoniancitizen.bingee.core.designsystem.theme.BingeeTheme
import com.cydoniancitizen.bingee.testutil.TestLocaleRule
import com.cydoniancitizen.bingee.testutil.localizedTestContext
import org.junit.Rule
import org.junit.Test

class MediaPosterTest {
    @get:Rule(order = 0)
    val localeRule = TestLocaleRule()

    private val context get() = localizedTestContext

    @get:Rule(order = 1)
    val composeRule = createComposeRule()

    @Test
    fun standalonePosterDescribesItsTitle() {
        composeRule.setContent {
            BingeeTheme {
                MediaPoster(title = "Arrival", posterUrl = null)
            }
        }

        composeRule.onNodeWithContentDescription(
            context.getString(R.string.poster_missing, "Arrival")
        ).assertIsDisplayed()
    }

    @Test
    fun decorativePosterContributesNoDescription() {
        composeRule.setContent {
            BingeeTheme {
                MediaPoster(title = "Arrival", posterUrl = null, contentDescription = null)
            }
        }

        composeRule.onAllNodesWithContentDescription(
            context.getString(R.string.poster_missing, "Arrival")
        ).assertCountEquals(0)
    }
}
