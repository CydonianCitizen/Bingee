package com.cydoniancitizen.bingee

import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.core.navigation.DetailRouteArgs
import com.cydoniancitizen.bingee.data.library.local.ALL_MIGRATIONS
import com.cydoniancitizen.bingee.data.library.local.BingeeDatabase
import com.cydoniancitizen.bingee.data.library.local.MediaDetailsEntity
import com.cydoniancitizen.bingee.data.library.local.MediaEntity
import com.cydoniancitizen.bingee.data.notification.NotificationDetailIntent
import com.cydoniancitizen.bingee.data.settings.bingeePreferences
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
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
                DetailRouteArgs(MediaType.MOVIE, 101)
            )
        )
        try {
            composeRule.onNodeWithText(context.getString(R.string.detail_screen_title)).assertIsDisplayed()
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            composeRule.onNodeWithText(context.getString(R.string.home_title)).assertIsDisplayed()
        } finally {
            scenario.onActivity { it.finish() }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        }
    }

    @Test
    fun warmTvIntentNavigatesToExistingDetailRoute() {
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
            composeRule.onNodeWithText(context.getString(R.string.home_title)).assertIsDisplayed()
            scenario.onActivity { activity ->
                InstrumentationRegistry.getInstrumentation().callActivityOnNewIntent(
                    activity,
                    NotificationDetailIntent.intent(
                        context,
                        DetailRouteArgs(MediaType.SERIES, 202)
                    )
                )
            }

            composeRule.onNodeWithText(context.getString(R.string.detail_screen_title)).assertIsDisplayed()
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            composeRule.onNodeWithText(context.getString(R.string.home_title)).assertIsDisplayed()
        }
    }

    @Test
    fun warmDetailsIntentChangesQualifiedIdentityAndBackRestoresPreviousTitles() = withCachedTitles {
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
            composeRule.onNodeWithText(context.getString(R.string.home_title)).assertIsDisplayed()
            deliver(scenario, MediaType.MOVIE, 990001)
            assertTitle("Navigation movie one")
            deliver(scenario, MediaType.SERIES, 990001)
            assertTitle("Navigation series")
            deliver(scenario, MediaType.MOVIE, 990002)
            assertTitle("Navigation movie two")

            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            assertTitle("Navigation series")
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            assertTitle("Navigation movie one")
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            composeRule.onNodeWithText(context.getString(R.string.home_title)).assertIsDisplayed()
        }
    }

    @Test
    fun repeatedSameTitleIntentDoesNotAddBackEntriesAfterRecreation() = withCachedTitles {
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
            composeRule.onNodeWithText(context.getString(R.string.home_title)).assertIsDisplayed()
            deliver(scenario, MediaType.MOVIE, 990001)
            assertTitle("Navigation movie one")
            deliver(scenario, MediaType.MOVIE, 990001)
            assertTitle("Navigation movie one")
            // The scenario tracker can retain a stale lifecycle stage after an intent is consumed.
            var previous: MainActivity? = null
            scenario.onActivity {
                previous = it
                it.recreate()
            }
            composeRule.waitForIdle()
            scenario.onActivity { assertNotSame(previous, it) }
            assertTitle("Navigation movie one")
            deliver(scenario, MediaType.MOVIE, 990001)
            assertTitle("Navigation movie one")
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            composeRule.onNodeWithText(context.getString(R.string.home_title)).assertIsDisplayed()
        }
    }

    @Test
    fun malformedNotificationIntentLeavesLauncherBehaviorUnchanged() {
        val malformed = Intent(context, MainActivity::class.java).apply {
            action = NotificationDetailIntent.ACTION_OPEN_DETAILS
        }
        ActivityScenario.launch<MainActivity>(malformed).use {
            composeRule.onNodeWithText(context.getString(R.string.home_title)).assertIsDisplayed()
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
            composeRule.onNodeWithText(context.getString(R.string.home_title)).assertIsDisplayed()
        }
    }

    private fun assertTitle(title: String) {
        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty() }
        val nodes = composeRule.onAllNodesWithText(title)
        assertTrue(
            "Expected visible title: $title",
            nodes.fetchSemanticsNodes().indices.any {
                nodes[it].isDisplayed()
            }
        )
    }

    private fun deliver(scenario: ActivityScenario<MainActivity>, type: MediaType, id: Long) {
        scenario.onActivity { activity ->
            InstrumentationRegistry.getInstrumentation().callActivityOnNewIntent(
                activity,
                NotificationDetailIntent.intent(context, DetailRouteArgs(type, id))
            )
        }
        // The title may already be visible on repeated delivery; wait for this intent to be consumed.
        composeRule.waitUntil(5_000) {
            var consumed = false
            scenario.onActivity { consumed = it.intent.action != NotificationDetailIntent.ACTION_OPEN_DETAILS }
            consumed
        }
    }

    private fun withCachedTitles(test: () -> Unit) {
        val database = Room.databaseBuilder(context, BingeeDatabase::class.java, BingeeDatabase.DATABASE_NAME)
            .addMigrations(*ALL_MIGRATIONS)
            .build()
        val localIds = mutableListOf<Long>()
        try {
            runBlocking {
                val now = Instant.now()
                listOf(
                    Triple(MediaType.MOVIE, 990001L, "Navigation movie one"),
                    Triple(MediaType.SERIES, 990001L, "Navigation series"),
                    Triple(MediaType.MOVIE, 990002L, "Navigation movie two")
                ).forEach { (type, id, title) ->
                    database.detailsDao().storeDetails(
                        candidate = MediaEntity(
                            mediaType = type,
                            title = title,
                            originalTitle = null,
                            overview = "Cached fixture",
                            posterUrl = null,
                            releaseDate = null,
                            createdAt = now,
                            metadataUpdatedAt = now
                        ),
                        source = MediaSource.TMDB,
                        externalId = id.toString(),
                        details = MediaDetailsEntity(
                            localMediaId = 0, backdropUrl = null, productionStatus = "RELEASED",
                            originalLanguage = "en", runtimeMinutes = 100, episodeRuntimeMinutes = null,
                            numberOfSeasons = 0, numberOfEpisodes = 0, detailsFetchedAt = now, language = "en-US"
                        ),
                        genres = emptyList()
                    )
                    localIds += database.detailsDao().getCachedDetails(MediaSource.TMDB, type, id.toString())!!
                        .media.localMediaId
                }
            }
            test()
        } finally {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            localIds.forEach { id ->
                database.openHelper.writableDatabase.execSQL(
                    "DELETE FROM media_entries WHERE local_media_id = ?",
                    arrayOf(id)
                )
            }
            database.close()
        }
    }
}
