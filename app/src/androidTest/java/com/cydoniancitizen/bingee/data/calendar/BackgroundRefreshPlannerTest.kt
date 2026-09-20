package com.cydoniancitizen.bingee.data.calendar

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cydoniancitizen.bingee.core.model.BackgroundRefreshTarget
import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.core.result.AppResult
import com.cydoniancitizen.bingee.data.library.local.BingeeDatabase
import com.cydoniancitizen.bingee.data.settings.bingeePreferences
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackgroundRefreshPlannerTest {
    private lateinit var database: BingeeDatabase
    private lateinit var planner: RoomBackgroundRefreshPlanner

    private var now = Instant.parse("2026-08-01T03:00:00Z")

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            BingeeDatabase::class.java
        ).build()
        val clock = object : Clock() {
            override fun getZone(): ZoneId = ZoneOffset.UTC
            override fun withZone(zone: ZoneId?): Clock = this
            override fun instant(): Instant = now
        }
        planner =
            RoomBackgroundRefreshPlanner(database.libraryDao(), clock, ApplicationProvider.getApplicationContext())
    }

    @After
    fun closeDatabase() = database.close()

    @Test
    fun emptyLibraryReturnsEmptyPlan() = runBlocking {
        val result = planner.plan(20)
        assertTrue(result is AppResult.Success && result.value.isEmpty())
    }

    @Test
    fun seasonAttemptsSurvivePlannerRecreationWithoutSuccessfulRefresh() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val reference = ExternalMediaRef(MediaSource.TMDB, "12345")
        val key = intPreferencesKey("background_season_cursor_TMDB_12345")
        context.bingeePreferences.edit { it.remove(key) }
        try {
            assertEquals(AppResult.Success(listOf(0, 1)), planner.claimSeasons(reference, (0..5).toList()))
            val recreated = RoomBackgroundRefreshPlanner(database.libraryDao(), Clock.systemUTC(), context)
            assertEquals(AppResult.Success(listOf(2, 3)), recreated.claimSeasons(reference, (0..5).toList()))
            assertEquals(AppResult.Success(listOf(4, 5)), planner.claimSeasons(reference, (0..5).toList()))
        } finally {
            context.bingeePreferences.edit { it.remove(key) }
        }
    }

    @Test
    fun boundedPlanPrioritizesNeverThenOldestAndRotatesAttemptedTitlesBehind() = runBlocking {
        insertMedia(1, "z", MediaType.MOVIE, active = true, fetchedAt = null)
        insertMedia(2, "a", MediaType.SERIES, active = true, fetchedAt = null)
        insertMedia(3, "old", MediaType.MOVIE, active = true, fetchedAt = "2026-01-01T00:00:00Z")
        insertMedia(4, "fresh", MediaType.SERIES, active = true, fetchedAt = "2026-07-01T00:00:00Z")
        insertMedia(5, "inactive", MediaType.MOVIE, active = false, fetchedAt = null)

        val first = plan(3)
        assertEquals(listOf("a", "z", "old"), first.map { it.mediaRef.externalId })
        assertEquals(listOf(MediaType.SERIES, MediaType.MOVIE, MediaType.MOVIE), first.map { it.mediaType })

        sql(
            "INSERT INTO media_details(local_media_id, backdrop_url, production_status, original_language, " +
                "runtime_minutes, episode_runtime_minutes, number_of_seasons, number_of_episodes, " +
                "details_fetched_at) " +
                "VALUES(1, NULL, 'CURRENT', NULL, NULL, NULL, NULL, NULL, '2026-08-04T00:00:00Z')," +
                "(2, NULL, 'CURRENT', NULL, NULL, NULL, NULL, NULL, '2026-08-04T00:00:00Z')"
        )
        // The unattempted title leads; the previous batch follows by its success age.
        val rotated = plan(3)
        assertEquals(listOf("fresh", "old", "a"), rotated.map { it.mediaRef.externalId })
    }

    @Test
    fun titlesThatKeepFailingRotateBehindAValidTitle() = runBlocking {
        val failing = (1..20).map { "fail-%02d".format(it) }
        failing.forEachIndexed { index, externalId ->
            insertMedia(index + 1L, externalId, MediaType.MOVIE, active = true, fetchedAt = null)
        }
        insertMedia(21, "valid", MediaType.MOVIE, active = true, fetchedAt = null)

        assertEquals(failing, plan(20).map { it.mediaRef.externalId })
        // Nothing was stored for the failed batch, yet the attempt alone moves it behind the valid title,
        // so a batch lost to 404s, network errors, cancellation, or retries cannot block the rest.
        assertEquals(0, count("SELECT COUNT(*) FROM media_details"))
        assertEquals(listOf("valid") + failing.take(19), plan(20).map { it.mediaRef.externalId })

        sql(
            "INSERT INTO media_details(local_media_id, backdrop_url, production_status, original_language, " +
                "runtime_minutes, episode_runtime_minutes, number_of_seasons, number_of_episodes, " +
                "details_fetched_at) VALUES(21, NULL, 'CURRENT', NULL, NULL, NULL, NULL, NULL, '$now')"
        )
        assertEquals(listOf("fail-20") + failing.take(19), plan(20).map { it.mediaRef.externalId })
        assertEquals(listOf("valid") + failing.take(19), plan(20).map { it.mediaRef.externalId })
    }

    /** One background run: a day later, the planner claims the next batch. */
    private suspend fun plan(limit: Int): List<BackgroundRefreshTarget> {
        now = now.plus(Duration.ofDays(1))
        return (planner.plan(limit) as AppResult.Success).value
    }

    private fun count(query: String): Int = database.openHelper.readableDatabase.query(query).use {
        it.moveToFirst()
        it.getInt(0)
    }

    private fun insertMedia(id: Long, externalId: String, type: MediaType, active: Boolean, fetchedAt: String?) {
        sql(
            "INSERT INTO media_entries(local_media_id, media_type, title, original_title, overview, poster_url, " +
                "release_date, created_at, metadata_updated_at, is_favorite) VALUES(" +
                "$id, '${type.name}', 'Fixture $id', NULL, NULL, NULL, NULL, " +
                "'2025-01-01T00:00:00Z', '2025-01-01T00:00:00Z', 0)"
        )
        sql(
            "INSERT INTO external_refs(local_media_id, source, media_type, external_id) " +
                "SELECT $id, 'TMDB', media_type, '$externalId' FROM media_entries WHERE local_media_id = $id"
        )
        if (active) sql("INSERT INTO library_entries(local_media_id, added_at) VALUES($id, '2026-01-01T00:00:00Z')")
        if (fetchedAt != null) {
            sql(
                "INSERT INTO media_details(local_media_id, backdrop_url, production_status, original_language, " +
                    "runtime_minutes, episode_runtime_minutes, number_of_seasons, number_of_episodes, " +
                    "details_fetched_at) " +
                    "VALUES($id, NULL, 'CURRENT', NULL, NULL, NULL, NULL, NULL, '$fetchedAt')"
            )
        }
    }

    private fun sql(statement: String) = database.openHelper.writableDatabase.execSQL(statement)
}
