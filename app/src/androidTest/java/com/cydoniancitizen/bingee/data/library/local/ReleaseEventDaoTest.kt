package com.cydoniancitizen.bingee.data.library.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.ReleaseEventType
import com.cydoniancitizen.bingee.core.model.ReleaseSubjectType
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReleaseEventDaoTest {
    private lateinit var database: BingeeDatabase
    private lateinit var dao: ReleaseEventDao
    private val now = Instant.parse("2026-08-03T12:00:00Z")

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BingeeDatabase::class.java).build()
        dao = database.releaseEventDao()
        insertFixture()
    }

    @After
    fun closeDatabase() = database.close()

    @Test
    fun backfillSeparatesNumericSubjectCollisionsAndOrdersSameDateDeterministically() = runBlocking {
        dao.backfill(now)

        val events = dao.observeActiveEvents(LocalDate.of(2026, 8, 3)).first()

        assertEquals(4, events.size)
        assertEquals(
            listOf(
                ReleaseEventType.EPISODE_AIRING,
                ReleaseEventType.SEASON_PREMIERE,
                ReleaseEventType.MOVIE_RELEASE,
                ReleaseEventType.MOVIE_RELEASE
            ),
            events.map { it.eventType }
        )
        assertEquals(
            listOf(
                ReleaseSubjectType.EPISODE,
                ReleaseSubjectType.SEASON,
                ReleaseSubjectType.MEDIA
            ),
            events.take(3).map { it.subjectType }
        )
        assertEquals(listOf("42", "42", "42"), events.take(3).map { it.subjectExternalId })
    }

    @Test
    fun repeatBackfillUpdatesAndRemovesOneProjectionWithoutTouchingUnrelatedRows() = runBlocking {
        dao.backfill(now)
        dao.backfill(now.plusSeconds(10))
        assertEquals(6, dao.observeActiveEvents(LocalDate.MIN).first().size)

        sql("UPDATE media_entries SET release_date = '2026-08-05' WHERE local_media_id = 1")
        dao.backfill(now.plusSeconds(20))
        val updated = dao.observeActiveEvents(LocalDate.MIN).first()
        assertEquals(6, updated.size)
        assertEquals(
            LocalDate.of(2026, 8, 5),
            updated.single { it.subjectType == ReleaseSubjectType.MEDIA && it.subjectExternalId == "42" }.eventDate
        )

        sql("UPDATE media_entries SET release_date = NULL WHERE local_media_id = 1")
        dao.backfill(now.plusSeconds(30))
        val removed = dao.observeActiveEvents(LocalDate.MIN).first()
        assertEquals(5, removed.size)
        assertEquals(1, removed.count { it.subjectType == ReleaseSubjectType.SEASON })
        assertEquals(1, removed.count { it.subjectType == ReleaseSubjectType.EPISODE })
    }

    @Test
    fun activeMembershipJoinHidesAndRestoresRetainedEventsWhilePersonalRowsRemain() = runBlocking {
        dao.backfill(now)
        sql(
            "INSERT INTO media_ratings(local_media_id, rating_value, rated_at, updated_at) " +
                "VALUES(1, 8, '2026-08-03T11:00:00Z', '2026-08-03T11:00:00Z')"
        )
        sql(
            "INSERT INTO movie_watch_progress(local_media_id, watched_at) " +
                "VALUES(1, '2026-08-03T11:00:00Z')"
        )

        database.libraryDao().removeMembership(MediaSource.TMDB, "42")
        assertEquals(
            0,
            dao.observeActiveEvents(LocalDate.MIN).first().count {
                it.subjectType == ReleaseSubjectType.MEDIA && it.subjectExternalId == "42"
            }
        )
        assertEquals(1, count("release_events", "subject_type = 'MEDIA' AND subject_external_id = '42'"))
        assertEquals(1, count("media_ratings", "local_media_id = 1"))
        assertEquals(1, count("movie_watch_progress", "local_media_id = 1"))

        database.libraryDao().addExistingToLibrary(MediaSource.TMDB, "42", now)
        assertEquals(
            1,
            dao.observeActiveEvents(LocalDate.MIN).first().count {
                it.subjectType == ReleaseSubjectType.MEDIA && it.subjectExternalId == "42"
            }
        )
    }

    @Test
    fun lookbackIncludesBoundaryAndAllFutureWhileRefreshStateIsIndependent() = runBlocking {
        dao.backfill(now)
        assertEquals(5, dao.observeActiveEvents(LocalDate.of(2026, 7, 27)).first().size)
        assertEquals(4, dao.observeActiveEvents(LocalDate.of(2026, 8, 3)).first().size)
        assertNull(dao.observeLastSuccessfulRefresh().first())

        dao.replaceRefreshState(CalendarRefreshStateEntity(lastSuccessfulRefreshAt = now))
        assertEquals(now, dao.observeLastSuccessfulRefresh().first())
    }

    @Test
    fun notificationCandidateWindowIsBoundedActiveAndDeterministic() = runBlocking {
        dao.backfill(now)
        val from = LocalDate.of(2026, 8, 3)
        val through = LocalDate.of(2026, 8, 10)

        val candidates = dao.getActiveEventsBetween(from, through, 200)
        assertEquals(3, candidates.size)
        assertEquals(
            listOf(
                ReleaseEventType.EPISODE_AIRING,
                ReleaseEventType.SEASON_PREMIERE,
                ReleaseEventType.MOVIE_RELEASE
            ),
            candidates.map { it.eventType }
        )

        database.libraryDao().removeMembership(MediaSource.TMDB, "42")
        assertEquals(2, dao.getActiveEventsBetween(from, through, 200).size)
        database.libraryDao().addExistingToLibrary(MediaSource.TMDB, "42", now)
        assertEquals(3, dao.getActiveEventsBetween(from, through, 200).size)
    }

    private fun insertFixture() {
        sql(
            "INSERT INTO media_entries(local_media_id, media_type, title, original_title, overview, poster_url, " +
                "release_date, created_at, metadata_updated_at, is_favorite) VALUES" +
                "(1, 'MOVIE', 'Zulu', NULL, NULL, NULL, '2026-08-03', " +
                "'2026-08-01T10:00:00Z', '2026-08-03T10:00:00Z', 0)," +
                "(2, 'SERIES', 'Alpha', NULL, NULL, NULL, NULL, " +
                "'2026-08-01T10:00:00Z', '2026-08-03T10:00:00Z', 0)," +
                "(3, 'MOVIE', 'Old', NULL, NULL, NULL, '2026-07-26', " +
                "'2026-08-01T10:00:00Z', '2026-08-03T10:00:00Z', 0)," +
                "(4, 'MOVIE', 'Future', NULL, NULL, NULL, '2030-01-01', " +
                "'2026-08-01T10:00:00Z', '2026-08-03T10:00:00Z', 0)," +
                "(5, 'MOVIE', 'Boundary', NULL, NULL, NULL, '2026-07-27', " +
                "'2026-08-01T10:00:00Z', '2026-08-03T10:00:00Z', 0)"
        )
        listOf(1L to "42", 2L to "100", 3L to "old", 4L to "future", 5L to "boundary").forEach {
            sql(
                "INSERT INTO external_refs(local_media_id, source, external_id) VALUES(${it.first}, 'TMDB', '${it.second}')"
            )
            sql("INSERT INTO library_entries(local_media_id, added_at) VALUES(${it.first}, '2026-08-02T10:00:00Z')")
        }
        sql(
            "INSERT INTO seasons(local_season_id, local_media_id, source, external_id, season_number, name, " +
                "overview, poster_url, air_date, episode_count, metadata_updated_at, episodes_fetched_at) " +
                "VALUES(10, 2, 'TMDB', '42', 0, 'Specials', NULL, NULL, '2026-08-03', 1, " +
                "'2026-08-03T10:00:00Z', '2026-08-03T10:00:00Z')"
        )
        sql(
            "INSERT INTO episodes(local_episode_id, local_season_id, source, external_id, episode_number, title, " +
                "overview, air_date, runtime_minutes, still_url, metadata_updated_at) " +
                "VALUES(20, 10, 'TMDB', '42', 1, 'Episode', NULL, '2026-08-03', NULL, NULL, " +
                "'2026-08-03T10:00:00Z')"
        )
    }

    private fun sql(statement: String) = database.openHelper.writableDatabase.execSQL(statement)

    private fun count(table: String, where: String): Int =
        database.openHelper.writableDatabase.query("SELECT COUNT(*) FROM $table WHERE $where").use {
            it.moveToFirst()
            it.getInt(0)
        }
}
