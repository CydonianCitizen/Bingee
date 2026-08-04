package com.cydoniancitizen.bingee.data.importexport

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.data.library.local.BingeeDatabase
import com.cydoniancitizen.bingee.data.settings.DataStoreReleaseNotificationPreferences
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupDataStoreTest {
    private lateinit var database: BingeeDatabase
    private lateinit var store: BackupDataStore
    private val exportedAt = Instant.parse("2026-08-04T10:00:00Z")

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            BingeeDatabase::class.java
        ).build()
        store = BackupDataStore(
            database,
            database.portableSnapshotDao(),
            database.releaseEventDao(),
            DataStoreReleaseNotificationPreferences(
                ApplicationProvider.getApplicationContext(),
                database,
                database.portableSnapshotDao()
            )
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun restoreReplacesPortableStateRegeneratesIdsAndSupportsRepeatImport() = runBlocking {
        val first = plan("1", includeSeries = false)
        store.restore(first)
        val firstId = database.portableSnapshotDao().readSnapshot().media.single().localMediaId

        val second = plan("2", includeSeries = true)
        store.restore(second)
        val secondSnapshot = database.portableSnapshotDao().readSnapshot()
        assertEquals(2, secondSnapshot.media.size)
        assertNotEquals(firstId, secondSnapshot.media.single { it.mediaType == MediaType.MOVIE }.localMediaId)
        assertEquals(1, secondSnapshot.memberships.size)
        assertEquals(1, secondSnapshot.movieProgress.size)
        assertEquals(1, secondSnapshot.episodeProgress.size)
        assertEquals(1, secondSnapshot.ratings.size)
        assertEquals(1, secondSnapshot.seasons.size)
        assertEquals(1, secondSnapshot.episodes.size)

        store.restore(second)
        val repeated = database.portableSnapshotDao().readSnapshot()
        assertEquals(secondSnapshot.media.size, repeated.media.size)
        assertEquals(secondSnapshot.refs.size, repeated.refs.size)
        assertEquals(secondSnapshot.memberships.size, repeated.memberships.size)
        assertEquals(secondSnapshot.episodeProgress.size, repeated.episodeProgress.size)
    }

    @Test
    fun restoreRollsBackAtEveryInsertionStage() = runBlocking {
        store.restore(plan("existing", includeSeries = true))
        val before = database.portableSnapshotDao().readSnapshot()

        RestoreStage.entries.forEach { failedStage ->
            var failed = false
            try {
                store.restore(
                    plan("incoming-${failedStage.name}", includeSeries = true),
                    RestoreFailureInjector { stage ->
                        if (stage == failedStage) throw InjectedRestoreFailure(stage)
                    }
                )
            } catch (_: InjectedRestoreFailure) {
                failed = true
            }
            assertTrue("restore must fail at ${failedStage.name}", failed)
            assertEquals(before, database.portableSnapshotDao().readSnapshot())
        }
    }

    @Test
    fun exportedBackupRestoresToTheSameSemanticData() = runBlocking {
        store.restore(roundTripPlan())
        val clock = Clock.fixed(exportedAt, ZoneOffset.UTC)
        val first = exportedDocument(BackupExporter(store, clock).export().bytes)

        val validated = BackupValidator.validate(first)
        assertTrue(validated is BackupValidationResult.Success)
        store.restore((validated as BackupValidationResult.Success).plan)

        val second = exportedDocument(BackupExporter(store, clock).export().bytes)
        assertEquals(first.data, second.data)
    }

    private fun exportedDocument(bytes: ByteArray): BackupDocument =
        (BackupJsonCodec.parse(bytes) as BackupParseResult.Success).document

    private class InjectedRestoreFailure(stage: RestoreStage) : RuntimeException(stage.name)

    private fun roundTripPlan(): ValidatedBackupPlan {
        val movie = BackupRef(MediaSource.TMDB, "movie-round-trip")
        val movieAlias = BackupRef(MediaSource.TMDB, "movie-round-trip-alias")
        val series = BackupRef(MediaSource.TMDB, "series-round-trip")
        val seriesAlias = BackupRef(MediaSource.TMDB, "series-round-trip-alias")
        val specials = BackupRef(MediaSource.TMDB, "season-round-trip-specials")
        val regular = BackupRef(MediaSource.TMDB, "season-round-trip-1")
        val watchedEpisode = BackupRef(MediaSource.TMDB, "episode-round-trip-watched")
        val futureEpisode = BackupRef(MediaSource.TMDB, "episode-round-trip-future")
        val removedMovie = BackupRef(MediaSource.TMDB, "movie-round-trip-removed")
        val watchedAt = Instant.parse("2026-07-01T08:00:00Z")
        val ratedAt = Instant.parse("2026-07-02T08:00:00Z")
        val data = BackupData(
            media = listOf(
                BackupMedia(
                    movie,
                    listOf(movie, movieAlias),
                    MediaType.MOVIE,
                    "映画 — 日本語",
                    null,
                    null,
                    null,
                    null
                ),
                BackupMedia(
                    series,
                    listOf(series, seriesAlias),
                    MediaType.SERIES,
                    "Series with optional metadata",
                    null,
                    null,
                    null,
                    null
                ),
                BackupMedia(
                    removedMovie,
                    listOf(removedMovie),
                    MediaType.MOVIE,
                    "Removed but rated",
                    null,
                    null,
                    null,
                    null
                )
            ),
            seasons = listOf(
                BackupSeason(series, specials, 0, "Specials", null, null, null, 1),
                BackupSeason(series, regular, 1, "Season 1", null, null, null, 2)
            ),
            episodes = listOf(
                BackupEpisode(specials, watchedEpisode, 1, "Special", null, null, 42, null),
                BackupEpisode(
                    regular,
                    futureEpisode,
                    1,
                    "Future episode",
                    null,
                    java.time.LocalDate.of(2026, 12, 31),
                    null,
                    null
                )
            ),
            library = listOf(
                BackupLibraryEntry(movie, exportedAt),
                BackupLibraryEntry(series, exportedAt)
            ),
            movieProgress = listOf(
                BackupMovieProgress(movie, watchedAt),
                BackupMovieProgress(removedMovie, watchedAt)
            ),
            episodeProgress = listOf(BackupEpisodeProgress(watchedEpisode, watchedAt)),
            ratings = listOf(
                BackupRating(movie, 10, ratedAt, ratedAt),
                BackupRating(removedMovie, 4, ratedAt, ratedAt)
            ),
            preferences = BackupPreferences(7, false, true, false)
        )
        val document = BackupDocument(BACKUP_FORMAT_ID, BACKUP_SCHEMA_VERSION, exportedAt, data)
        return (BackupValidator.validate(document) as BackupValidationResult.Success).plan
    }

    private fun plan(movieId: String, includeSeries: Boolean): ValidatedBackupPlan {
        val movieRef = BackupRef(MediaSource.TMDB, movieId)
        val seriesRef = BackupRef(MediaSource.TMDB, "series-$movieId")
        val seasonRef = BackupRef(MediaSource.TMDB, "season-$movieId")
        val episodeRef = BackupRef(MediaSource.TMDB, "episode-$movieId")
        val data = BackupData(
            media = buildList {
                add(BackupMedia(movieRef, listOf(movieRef), MediaType.MOVIE, "Movie $movieId", null, null, null, null))
                if (includeSeries) {
                    add(
                        BackupMedia(
                            seriesRef,
                            listOf(seriesRef),
                            MediaType.SERIES,
                            "Series $movieId",
                            null,
                            null,
                            null,
                            null
                        )
                    )
                }
            },
            seasons = if (includeSeries) {
                listOf(
                    BackupSeason(seriesRef, seasonRef, 0, "Specials", null, null, null, 1)
                )
            } else {
                emptyList()
            },
            episodes = if (includeSeries) {
                listOf(
                    BackupEpisode(seasonRef, episodeRef, 1, "Episode", null, null, 40, null)
                )
            } else {
                emptyList()
            },
            library = listOf(BackupLibraryEntry(movieRef, exportedAt)),
            movieProgress = listOf(BackupMovieProgress(movieRef, exportedAt)),
            episodeProgress = if (includeSeries) listOf(BackupEpisodeProgress(episodeRef, exportedAt)) else emptyList(),
            ratings = listOf(BackupRating(movieRef, 9, exportedAt, exportedAt)),
            preferences = BackupPreferences(3, true, false, true)
        )
        val document = BackupDocument(BACKUP_FORMAT_ID, BACKUP_SCHEMA_VERSION, exportedAt, data)
        return (BackupValidator.validate(document) as BackupValidationResult.Success).plan
    }
}
