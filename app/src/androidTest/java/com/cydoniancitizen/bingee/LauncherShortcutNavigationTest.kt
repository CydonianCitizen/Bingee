package com.cydoniancitizen.bingee

import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.cydoniancitizen.bingee.core.navigation.AppShortcut
import com.cydoniancitizen.bingee.data.settings.bingeePreferences
import com.cydoniancitizen.bingee.testutil.scrollListTo
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotSame
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

    @Test
    fun watchingShortcutStatisticsBackReturnsToCollectionThenHome() {
        verifyCollectionStatistics("watching", R.string.profile_tab_tv_series, useUp = false)
    }

    @Test
    fun watchLaterShortcutStatisticsUpReturnsToCollectionThenHome() {
        verifyCollectionStatistics("watch_later", R.string.profile_tab_watch_later, useUp = true)
    }

    @Test
    fun warmCollectionShortcutStatisticsSurvivesRecreation() {
        // A neutral launch lets ActivityScenario recognize the new Activity's consumed intent.
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
            for (destination in listOf("watching", "watch_later")) {
                scenario.onActivity { activity ->
                    activity.startActivity(shortcut(destination).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP })
                }
                composeRule.onNodeWithText(context.getString(R.string.profile_collection_title)).assertIsDisplayed()
                composeRule.onNodeWithContentDescription(context.getString(R.string.statistics_title)).performClick()
                composeRule.onNode(hasText(context.getString(R.string.statistics_filter_movies)) and isSelectable())
                    .performClick()
                // onNewIntent pauses/resumes while its action differs from the tracked intent.
                // Request real recreation directly rather than using the tracker's stale stage.
                var previous: MainActivity? = null
                scenario.onActivity {
                    previous = it
                    it.recreate()
                }
                composeRule.waitForIdle()
                scenario.onActivity { assertNotSame(previous, it) }
                composeRule.onNode(hasText(context.getString(R.string.statistics_filter_movies)) and isSelectable())
                    .assertIsSelected()
                scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
                composeRule.onNodeWithText(context.getString(R.string.profile_collection_title)).assertIsDisplayed()
                composeRule.onNodeWithContentDescription(context.getString(R.string.detail_back)).performClick()
                composeRule.onNodeWithText(context.getString(R.string.home_title)).assertIsDisplayed()
            }
        }
    }

    @Test
    fun dashboardAndEveryCollectionKeepStatisticsScopeAndBackPath() {
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
            composeRule.onNodeWithText(context.getString(R.string.profile_title_dashboard)).performClick()
            composeRule.scrollListTo(hasText(context.getString(R.string.profile_statistics_view_all))).performClick()
            composeRule.onNode(hasText(context.getString(R.string.statistics_filter_series)) and isSelectable())
                .performClick()
            composeRule.onNodeWithContentDescription(context.getString(R.string.detail_back)).performClick()

            for (label in listOf(
                R.string.profile_collection_watch_later,
                R.string.profile_collection_watched,
                R.string.profile_collection_favorites,
                R.string.profile_collection_abandoned
            )) {
                composeRule.scrollListTo(
                    hasContentDescription(context.getString(label), substring = true)
                ).performClick()
                verifyDashboardCollectionStatistics(scenario)
            }
            composeRule.scrollListTo(hasText(context.getString(R.string.profile_watching_title)))
            composeRule.onAllNodesWithText(context.getString(R.string.profile_view_all)).onFirst().performClick()
            verifyDashboardCollectionStatistics(scenario)
        }
    }

    private fun verifyDashboardCollectionStatistics(scenario: ActivityScenario<MainActivity>) {
        composeRule.onNodeWithContentDescription(context.getString(R.string.statistics_title)).performClick()
        composeRule.onNode(hasText(context.getString(R.string.statistics_filter_series)) and isSelectable())
            .assertIsSelected()
        scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        composeRule.onNodeWithText(context.getString(R.string.profile_collection_title)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(context.getString(R.string.detail_back)).performClick()
        composeRule.scrollListTo(hasText(context.getString(R.string.profile_watching_title))).assertIsDisplayed()
    }

    private fun verifyCollectionStatistics(destination: String, selectedCollectionControl: Int, useUp: Boolean) {
        val scenario = ActivityScenario.launch<MainActivity>(shortcut(destination))
        try {
            composeRule.onNodeWithText(context.getString(R.string.profile_collection_title)).assertIsDisplayed()
            composeRule.onNode(hasText(context.getString(selectedCollectionControl)) and isSelectable())
                .assertIsSelected()
            composeRule.onNodeWithContentDescription(context.getString(R.string.statistics_title)).performClick()
            composeRule.onNodeWithText(context.getString(R.string.statistics_title)).assertIsDisplayed()
            if (useUp) {
                composeRule.onNodeWithContentDescription(context.getString(R.string.detail_back)).performClick()
            } else {
                scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            }
            composeRule.onNodeWithText(context.getString(R.string.profile_collection_title)).assertIsDisplayed()
            composeRule.onNode(hasText(context.getString(selectedCollectionControl)) and isSelectable())
                .assertIsSelected()
            composeRule.onNodeWithContentDescription(context.getString(R.string.detail_back)).performClick()
            composeRule.onNodeWithText(context.getString(R.string.home_title)).assertIsDisplayed()
        } finally {
            // Consumed shortcut intents also stop ActivityScenario from observing DESTROYED.
            scenario.onActivity { it.finish() }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        }
    }

    private fun shortcut(destination: String) = Intent(context, MainActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        action = AppShortcut.ACTION_OPEN
        putExtra(AppShortcut.EXTRA_DESTINATION, destination)
    }
}
