package com.cydoniancitizen.bingee.core.designsystem.component

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import com.cydoniancitizen.bingee.core.designsystem.theme.BingeeTheme
import org.junit.Rule
import org.junit.Test

class MediaPosterTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun standalonePosterDescribesItsTitle() {
        composeRule.setContent {
            BingeeTheme {
                MediaPoster(title = "Arrival", posterUrl = null)
            }
        }

        composeRule.onNodeWithContentDescription("No poster available for Arrival").assertIsDisplayed()
    }

    @Test
    fun decorativePosterContributesNoDescription() {
        composeRule.setContent {
            BingeeTheme {
                MediaPoster(title = "Arrival", posterUrl = null, contentDescription = null)
            }
        }

        composeRule.onAllNodesWithContentDescription("No poster available for Arrival").assertCountEquals(0)
    }
}
