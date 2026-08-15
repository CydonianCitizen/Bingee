package com.cydoniancitizen.bingee.data.calendar

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cydoniancitizen.bingee.core.model.Episode
import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.MediaDetails
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.core.model.Season
import com.cydoniancitizen.bingee.core.result.AppResult
import com.cydoniancitizen.bingee.data.library.local.BingeeDatabase
import com.cydoniancitizen.bingee.data.tmdb.series.TmdbSeasonPayload
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MetadataCalendarStoreTest {
    private lateinit var database: BingeeDatabase
    private lateinit var store: RoomMetadataCalendarStore
    private val now = Instant.parse("2026-08-03T12:00:00Z")
    private val movieRef = ref("movie")
    private val seriesRef = ref("series")
    private val seasonRef = ref("season")

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BingeeDatabase::class.java).build()
        store = RoomMetadataCalendarStore(
            database,
            database.detailsDao(),
            database.seriesDao(),
            database.seriesDao(),
            database.releaseEventDao(),
            ReleaseEventProjector()
        )
    }

    @After
    fun closeDatabase() = database.close()

    @Test
    fun metadataWritesProjectMovieSeasonAndEpisodeEvents() = runBlocking {
        store.storeDetails(movieRef, movie("Film", LocalDate.of(2026, 8, 4)), emptyList(), now)
        val season = season(LocalDate.of(2026, 8, 5))
        store.storeDetails(seriesRef, series("Show"), listOf(season), now)
        store.storeSeason(
            seriesRef,
            TmdbSeasonPayload(
                season,
                listOf(episode(LocalDate.of(2026, 8, 6)))
            ),
            now
        )

        assertEquals(3, count("release_events"))
    }

    @Test
    fun failedEventProjectionRollsBackRelatedMetadataWriteAndPreservesOldEvent() = runBlocking {
        store.storeDetails(movieRef, movie("Old", LocalDate.of(2026, 8, 4)), emptyList(), now)

        runCatching {
            store.storeDetails(
                movieRef,
                movie("New", LocalDate.of(2026, 8, 8), externalRef = ref("different")),
                emptyList(),
                now.plusSeconds(1)
            )
        }

        assertEquals("Old", text("SELECT title FROM media_entries LIMIT 1"))
        assertEquals("2026-08-04", text("SELECT event_date FROM release_events LIMIT 1"))
        assertEquals(1, count("release_events"))
    }

    @Test
    fun episodeDateRefreshPreservesRatingProgressAndMembership() = runBlocking {
        val season = season(LocalDate.of(2026, 8, 5))
        store.storeDetails(seriesRef, series("Show"), listOf(season), now)
        store.storeSeason(
            seriesRef,
            TmdbSeasonPayload(season, listOf(episode(LocalDate.of(2026, 8, 6)))),
            now
        )
        val mediaId = long("SELECT local_media_id FROM media_entries LIMIT 1")
        val episodeId = long("SELECT local_episode_id FROM episodes LIMIT 1")
        sql("INSERT INTO library_entries(local_media_id, added_at) VALUES($mediaId, '2026-08-03T12:00:00Z')")
        sql(
            "INSERT INTO media_ratings(local_media_id, rating_value, rated_at, updated_at) " +
                "VALUES($mediaId, 8, '2026-08-03T12:00:00Z', '2026-08-03T12:00:00Z')"
        )
        sql(
            "INSERT INTO episode_watch_progress(local_episode_id, watched_at) " +
                "VALUES($episodeId, '2026-08-03T12:00:00Z')"
        )
        sql("UPDATE media_entries SET is_favorite = 1 WHERE local_media_id = $mediaId")

        store.storeDetails(seriesRef, series("Refreshed"), listOf(season), now.plusSeconds(1))

        store.storeSeason(
            seriesRef,
            TmdbSeasonPayload(season, listOf(episode(LocalDate.of(2026, 8, 7)))),
            now.plusSeconds(1)
        )

        assertEquals("2026-08-07", text("SELECT event_date FROM release_events WHERE subject_type = 'EPISODE'"))
        assertEquals(1, count("release_events", "subject_type = 'EPISODE'"))
        assertEquals(1, count("media_ratings"))
        assertEquals(1, count("episode_watch_progress"))
        assertEquals(1, count("library_entries"))
        assertEquals("1", text("SELECT is_favorite FROM media_entries WHERE local_media_id = $mediaId"))
    }

    @Test
    fun localRepositoryMapsActiveRowsAndPersistsSuccessfulRefreshState() = runBlocking {
        store.storeDetails(movieRef, movie("Film", LocalDate.of(2026, 8, 4)), emptyList(), now)
        val mediaId = long("SELECT local_media_id FROM media_entries LIMIT 1")
        sql("INSERT INTO library_entries(local_media_id, added_at) VALUES($mediaId, '2026-08-03T12:00:00Z')")
        val repository = DefaultReleaseCalendarRepository(
            database.releaseEventDao(),
            Clock.fixed(now, ZoneOffset.UTC)
        )

        val events = repository.observeEvents(LocalDate.of(2026, 7, 27)).first()
        assertTrue(events is AppResult.Success)
        assertEquals(1, (events as AppResult.Success).value.size)
        assertEquals(movieRef, events.value.single().mediaRef)
        assertTrue(repository.markRefreshSuccessful(now) is AppResult.Success)
        assertEquals(now, (repository.observeLastSuccessfulRefresh().first() as AppResult.Success).value)
    }

    private fun movie(title: String, date: LocalDate?, externalRef: ExternalMediaRef = movieRef) = MediaDetails(
        externalRef = externalRef,
        mediaType = MediaType.MOVIE,
        title = title,
        releaseDate = date
    )

    private fun series(title: String) = MediaDetails(
        externalRef = seriesRef,
        mediaType = MediaType.SERIES,
        title = title
    )

    private fun season(date: LocalDate?) = Season(
        seriesRef = seriesRef,
        externalRef = seasonRef,
        seasonNumber = 0,
        name = "Specials",
        airDate = date,
        episodeCount = 1
    )

    private fun episode(date: LocalDate?) = Episode(
        seriesRef = seriesRef,
        seasonRef = seasonRef,
        externalRef = ref("episode"),
        seasonNumber = 0,
        episodeNumber = 1,
        title = "Episode",
        airDate = date
    )

    private fun ref(id: String) = ExternalMediaRef(MediaSource.TMDB, id)

    private fun sql(statement: String) = database.openHelper.writableDatabase.execSQL(statement)

    private fun count(table: String, where: String? = null): Int = database.openHelper.writableDatabase.query(
        "SELECT COUNT(*) FROM $table" + where?.let { " WHERE $it" }.orEmpty()
    ).use {
        it.moveToFirst()
        it.getInt(0)
    }

    private fun text(statement: String): String = database.openHelper.writableDatabase.query(statement).use {
        it.moveToFirst()
        it.getString(0)
    }

    private fun long(statement: String): Long = database.openHelper.writableDatabase.query(statement).use {
        it.moveToFirst()
        it.getLong(0)
    }
}
