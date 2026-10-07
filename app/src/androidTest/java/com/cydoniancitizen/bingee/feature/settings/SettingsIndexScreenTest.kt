package com.cydoniancitizen.bingee.feature.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.cydoniancitizen.bingee.R
import com.cydoniancitizen.bingee.core.designsystem.theme.BingeeTheme
import com.cydoniancitizen.bingee.testutil.TestLocaleRule
import com.cydoniancitizen.bingee.testutil.localizedTestContext
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SettingsIndexScreenTest {
    @get:Rule(order = 0)
    val localeRule = TestLocaleRule()

    private val context get() = localizedTestContext

    @get:Rule(order = 1)
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
                    onBack = {},
                    onNavigateToAppearance = { appearanceClicked.set(true) },
                    onNavigateToNotifications = { notificationsClicked.set(true) },
                    onNavigateToDataBackup = { dataBackupClicked.set(true) },
                    onNavigateToPrivacy = { privacyClicked.set(true) },
                    onNavigateToAbout = { aboutClicked.set(true) }
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.nav_settings)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.settings_nav_appearance)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.settings_nav_notifications)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.settings_nav_data_backup)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.privacy_title)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.settings_nav_about)).assertIsDisplayed()

        composeRule.onNodeWithText(context.getString(R.string.settings_nav_appearance)).performClick()
        assertTrue(appearanceClicked.get())

        composeRule.onNodeWithText(context.getString(R.string.settings_nav_notifications)).performClick()
        assertTrue(notificationsClicked.get())

        composeRule.onNodeWithText(context.getString(R.string.settings_nav_data_backup)).performClick()
        assertTrue(dataBackupClicked.get())

        composeRule.onNodeWithText(context.getString(R.string.privacy_title)).performClick()
        assertTrue(privacyClicked.get())

        composeRule.onNodeWithText(context.getString(R.string.settings_nav_about)).performClick()
        assertTrue(aboutClicked.get())
    }

    @Test
    fun exposesVisibleUpNavigationThatCallsBack() {
        val back = AtomicBoolean(false)

        composeRule.setContent {
            BingeeTheme {
                SettingsIndexScreen(
                    onBack = { back.set(true) },
                    onNavigateToAppearance = {},
                    onNavigateToNotifications = {},
                    onNavigateToDataBackup = {},
                    onNavigateToPrivacy = {},
                    onNavigateToAbout = {}
                )
            }
        }

        // Settings is reached from Your Bingee, so system Back alone is not the whole affordance.
        composeRule.onNodeWithContentDescription(context.getString(R.string.detail_back)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(context.getString(R.string.detail_back)).performClick()

        assertTrue(back.get())
    }
}
