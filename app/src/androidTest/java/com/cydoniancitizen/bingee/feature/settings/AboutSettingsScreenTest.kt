package com.cydoniancitizen.bingee.feature.settings

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
import com.cydoniancitizen.bingee.BuildConfig
import com.cydoniancitizen.bingee.R
import com.cydoniancitizen.bingee.core.designsystem.theme.BingeeTheme
import com.cydoniancitizen.bingee.core.result.AppError
import com.cydoniancitizen.bingee.testutil.TestLocaleRule
import com.cydoniancitizen.bingee.testutil.localizedTestContext
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AboutSettingsScreenTest {
    @get:Rule(order = 0)
    val localeRule = TestLocaleRule()

    private val context get() = localizedTestContext

    @get:Rule(order = 1)
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

        composeRule.onNodeWithText(context.getString(R.string.settings_nav_about)).assertIsDisplayed()
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        composeRule.onNodeWithText(context.getString(R.string.app_name)).assertIsDisplayed()
        composeRule.onNodeWithText(
            context.getString(R.string.about_version_label, BuildConfig.VERSION_NAME)
        ).assertIsDisplayed()
        composeRule.onNodeWithText(
            context.getString(R.string.settings_open_source_title)
        ).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(
            context.getString(R.string.settings_open_source_github_action)
        ).performScrollTo().assertIsDisplayed()
        val context = localizedTestContext
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

        composeRule.onNodeWithText(context.getString(R.string.update_up_to_date)).performScrollTo().assertIsDisplayed()
        composeRule.onAllNodesWithText(
            context.getString(R.string.about_version_label, "1.0.1")
        )[0].performScrollTo().assertIsDisplayed()
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

        composeRule.onNodeWithText(context.getString(R.string.update_available)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(
            context.getString(R.string.update_current_version, "1.0.1")
        ).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(
            context.getString(R.string.update_latest_version, "1.1.0")
        ).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(
            context.getString(R.string.update_view_release)
        ).performScrollTo().assertIsDisplayed()
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

        composeRule.onNodeWithText(context.getString(R.string.update_error_network))
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.action_retry)).performScrollTo().performClick()
        assertTrue(retried.get())
    }
}
