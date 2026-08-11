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
import com.cydoniancitizen.bingee.core.designsystem.theme.BingeeTheme
import com.cydoniancitizen.bingee.core.model.NotificationCapabilityStatus
import com.cydoniancitizen.bingee.core.model.ReleaseNotificationLeadTime
import com.cydoniancitizen.bingee.core.model.ReleaseNotificationPreferences
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ReleaseNotificationSettingsScreenTest {
    @get:Rule
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
            "Enable release notifications",
            "Android schedules background checks approximately.",
            "Permission will be requested only when you enable notifications.",
            "Three days before",
            "Movie releases",
            "Season premieres",
            "Episode airings"
        ).forEach { text ->
            composeRule.onNodeWithText(text, substring = true).performScrollTo().assertIsDisplayed()
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
        composeRule.onNodeWithText("Open notification settings").performScrollTo().performClick()
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

        composeRule.onNodeWithText("Movie releases")
            .performScrollTo()
            .assert(
                SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch)
            )
        composeRule.onNodeWithText("Three days before")
            .performScrollTo()
            .assert(
                SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)
            )
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
    }
}
