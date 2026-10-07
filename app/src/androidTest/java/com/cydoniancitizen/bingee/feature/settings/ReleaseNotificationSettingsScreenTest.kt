package com.cydoniancitizen.bingee.feature.settings

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.cydoniancitizen.bingee.R
import com.cydoniancitizen.bingee.core.designsystem.theme.BingeeTheme
import com.cydoniancitizen.bingee.core.model.NotificationCapabilityStatus
import com.cydoniancitizen.bingee.core.model.ReleaseNotificationLeadTime
import com.cydoniancitizen.bingee.core.model.ReleaseNotificationPreferences
import com.cydoniancitizen.bingee.testutil.TestLocaleRule
import com.cydoniancitizen.bingee.testutil.localizedTestContext
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ReleaseNotificationSettingsScreenTest {
    @get:Rule(order = 0)
    val localeRule = TestLocaleRule()

    private val context get() = localizedTestContext

    @get:Rule(order = 1)
    val composeRule = createComposeRule()

    @Test
    fun disabledSettingsShowLeadCategoriesAndApproximateSchedulingCopy() {
        composeRule.setContent {
            BingeeTheme(darkTheme = true) {
                NotificationSettingsContent(
                    state = ReleaseNotificationSettingsUiState(
                        preferences = ReleaseNotificationPreferences(
                            leadTime = ReleaseNotificationLeadTime.THREE_DAYS
                        )
                    ),
                    onBack = {},
                    onNotificationEnabledChanged = {},
                    onLeadTimeChanged = {},
                    onMovieReleasesChanged = {},
                    onSeasonPremieresChanged = {},
                    onEpisodeAiringsChanged = {},
                    onOpenSystemSettings = {},
                    onDismissError = {}
                )
            }
        }

        listOf(
            R.string.settings_notifications_enable,
            R.string.settings_notifications_approximate,
            R.string.settings_notifications_permission_required,
            R.string.settings_notifications_three_days,
            R.string.settings_notifications_movies,
            R.string.settings_notifications_seasons,
            R.string.settings_notifications_episodes
        ).forEach { label ->
            composeRule.onNodeWithText(context.getString(label)).performScrollTo().assertIsDisplayed()
        }
    }

    @Test
    fun blockedStateOffersSystemSettingsAction() {
        val opened = AtomicBoolean(false)
        composeRule.setContent {
            BingeeTheme {
                NotificationSettingsContent(
                    state = ReleaseNotificationSettingsUiState(
                        capability = NotificationCapabilityStatus.SYSTEM_BLOCKED
                    ),
                    onBack = {},
                    onNotificationEnabledChanged = {},
                    onLeadTimeChanged = {},
                    onMovieReleasesChanged = {},
                    onSeasonPremieresChanged = {},
                    onEpisodeAiringsChanged = {},
                    onOpenSystemSettings = { opened.set(true) },
                    onDismissError = {}
                )
            }
        }
        composeRule.onNodeWithText(
            context.getString(R.string.settings_notifications_open_system)
        ).performScrollTo().performClick()
        assertTrue(opened.get())
    }

    @Test
    fun notificationControlsExposeRolesAndSelectionState() {
        composeRule.setContent {
            BingeeTheme {
                NotificationSettingsContent(
                    state = ReleaseNotificationSettingsUiState(
                        preferences = ReleaseNotificationPreferences(
                            leadTime = ReleaseNotificationLeadTime.THREE_DAYS
                        )
                    ),
                    onBack = {},
                    onNotificationEnabledChanged = {},
                    onLeadTimeChanged = {},
                    onMovieReleasesChanged = {},
                    onSeasonPremieresChanged = {},
                    onEpisodeAiringsChanged = {},
                    onOpenSystemSettings = {},
                    onDismissError = {}
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.settings_notifications_movies))
            .performScrollTo()
            .assert(
                SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch)
            )
        composeRule.onNodeWithText(context.getString(R.string.settings_notifications_three_days))
            .performScrollTo()
            .assert(
                SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)
            )
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
    }
}
