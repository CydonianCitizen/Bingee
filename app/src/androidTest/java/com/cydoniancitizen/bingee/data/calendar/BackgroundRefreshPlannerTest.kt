package com.cydoniancitizen.bingee.data.calendar

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.core.result.AppResult
import com.cydoniancitizen.bingee.data.library.local.BingeeDatabase
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

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            BingeeDatabase::class.java
        ).build()
        planner = RoomBackgroundRefreshPlanner(database.libraryDao())
    }

    @After
    fun closeDatabase() = database.close()

    @Test
    fun emptyLibraryReturnsEmptyPlan() = runBlocking {
        val result = planner.plan(20)
        assertTrue(result is AppResult.Success && result.value.isEmpty())
    }

    @Test
    fun boundedPlanPrioritizesNeverThenOldestWithStableProviderTieBreak() = runBlocking {
        insertMedia(1, "z", MediaType.MOVIE, active = true, fetchedAt = null)
        insertMedia(2, "a", MediaType.SERIES, active = true, fetchedAt = null)
        insertMedia(3, "old", MediaType.MOVIE, active = true, fetchedAt = "2026-01-01T00:00:00Z")
        insertMedia(4, "fresh", MediaType.SERIES, active = true, fetchedAt = "2026-07-01T00:00:00Z")
        insertMedia(5, "inactive", MediaType.MOVIE, active = false, fetchedAt = null)

        val first = (planner.plan(3) as AppResult.Success).value
        assertEquals(listOf("a", "z", "old"), first.map { it.mediaRef.externalId })
        assertEquals(listOf(MediaType.SERIES, MediaType.MOVIE, MediaType.MOVIE), first.map { it.mediaType })

        sql(
            "INSERT INTO media_details(local_media_id, backdrop_url, production_status, original_language, " +
                "runtime_minutes, episode_runtime_minutes, number_of_seasons, number_of_episodes, " +
                "details_fetched_at) " +
                "VALUES(1, NULL, 'CURRENT', NULL, NULL, NULL, NULL, NULL, '2026-08-04T00:00:00Z')," +
                "(2, NULL, 'CURRENT', NULL, NULL, NULL, NULL, NULL, '2026-08-04T00:00:00Z')"
        )
        val rotated = (planner.plan(3) as AppResult.Success).value
        assertEquals(listOf("old", "fresh", "a"), rotated.map { it.mediaRef.externalId })
    }

    private fun insertMedia(id: Long, externalId: String, type: MediaType, active: Boolean, fetchedAt: String?) {
        sql(
            "INSERT INTO media_entries(local_media_id, media_type, title, original_title, overview, poster_url, " +
                "release_date, created_at, metadata_updated_at) VALUES($id, '${type.name}', 'Fixture $id', " +
                "NULL, NULL, NULL, NULL, '2025-01-01T00:00:00Z', '2025-01-01T00:00:00Z')"
        )
        sql("INSERT INTO external_refs(local_media_id, source, external_id) VALUES($id, 'TMDB', '$externalId')")
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
