package com.cydoniancitizen.bingee.core.designsystem.component

import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.cydoniancitizen.bingee.core.designsystem.theme.BingeeTheme
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class StateComponentsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun loadingStateExposesProgressDescription() {
        composeRule.setContent {
            BingeeTheme {
                LoadingState(message = "Loading library")
            }
        }

        composeRule
            .onNode(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    "Loading library"
                )
            ).assertIsDisplayed()
    }

    @Test
    fun emptyStateDisplaysProvidedContent() {
        composeRule.setContent {
            BingeeTheme {
                EmptyState(
                    title = "Nothing saved",
                    body = "Add a title to begin."
                )
            }
        }

        composeRule.onNodeWithText("Nothing saved").assertIsDisplayed()
        composeRule.onNodeWithText("Add a title to begin.").assertIsDisplayed()
    }

    @Test
    fun errorStateInvokesRetryAction() {
        val retried = AtomicBoolean(false)
        composeRule.setContent {
            BingeeTheme {
                ErrorState(
                    title = "Unable to load",
                    message = "Try again.",
                    retryLabel = "Retry",
                    onRetry = { retried.set(true) }
                )
            }
        }

        composeRule.onNodeWithText("Retry").performClick()

        assertTrue(retried.get())
    }

    @Test
    fun offlineBannerUsesPoliteLiveRegion() {
        composeRule.setContent {
            BingeeTheme {
                OfflineBanner(message = "Offline")
            }
        }

        composeRule
            .onNode(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.LiveRegion,
                    LiveRegionMode.Polite
                )
            ).assertIsDisplayed()
    }
}
