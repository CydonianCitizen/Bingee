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
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.data.notification.NotificationDetailIntent
import com.cydoniancitizen.bingee.data.notification.NotificationNavigationTarget
import com.cydoniancitizen.bingee.data.settings.bingeePreferences
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationActivityNavigationTest {
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
    fun coldStartNavigatesOnceAndBackReturnsToHome() {
        val scenario = ActivityScenario.launch<MainActivity>(
            NotificationDetailIntent.intent(
                context,
                NotificationNavigationTarget(MediaType.MOVIE, 101)
            )
        )
        try {
            composeRule.onNodeWithText("Title details").assertIsDisplayed()
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            composeRule.onNodeWithText("Release calendar").assertIsDisplayed()
        } finally {
            scenario.onActivity { it.finish() }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        }
    }

    @Test
    fun warmTvIntentNavigatesToExistingDetailRoute() {
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
            composeRule.onNodeWithText("Release calendar").assertIsDisplayed()
            scenario.onActivity { activity ->
                InstrumentationRegistry.getInstrumentation().callActivityOnNewIntent(
                    activity,
                    NotificationDetailIntent.intent(
                        context,
                        NotificationNavigationTarget(MediaType.SERIES, 202)
                    )
                )
            }

            composeRule.onNodeWithText("Title details").assertIsDisplayed()
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            composeRule.onNodeWithText("Release calendar").assertIsDisplayed()
        }
    }

    @Test
    fun malformedNotificationIntentLeavesLauncherBehaviorUnchanged() {
        val malformed = Intent(context, MainActivity::class.java).apply {
            action = NotificationDetailIntent.ACTION_OPEN_DETAILS
        }
        ActivityScenario.launch<MainActivity>(malformed).use {
            composeRule.onNodeWithText("Release calendar").assertIsDisplayed()
        }
    }

    @Test
    fun malformedProviderNotificationIntentDoesNotCrashOrOpenDetails() {
        val unsupported = Intent(context, MainActivity::class.java).apply {
            action = NotificationDetailIntent.ACTION_OPEN_DETAILS
            putExtra("notification_source", "IMDB")
            putExtra("notification_media_type", "MOVIE")
            putExtra("notification_external_id", "tt123")
        }
        ActivityScenario.launch<MainActivity>(unsupported).use {
            composeRule.onNodeWithText("Release calendar").assertIsDisplayed()
        }
    }
}
