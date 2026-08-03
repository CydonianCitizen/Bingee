package com.cydoniancitizen.bingee.data.library.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SeriesAndProgressDaoTest {
    private lateinit var database: BingeeDatabase
    private lateinit var libraryDao: LibraryDao
    private lateinit var seriesDao: SeriesDao
    private lateinit var progressDao: WatchProgressDao
    private val now = Instant.parse("2026-08-03T12:00:00Z")
    private val today = LocalDate.of(2026, 8, 3)

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BingeeDatabase::class.java).build()
        libraryDao = database.libraryDao()
        seriesDao = database.seriesDao()
        progressDao = database.watchProgressDao()
        runBlocking {
            addMedia("100", MediaType.SERIES, "Series")
            addMedia("200", MediaType.MOVIE, "Movie")
        }
    }

    @After
    fun closeDatabase() = database.close()

    @Test
    fun seasonZeroAndRegularEpisodesAreOrderedObservedAndProviderAware() = runBlocking {
        seriesDao.upsertSeasonSummaries(
            MediaSource.TMDB,
            "100",
            listOf(season("11", 1), season("10", 0))
        )
        seriesDao.storeSeasonEpisodes(
            MediaSource.TMDB,
            "100",
            season("11", 1),
            listOf(episode("102", 2, title = "Second"), episode("101", 1, title = "First")),
            now
        )

        val rows = seriesDao.observeSeriesSeasons(MediaSource.TMDB, "100").first()

        assertEquals(listOf(0, 1), rows.map { it.season.seasonNumber })
        assertEquals(listOf(1, 2), rows[1].episodes.map { it.episode.episodeNumber })
        assertEquals("First", rows[1].episodes.first().episode.title)
        assertEquals("11", seriesDao.getSeasonForSeries(MediaSource.TMDB, "100", 1)?.externalId)
    }

    @Test
    fun metadataRefreshIsAtomicRetainsUnmatchedRowsAndPreservesProgressTimestamp() = runBlocking {
        storeRegularEpisodes()
        val firstWatchedAt = now.minusSeconds(3600)
        assertEquals(
            ProgressWriteOutcome.SUCCESS,
            progressDao.markEpisodeWatched(MediaSource.TMDB, "101", today, firstWatchedAt)
        )

        seriesDao.storeSeasonEpisodes(
            MediaSource.TMDB,
            "100",
            season("11", 1, name = "Updated"),
            listOf(episode("101", 1, title = "Updated first")),
            now.plusSeconds(60)
        )

        val refreshed = seriesDao.observeSeason(MediaSource.TMDB, "100", "11").first()!!
        assertEquals(listOf(1, 2, 3), refreshed.episodes.map { it.episode.episodeNumber })
        assertEquals("Updated first", refreshed.episodes.first().episode.title)
        assertEquals(firstWatchedAt, refreshed.episodes.first().progress?.watchedAt)
        assertEquals(now.plusSeconds(60), refreshed.season.episodesFetchedAt)

        try {
            seriesDao.storeSeasonEpisodes(
                MediaSource.TMDB,
                "100",
                season("11", 1, name = "Must roll back"),
                listOf(
                    episode("101", 1, title = "Partial update"),
                    episode("102", 1, title = "Conflicting number")
                ),
                now.plusSeconds(120)
            )
            throw AssertionError("Expected identity conflict")
        } catch (_: IllegalStateException) {
            // The transaction must restore both metadata and freshness.
        }

        val rolledBack = seriesDao.observeSeason(MediaSource.TMDB, "100", "11").first()!!
        assertEquals("Updated", rolledBack.season.name)
        assertEquals("Updated first", rolledBack.episodes.first().episode.title)
        assertEquals(now.plusSeconds(60), rolledBack.season.episodesFetchedAt)
        assertEquals(firstWatchedAt, rolledBack.episodes.first().progress?.watchedAt)
    }

    @Test
    fun episodeAndBulkActionsEnforceTrackabilityTimestampAndSeasonIsolation() = runBlocking {
        storeRegularEpisodes()
        seriesDao.storeSeasonEpisodes(
            MediaSource.TMDB,
            "100",
            season("10", 0),
            listOf(episode("91", 1)),
            now
        )
        val original = now.minusSeconds(600)
        val bulk = now
        assertEquals(
            ProgressWriteOutcome.NOT_TRACKABLE,
            progressDao.markEpisodeWatched(MediaSource.TMDB, "103", today, bulk)
        )
        assertEquals(
            ProgressWriteOutcome.SUCCESS,
            progressDao.markEpisodeWatched(MediaSource.TMDB, "101", today, original)
        )
        assertEquals(
            ProgressWriteOutcome.SUCCESS,
            progressDao.markSeasonWatched(MediaSource.TMDB, "11", today, bulk)
        )

        val regular = seriesDao.observeSeason(MediaSource.TMDB, "100", "11").first()!!
        assertEquals(original, regular.episodes[0].progress?.watchedAt)
        assertEquals(bulk, regular.episodes[1].progress?.watchedAt)
        assertNull(regular.episodes[2].progress)
        assertNull(seriesDao.observeSeason(MediaSource.TMDB, "100", "10").first()!!.episodes[0].progress)

        progressDao.markSeasonWatched(MediaSource.TMDB, "10", today, bulk.plusSeconds(1))
        assertEquals(
            bulk.plusSeconds(1),
            seriesDao.observeSeason(MediaSource.TMDB, "100", "10").first()!!.episodes[0].progress?.watchedAt
        )
        progressDao.markSeasonUnwatched(MediaSource.TMDB, "11")
        assertTrue(
            seriesDao.observeSeason(MediaSource.TMDB, "100", "11").first()!!
                .episodes.all { it.progress == null }
        )
        assertFalse(
            seriesDao.observeSeason(MediaSource.TMDB, "100", "10").first()!!
                .episodes.all { it.progress == null }
        )
        assertEquals(
            ProgressWriteOutcome.SUCCESS,
            progressDao.markEpisodeUnwatched(MediaSource.TMDB, "101")
        )
    }

    @Test
    fun movieProgressIsSeparateIdempotentAndSurvivesLibraryRemoval() = runBlocking {
        assertEquals(
            ProgressWriteOutcome.MEDIA_TYPE_MISMATCH,
            progressDao.markMovieWatched(MediaSource.TMDB, "100", now)
        )
        assertEquals(
            ProgressWriteOutcome.SUCCESS,
            progressDao.markMovieWatched(MediaSource.TMDB, "200", now)
        )
        assertEquals(
            ProgressWriteOutcome.SUCCESS,
            progressDao.markMovieWatched(MediaSource.TMDB, "200", now.plusSeconds(1))
        )
        assertEquals(now, progressDao.observeMovieProgress(MediaSource.TMDB, "200").first()?.watchedAt)

        libraryDao.removeMembership(MediaSource.TMDB, "200")
        assertEquals(now, progressDao.observeMovieProgress(MediaSource.TMDB, "200").first()?.watchedAt)
        assertEquals(
            ProgressWriteOutcome.SUCCESS,
            progressDao.markMovieUnwatched(MediaSource.TMDB, "200")
        )
        assertNull(progressDao.observeMovieProgress(MediaSource.TMDB, "200").first()?.watchedAt)
        assertEquals(
            ProgressWriteOutcome.SUCCESS,
            progressDao.markMovieUnwatched(MediaSource.TMDB, "200")
        )
    }

    private suspend fun storeRegularEpisodes() {
        seriesDao.storeSeasonEpisodes(
            MediaSource.TMDB,
            "100",
            season("11", 1),
            listOf(
                episode("101", 1, airDate = today),
                episode("102", 2, airDate = null),
                episode("103", 3, airDate = today.plusDays(1))
            ),
            now
        )
    }

    private suspend fun addMedia(externalId: String, type: MediaType, title: String) {
        libraryDao.addToLibrary(
            MediaEntity(
                mediaType = type,
                title = title,
                originalTitle = null,
                overview = null,
                posterUrl = null,
                releaseDate = null,
                createdAt = now,
                metadataUpdatedAt = now
            ),
            MediaSource.TMDB,
            externalId,
            now
        )
    }

    private fun season(
        externalId: String,
        number: Int,
        name: String = if (number == 0) "Specials" else "Season $number"
    ) = SeasonEntity(
        localMediaId = 0,
        source = MediaSource.TMDB,
        externalId = externalId,
        seasonNumber = number,
        name = name,
        overview = null,
        posterUrl = null,
        airDate = null,
        episodeCount = 3,
        metadataUpdatedAt = now,
        episodesFetchedAt = null
    )

    private fun episode(
        externalId: String,
        number: Int,
        title: String = "Episode $number",
        airDate: LocalDate? = today
    ) = EpisodeEntity(
        localSeasonId = 0,
        source = MediaSource.TMDB,
        externalId = externalId,
        episodeNumber = number,
        title = title,
        overview = null,
        airDate = airDate,
        runtimeMinutes = 45,
        stillUrl = null,
        metadataUpdatedAt = now
    )
}
