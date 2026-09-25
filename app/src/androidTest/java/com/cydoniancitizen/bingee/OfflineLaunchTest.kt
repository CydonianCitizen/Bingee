package com.cydoniancitizen.bingee

import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.cydoniancitizen.bingee.data.settings.bingeePreferences
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfflineLaunchTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun firstLaunchContinuesOfflineToHome() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        runBlocking {
            context.bingeePreferences.edit { preferences ->
                preferences[booleanPreferencesKey("onboarding_complete")] = false
            }
        }

        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use {
            composeRule.waitUntil(10_000) {
                composeRule.onAllNodesWithText("Continue without TMDB").fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("Continue without TMDB").performScrollTo().performClick()
            composeRule.onNodeWithText("Release calendar").assertIsDisplayed()
        }
    }
}
