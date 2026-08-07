package com.cydoniancitizen.bingee.data.library.local

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.data.importexport.BACKUP_FORMAT_ID
import com.cydoniancitizen.bingee.data.importexport.BACKUP_SCHEMA_VERSION
import com.cydoniancitizen.bingee.data.importexport.BackupData
import com.cydoniancitizen.bingee.data.importexport.BackupDataStore
import com.cydoniancitizen.bingee.data.importexport.BackupDocument
import com.cydoniancitizen.bingee.data.importexport.BackupLibraryEntry
import com.cydoniancitizen.bingee.data.importexport.BackupMedia
import com.cydoniancitizen.bingee.data.importexport.BackupPreferences
import com.cydoniancitizen.bingee.data.importexport.BackupRating
import com.cydoniancitizen.bingee.data.importexport.BackupRef
import com.cydoniancitizen.bingee.data.importexport.RestoreStage
import com.cydoniancitizen.bingee.data.importexport.ValidatedBackupPlan
import com.cydoniancitizen.bingee.data.settings.DataStoreReleaseNotificationPreferences
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BingeeDatabaseMigrationTest {
    @Suppress("DEPRECATION")
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        requireNotNull(BingeeDatabase::class.java.canonicalName),
        FrameworkSQLiteOpenHelperFactory()
    )

    private lateinit var database: BingeeDatabase
    private val clock = Clock.fixed(Instant.parse("2026-08-06T10:00:00Z"), ZoneOffset.UTC)

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            BingeeDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun createDatabaseVersionOneValidatesCanonicalSchema() {
        val db = helper.createDatabase(TEST_DB, 1)
        val validated = helper.runMigrationsAndValidate(TEST_DB, 1, true)

        listOf(
            "media_entries",
            "external_refs",
            "library_entries",
            "media_details",
            "media_genres",
            "seasons",
            "episodes",
            "episode_watch_progress",
            "movie_watch_progress",
            "series_watch_progress",
            "media_ratings",
            "release_events",
            "calendar_refresh_state",
            "notification_deliveries",
            "portable_preferences",
            "import_provenance_refs"
        ).forEach { table ->
            val count = validated.query("SELECT COUNT(*) FROM $table").use { c ->
                c.moveToFirst()
                c.getInt(0)
            }
            assertEquals(0, count)
        }

        db.close()
        validated.close()
    }

    @Test
    fun versionOneBaselinePersistsFavoritesAndWatchedDates() = runBlocking {
        val movieMediaId = database.portableSnapshotDao().insertMedia(
            MediaEntity(
                mediaType = MediaType.MOVIE,
                title = "Favorite Movie",
                originalTitle = null,
                overview = null,
                posterUrl = null,
                releaseDate = LocalDate.parse("2026-01-01"),
                createdAt = clock.instant(),
                metadataUpdatedAt = clock.instant(),
                isFavorite = true
            )
        )
        database.portableSnapshotDao().insertExternalRef(
            ExternalRefEntity(localMediaId = movieMediaId, source = MediaSource.TMDB, externalId = "1001")
        )
        database.watchProgressDao().setMediaWatchedDate(
            source = MediaSource.TMDB,
            externalId = "1001",
            watchedDate = LocalDate.parse("2026-06-01"),
            now = clock.instant()
        )

        val seriesMediaId = database.portableSnapshotDao().insertMedia(
            MediaEntity(
                mediaType = MediaType.SERIES,
                title = "Watched Series",
                originalTitle = null,
                overview = null,
                posterUrl = null,
                releaseDate = LocalDate.parse("2026-01-01"),
                createdAt = clock.instant(),
                metadataUpdatedAt = clock.instant(),
                isFavorite = false
            )
        )
        database.portableSnapshotDao().insertExternalRef(
            ExternalRefEntity(localMediaId = seriesMediaId, source = MediaSource.TMDB, externalId = "1002")
        )
        database.watchProgressDao().setMediaWatchedDate(
            source = MediaSource.TMDB,
            externalId = "1002",
            watchedDate = LocalDate.parse("2026-07-01"),
            now = clock.instant()
        )

        val storedMovie = database.portableSnapshotDao().readSnapshot().media.first { it.localMediaId == movieMediaId }
        val storedSeries = database.portableSnapshotDao().readSnapshot().media.first {
            it.localMediaId == seriesMediaId
        }

        assertEquals(true, storedMovie.isFavorite)
        assertEquals(false, storedSeries.isFavorite)
    }

    @Test
    fun versionOneSchemaOperationsVerifyDatabaseIntegrity() = runBlocking {
        val mediaId = insertMediaFixture(MediaSource.TMDB, MediaType.MOVIE, "1001", "Baseline Movie")
        database.detailsDao().storeDetails(
            candidate = MediaEntity(
                mediaType = MediaType.MOVIE,
                title = "Baseline Movie",
                originalTitle = null,
                overview = "Overview",
                posterUrl = null,
                releaseDate = LocalDate.parse("2026-01-01"),
                createdAt = clock.instant(),
                metadataUpdatedAt = clock.instant()
            ),
            source = MediaSource.TMDB,
            externalId = "1001",
            details = MediaDetailsEntity(
                localMediaId = mediaId,
                backdropUrl = null,
                productionStatus = "Released",
                originalLanguage = "en",
                runtimeMinutes = 120,
                episodeRuntimeMinutes = null,
                numberOfSeasons = null,
                numberOfEpisodes = null,
                detailsFetchedAt = clock.instant()
            ),
            genres = emptyList()
        )
        database.portableSnapshotDao().insertMembership(
            LibraryMembershipEntity(localMediaId = mediaId, addedAt = clock.instant())
        )

        val libraryItem = database.libraryDao().observeLibraryItem(MediaSource.TMDB, "1001").first()
        assertNotNull(libraryItem)
        assertEquals("Baseline Movie", libraryItem?.media?.title)

        database.ratingDao().setRating(MediaSource.TMDB, "1001", 9, clock.instant())
        val rating = database.ratingDao().observeRating(MediaSource.TMDB, "1001").first()
        assertEquals(9, rating?.ratingValue)
    }

    @Test
    fun backupV1RestoreAgainstVersionOneBaseline() = runBlocking {
        val backupDataStore = createBackupDataStore()
        val now = clock.instant()
        val doc = BackupDocument(
            formatId = BACKUP_FORMAT_ID,
            schemaVersion = 1,
            exportedAt = now,
            data = BackupData(
                media = listOf(
                    BackupMedia(
                        primaryRef = BackupRef(MediaSource.TMDB, "101"),
                        externalRefs = listOf(BackupRef(MediaSource.TMDB, "101")),
                        mediaType = MediaType.MOVIE,
                        title = "V1 Backup Movie",
                        originalTitle = null,
                        overview = null,
                        posterUrl = null,
                        releaseDate = LocalDate.parse("2025-01-01")
                    )
                ),
                seasons = emptyList(),
                episodes = emptyList(),
                library = listOf(BackupLibraryEntry(BackupRef(MediaSource.TMDB, "101"), now)),
                movieProgress = emptyList(),
                episodeProgress = emptyList(),
                ratings = listOf(BackupRating(BackupRef(MediaSource.TMDB, "101"), 8, now, now)),
                preferences = BackupPreferences(1, true, true, true)
            )
        )

        val plan = ValidatedBackupPlan(doc)
        backupDataStore.restore(plan)

        val library = database.libraryDao().observeLibraryItem(MediaSource.TMDB, "101").first()
        assertNotNull(library)
        assertEquals("V1 Backup Movie", library?.media?.title)

        val rating = database.ratingDao().observeRating(MediaSource.TMDB, "101").first()
        assertEquals(8, rating?.ratingValue)
    }

    private fun createBackupDataStore(): BackupDataStore {
        val notificationPrefs = DataStoreReleaseNotificationPreferences(
            ApplicationProvider.getApplicationContext(),
            database,
            database.portableSnapshotDao()
        )

        return BackupDataStore(
            database = database,
            snapshotDao = database.portableSnapshotDao(),
            releaseEventDao = database.releaseEventDao(),
            notificationPreferences = notificationPrefs
        )
    }

    private suspend fun insertMediaFixture(source: MediaSource, type: MediaType, extId: String, title: String): Long {
        val mediaId = database.portableSnapshotDao().insertMedia(
            MediaEntity(
                mediaType = type,
                title = title,
                originalTitle = null,
                overview = null,
                posterUrl = null,
                releaseDate = null,
                createdAt = clock.instant(),
                metadataUpdatedAt = clock.instant()
            )
        )
        database.portableSnapshotDao().insertExternalRef(
            ExternalRefEntity(localMediaId = mediaId, source = source, externalId = extId)
        )
        return mediaId
    }

    private companion object {
        const val TEST_DB = "bingee-baseline-1"
    }
}
