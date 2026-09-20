package com.cydoniancitizen.bingee

import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.cydoniancitizen.bingee.core.navigation.AppShortcut
import com.cydoniancitizen.bingee.data.settings.bingeePreferences
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LauncherShortcutNavigationTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun completeOnboarding() {
        runBlocking {
            context.bingeePreferences.edit { preferences ->
                preferences[booleanPreferencesKey("onboarding_complete")] = true
            }
        }
    }

    @Test
    fun searchShortcutOpensSearchAndBackReturnsToHome() {
        val scenario = ActivityScenario.launch<MainActivity>(shortcut("search"))
        try {
            composeRule.onNodeWithText("Search TMDB").assertIsDisplayed()
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            composeRule.onNodeWithText("Release calendar").assertIsDisplayed()
        } finally {
            scenario.onActivity { it.finish() }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        }
    }

    @Test
    fun unknownShortcutDestinationLeavesLauncherBehaviorUnchanged() {
        ActivityScenario.launch<MainActivity>(shortcut("nowhere")).use {
            composeRule.onNodeWithText("Release calendar").assertIsDisplayed()
        }
    }

    private fun shortcut(destination: String) = Intent(context, MainActivity::class.java).apply {
        action = AppShortcut.ACTION_OPEN
        putExtra(AppShortcut.EXTRA_DESTINATION, destination)
    }
}
