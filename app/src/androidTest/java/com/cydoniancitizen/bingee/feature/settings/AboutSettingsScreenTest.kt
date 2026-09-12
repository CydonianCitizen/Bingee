package com.cydoniancitizen.bingee.feature.settings

import android.content.Context
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.cydoniancitizen.bingee.BuildConfig
import com.cydoniancitizen.bingee.R
import com.cydoniancitizen.bingee.core.designsystem.theme.BingeeTheme
import com.cydoniancitizen.bingee.core.result.AppError
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AboutSettingsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun aboutDisplaysDynamicVersionAndOpenSourceInfo() {
        val wentBack = AtomicBoolean(false)
        composeRule.setContent {
            BingeeTheme {
                AboutSettingsContent(
                    state = AboutUiState(installedVersion = BuildConfig.VERSION_NAME),
                    onCheckForUpdates = {},
                    onBack = { wentBack.set(true) }
                )
            }
        }

        composeRule.onNodeWithText("About Bingee").assertIsDisplayed()
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        composeRule.onNodeWithText("Bingee").assertIsDisplayed()
        composeRule.onNodeWithText("Version ${BuildConfig.VERSION_NAME}").assertIsDisplayed()
        composeRule.onNodeWithText("Open Source").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("View on GitHub").performScrollTo().assertIsDisplayed()
        val context = ApplicationProvider.getApplicationContext<Context>()
        composeRule.onNodeWithContentDescription(context.getString(R.string.detail_back))
            .performScrollTo().performClick()
        assertTrue(wentBack.get())
    }

    @Test
    fun updateCheckerShowsUpToDateState() {
        composeRule.setContent {
            BingeeTheme {
                AboutSettingsContent(
                    state = AboutUiState(
                        installedVersion = "1.0.1",
                        updateState = UpdateCheckUiState.UpToDate(installedVersion = "1.0.1")
                    ),
                    onCheckForUpdates = {},
                    onBack = {}
                )
            }
        }

        composeRule.onNodeWithText("Bingee is up to date").performScrollTo().assertIsDisplayed()
        composeRule.onAllNodesWithText("Version 1.0.1")[0].performScrollTo().assertIsDisplayed()
    }

    @Test
    fun updateCheckerShowsUpdateAvailableState() {
        composeRule.setContent {
            BingeeTheme {
                AboutSettingsContent(
                    state = AboutUiState(
                        installedVersion = "1.0.1",
                        updateState = UpdateCheckUiState.UpdateAvailable(
                            installedVersion = "1.0.1",
                            latestVersion = "1.1.0",
                            releaseUrl = "https://github.com/CydonianCitizen/Bingee/releases/tag/v1.1.0"
                        )
                    ),
                    onCheckForUpdates = {},
                    onBack = {}
                )
            }
        }

        composeRule.onNodeWithText("Update available").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Installed version: 1.0.1").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Latest version: 1.1.0").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("View release").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun updateCheckerShowsErrorStateAndTriggersRetry() {
        val retried = AtomicBoolean(false)
        composeRule.setContent {
            BingeeTheme {
                AboutSettingsContent(
                    state = AboutUiState(
                        installedVersion = "1.0.1",
                        updateState = UpdateCheckUiState.Error(AppError.NetworkUnavailable)
                    ),
                    onCheckForUpdates = { retried.set(true) },
                    onBack = {}
                )
            }
        }

        composeRule.onNodeWithText("No network connection. Check your connection and try again.")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("Retry").performScrollTo().performClick()
        assertTrue(retried.get())
    }
}
