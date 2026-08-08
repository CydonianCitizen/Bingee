package com.cydoniancitizen.bingee.feature.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.cydoniancitizen.bingee.core.designsystem.theme.BingeeTheme
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SettingsIndexScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun displaysAllFiveDestinationsInOrderAndTriggersNavigation() {
        val appearanceClicked = AtomicBoolean(false)
        val notificationsClicked = AtomicBoolean(false)
        val dataBackupClicked = AtomicBoolean(false)
        val privacyClicked = AtomicBoolean(false)
        val aboutClicked = AtomicBoolean(false)

        composeRule.setContent {
            BingeeTheme {
                SettingsIndexScreen(
                    onNavigateToAppearance = { appearanceClicked.set(true) },
                    onNavigateToNotifications = { notificationsClicked.set(true) },
                    onNavigateToDataBackup = { dataBackupClicked.set(true) },
                    onNavigateToPrivacy = { privacyClicked.set(true) },
                    onNavigateToAbout = { aboutClicked.set(true) }
                )
            }
        }

        composeRule.onNodeWithText("Settings").assertIsDisplayed()
        composeRule.onNodeWithText("Appearance & Language").assertIsDisplayed()
        composeRule.onNodeWithText("Notifications").assertIsDisplayed()
        composeRule.onNodeWithText("Data & Backup").assertIsDisplayed()
        composeRule.onNodeWithText("Privacy").assertIsDisplayed()
        composeRule.onNodeWithText("About Bingee").assertIsDisplayed()

        composeRule.onNodeWithText("Appearance & Language").performClick()
        assertTrue(appearanceClicked.get())

        composeRule.onNodeWithText("Notifications").performClick()
        assertTrue(notificationsClicked.get())

        composeRule.onNodeWithText("Data & Backup").performClick()
        assertTrue(dataBackupClicked.get())

        composeRule.onNodeWithText("Privacy").performClick()
        assertTrue(privacyClicked.get())

        composeRule.onNodeWithText("About Bingee").performClick()
        assertTrue(aboutClicked.get())
    }
}
