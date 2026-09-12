package com.cydoniancitizen.bingee.data.library

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.Genre
import com.cydoniancitizen.bingee.core.model.MediaDetails
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.core.result.AppResult
import com.cydoniancitizen.bingee.data.details.toCacheWrite
import com.cydoniancitizen.bingee.data.importexport.BackupDataStore
import com.cydoniancitizen.bingee.data.importexport.BackupJsonCodec
import com.cydoniancitizen.bingee.data.importexport.BackupParseResult
import com.cydoniancitizen.bingee.data.importexport.BackupValidationResult
import com.cydoniancitizen.bingee.data.importexport.BackupValidator
import com.cydoniancitizen.bingee.data.importexport.RestoreFailureInjector
import com.cydoniancitizen.bingee.data.importexport.RestoreStage
import com.cydoniancitizen.bingee.data.library.local.BingeeDatabase
import com.cydoniancitizen.bingee.data.settings.DataStoreReleaseNotificationPreferences
import com.cydoniancitizen.bingee.domain.model.GenreStatistic
import com.cydoniancitizen.bingee.domain.model.calculateTasteStatistics
import com.cydoniancitizen.bingee.domain.model.calculateWatchedStatistics
import com.cydoniancitizen.bingee.testutil.TestCalendarDateSource
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GenreStatisticsPipelineTest {
    private lateinit var database: BingeeDatabase
    private lateinit var repository: DefaultLibraryRepository
    private lateinit var store: BackupDataStore
    private val now = Instant.parse("2026-08-01T10:00:00Z")
    private val today = LocalDate.of(2026, 8, 1)
    private val drama = Genre("Drama", MediaSource.TMDB, 18)

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            BingeeDatabase::class.java
        ).build()
        repository = DefaultLibraryRepository(
            database.libraryDao(),
            database.watchProgressDao(),
            database.ratingDao(),
            Clock.fixed(now, ZoneOffset.UTC),
            TestCalendarDateSource(today)
        )
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
    fun tearDown() = database.close()

    @Test
    fun watchedTitleWithGenresFeedsRankingAndProfilePodium() = runBlocking {
        cacheMovie("1", listOf(drama))
        val entries = (repository.observePersonalViewing().first() as AppResult.Success).value
        val expected = listOf(GenreStatistic(MediaSource.TMDB, 18, "Drama", 1))
        assertEquals(expected, calculateTasteStatistics(entries).rankedGenres)
        assertEquals(expected, calculateWatchedStatistics(entries, ZoneOffset.UTC, today, today.year).movieGenres)
    }

    @Test
    fun multipleTitlesCountOncePerCanonicalGenreAndIgnoreUnwatchedTitles() = runBlocking {
        cacheMovie("1", listOf(drama, drama.copy(name = "Dramma"), Genre("Comedy", MediaSource.TMDB, 35)))
        cacheMovie("2", listOf(drama.copy(name = "Dramma")))
        cacheMovie("3", listOf(drama), watched = false)
        val ranking = ranking()
        assertEquals(listOf(18L, 35L), ranking.map { it.genreId })
        assertEquals(listOf(2, 1), ranking.map { it.titleCount })
    }

    @Test
    fun missingAndLegacyGenresDoNotInventStatistics() = runBlocking {
        cacheMovie("1", emptyList())
        cacheMovie("2", listOf(Genre("Dramma")))
        assertTrue(ranking().isEmpty())
        assertEquals(2, (repository.observePersonalViewing().first() as AppResult.Success).value.size)
    }

    @Test
    fun backupRoundTripPreservesGenresWithoutDetailsOrNetworkAndRollsBackFailure() = runBlocking {
        cacheMovie("1", listOf(drama, Genre("Legacy name")))
        cacheMovie("2", listOf(drama.copy(name = "Dramma")))
        val before = ranking()
        val document = (BackupJsonCodec.parse(store.createPortableBackup(now)) as BackupParseResult.Success).document
        val plan = (BackupValidator.validate(document, today) as BackupValidationResult.Success).plan
        // Export initializes portable preferences; rollback must preserve the state just before restore.
        val snapshot = database.portableSnapshotDao().readSnapshot()
        var failed = false
        try {
            store.restore(plan, RestoreFailureInjector { if (it == RestoreStage.GENRES) error("injected") })
        } catch (_: IllegalStateException) {
            failed = true
        }
        assertTrue(failed)
        assertEquals(snapshot, database.portableSnapshotDao().readSnapshot())
        store.restore(plan)
        assertEquals(before, ranking())
        val restored = database.portableSnapshotDao().readSnapshot()
        assertNotEquals(snapshot.media.map { it.localMediaId }, restored.media.map { it.localMediaId })
        assertEquals(listOf("Drama", "Legacy name", "Dramma"), restored.genres.map { it.name })
        assertTrue(
            database.detailsDao().observeCachedDetails(MediaSource.TMDB, MediaType.MOVIE, "1").first()?.details == null
        )
        assertEquals(document.data, store.readPortableData())
    }

    private suspend fun ranking(): List<GenreStatistic> = calculateTasteStatistics(
        (repository.observePersonalViewing().first() as AppResult.Success).value
    ).rankedGenres

    private suspend fun cacheMovie(id: String, genres: List<Genre>, watched: Boolean = true) {
        val write = MediaDetails(ExternalMediaRef(MediaSource.TMDB, id), MediaType.MOVIE, "Movie $id", genres = genres)
            .toCacheWrite(now)
        database.detailsDao().storeDetails(write.media, MediaSource.TMDB, id, write.details, write.genres)
        if (watched) database.watchProgressDao().markMovieWatched(MediaSource.TMDB, id, now)
    }
}
