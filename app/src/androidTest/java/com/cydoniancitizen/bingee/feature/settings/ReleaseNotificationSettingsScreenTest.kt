package com.cydoniancitizen.bingee.feature.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.cydoniancitizen.bingee.core.credential.TmdbCredentialStatus
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
                SettingsContent(
                    state = SettingsUiState(credentialStatus = TmdbCredentialStatus.NotConfigured),
                    onInputChanged = {},
                    onSubmit = {},
                    onRetry = {},
                    onRequestRemoval = {},
                    onDismissRemoval = {},
                    onConfirmRemoval = {},
                    notificationState = ReleaseNotificationSettingsUiState(
                        preferences = ReleaseNotificationPreferences(
                            leadTime = ReleaseNotificationLeadTime.THREE_DAYS
                        )
                    )
                )
            }
        }

        listOf(
            "Release notifications",
            "Permission will be requested only when you enable notifications.",
            "Three days before",
            "Movie releases",
            "Season premieres",
            "Episode airings"
        ).forEach { text ->
            composeRule.onNodeWithText(text).performScrollTo().assertIsDisplayed()
        }
        composeRule.onNodeWithText("Android schedules background checks approximately.", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun blockedStateOffersSystemSettingsAction() {
        val opened = AtomicBoolean(false)
        composeRule.setContent {
            BingeeTheme {
                SettingsContent(
                    state = SettingsUiState(),
                    onInputChanged = {},
                    onSubmit = {},
                    onRetry = {},
                    onRequestRemoval = {},
                    onDismissRemoval = {},
                    onConfirmRemoval = {},
                    notificationState = ReleaseNotificationSettingsUiState(
                        capability = NotificationCapabilityStatus.SYSTEM_BLOCKED
                    ),
                    onOpenNotificationSettings = { opened.set(true) }
                )
            }
        }
        composeRule.onNodeWithText("Open notification settings").performScrollTo().performClick()
        assertTrue(opened.get())
    }
}
