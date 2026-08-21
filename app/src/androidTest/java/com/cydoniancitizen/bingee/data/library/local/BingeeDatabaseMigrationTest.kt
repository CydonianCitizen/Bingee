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
    fun migrationOneToTwoAddsOnlyExplicitSeriesOverrideAndPreservesLegacyRows() {
        val name = "bingee-v1-to-v2"
        val legacy = helper.createDatabase(name, 1)
        legacy.execSQL(
            "INSERT INTO media_entries " +
                "(local_media_id, media_type, title, original_title, overview, poster_url, release_date, " +
                "created_at, metadata_updated_at, is_favorite) VALUES " +
                "(1, 'SERIES', 'Legacy', NULL, NULL, NULL, NULL, " +
                "'2026-08-01T00:00:00Z', '2026-08-01T00:00:00Z', 0)"
        )
        legacy.execSQL(
            "INSERT INTO external_refs (local_media_id, source, external_id) VALUES (1, 'TMDB', '9001')"
        )
        legacy.execSQL(
            "INSERT INTO series_watch_progress (local_media_id, watched_date, completed_at) " +
                "VALUES (1, '2026-08-01', '2026-08-01T00:00:00Z')"
        )
        legacy.close()

        val migrated = helper.runMigrationsAndValidate(name, 2, true, *ALL_MIGRATIONS)
        migrated.query("SELECT COUNT(*) FROM series_state_overrides").use { cursor ->
            cursor.moveToFirst()
            assertEquals(0, cursor.getInt(0))
        }
        migrated.query("SELECT COUNT(*) FROM series_watch_progress WHERE local_media_id = 1").use { cursor ->
            cursor.moveToFirst()
            assertEquals(1, cursor.getInt(0))
        }
        migrated.close()
    }

    @Test
    fun migrationTwoToThreePreservesLegacyGenreWithNullIdentity() {
        val name = "bingee-v2-to-v3"
        val legacy = helper.createDatabase(name, 2)
        legacy.execSQL(
            "INSERT INTO media_entries " +
                "(local_media_id, media_type, title, original_title, overview, poster_url, release_date, " +
                "created_at, metadata_updated_at, is_favorite) VALUES " +
                "(1, 'MOVIE', 'Legacy', NULL, NULL, NULL, NULL, " +
                "'2026-08-01T00:00:00Z', '2026-08-01T00:00:00Z', 0)"
        )
        legacy.execSQL("INSERT INTO media_genres (local_media_id, genre_order, name) VALUES (1, 2, 'Dramma')")
        legacy.close()

        val migrated = helper.runMigrationsAndValidate(name, 3, true, *ALL_MIGRATIONS)
        migrated.query(
            "SELECT genre_order, name, source, genre_id FROM media_genres WHERE local_media_id = 1"
        ).use { cursor ->
            cursor.moveToFirst()
            assertEquals(2, cursor.getInt(0))
            assertEquals("Dramma", cursor.getString(1))
            assertEquals(true, cursor.isNull(2))
            assertEquals(true, cursor.isNull(3))
        }
        migrated.close()
    }

    @Test
    fun migrationThreeToFourPreservesFavoriteAndLeavesLegacyChronologyUnknown() {
        val name = "bingee-v3-to-v4"
        val legacy = helper.createDatabase(name, 3)
        legacy.execSQL(
            "INSERT INTO media_entries " +
                "(local_media_id, media_type, title, original_title, overview, poster_url, release_date, " +
                "created_at, metadata_updated_at, is_favorite) VALUES " +
                "(1, 'MOVIE', 'Legacy Favorite', NULL, NULL, NULL, NULL, " +
                "'2026-08-01T00:00:00Z', '2026-08-01T00:00:00Z', 1)"
        )
        legacy.close()

        val migrated = helper.runMigrationsAndValidate(name, 4, true, *ALL_MIGRATIONS)
        migrated.query("SELECT is_favorite, favorite_added_at FROM media_entries WHERE local_media_id = 1").use {
            it.moveToFirst()
            assertEquals(1, it.getInt(0))
            assertEquals(true, it.isNull(1))
        }
        migrated.close()
    }

    @Test
    fun fullMigrationChainPreservesCanonicalPersonalDataThroughEveryVersion() {
        val name = "bingee-v1-to-v4"
        val legacy = helper.createDatabase(name, 1)
        legacy.execSQL(
            "INSERT INTO media_entries " +
                "(local_media_id, media_type, title, original_title, overview, poster_url, release_date, " +
                "created_at, metadata_updated_at, is_favorite) VALUES " +
                "(1, 'SERIES', 'Legacy Series', 'Serie Legacy', 'Legacy overview', 'poster', '2026-01-01', " +
                "'2026-08-01T00:00:00Z', '2026-08-01T00:00:00Z', 1)"
        )
        legacy.execSQL(
            "INSERT INTO media_entries " +
                "(local_media_id, media_type, title, original_title, overview, poster_url, release_date, " +
                "created_at, metadata_updated_at, is_favorite) VALUES " +
                "(2, 'MOVIE', 'Legacy Movie', NULL, NULL, NULL, '2026-02-02', " +
                "'2026-08-02T00:00:00Z', '2026-08-02T00:00:00Z', 0)"
        )
        legacy.execSQL(
            "INSERT INTO external_refs (local_media_id, source, external_id) VALUES " +
                "(1, 'TMDB', '9001'), (1, 'IMDB', 'tt9001'), (2, 'TMDB', '9002')"
        )
        legacy.execSQL(
            "INSERT INTO library_entries (local_media_id, added_at) VALUES " +
                "(1, '2026-08-01T00:00:00Z'), (2, '2026-08-02T00:00:00Z')"
        )
        legacy.execSQL(
            "INSERT INTO media_genres (local_media_id, genre_order, name) VALUES " +
                "(1, 0, 'Dramma'), (1, 1, 'Fantascienza'), (2, 0, 'Commedia')"
        )
        legacy.execSQL(
            "INSERT INTO seasons " +
                "(local_season_id, local_media_id, source, external_id, season_number, name, overview, poster_url, " +
                "air_date, episode_count, metadata_updated_at, episodes_fetched_at) VALUES " +
                "(1, 1, 'TMDB', '8001', 1, 'Stagione 1', NULL, NULL, '2026-03-01', 2, " +
                "'2026-08-01T00:00:00Z', '2026-08-01T00:00:00Z')"
        )
        legacy.execSQL(
            "INSERT INTO episodes " +
                "(local_episode_id, local_season_id, source, external_id, episode_number, title, overview, " +
                "air_date, runtime_minutes, still_url, metadata_updated_at) VALUES " +
                "(1, 1, 'TMDB', '7001', 1, 'Primo', NULL, '2026-03-01', 45, NULL, '2026-08-01T00:00:00Z'), " +
                "(2, 1, 'TMDB', '7002', 2, 'Secondo', NULL, '2026-03-08', 47, NULL, '2026-08-01T00:00:00Z')"
        )
        legacy.execSQL(
            "INSERT INTO episode_watch_progress (local_episode_id, watched_at) VALUES (1, '2026-07-01T20:00:00Z')"
        )
        legacy.execSQL(
            "INSERT INTO movie_watch_progress (local_media_id, watched_at, watched_date) " +
                "VALUES (2, '2026-07-02T21:00:00Z', '2026-07-02')"
        )
        legacy.execSQL(
            "INSERT INTO series_watch_progress (local_media_id, watched_date, completed_at) " +
                "VALUES (1, '2026-08-01', '2026-08-01T00:00:00Z')"
        )
        legacy.execSQL(
            "INSERT INTO media_ratings (local_media_id, rating_value, rated_at, updated_at) VALUES " +
                "(1, 9, '2026-06-01T09:00:00Z', '2026-06-10T09:00:00Z')"
        )
        legacy.close()

        // Room validates the migrated database against canonical v4 -- columns, indices and foreign
        // keys -- and fails the call if the chain diverges from the exported schema.
        val migrated = helper.runMigrationsAndValidate(name, 4, true, *ALL_MIGRATIONS)

        migrated.query(
            "SELECT media_type, title, original_title, overview, poster_url, release_date, created_at, " +
                "metadata_updated_at, is_favorite, favorite_added_at FROM media_entries ORDER BY local_media_id"
        ).use { cursor ->
            cursor.moveToFirst()
            assertEquals("SERIES", cursor.getString(0))
            assertEquals("Legacy Series", cursor.getString(1))
            assertEquals("Serie Legacy", cursor.getString(2))
            assertEquals("Legacy overview", cursor.getString(3))
            assertEquals("poster", cursor.getString(4))
            assertEquals("2026-01-01", cursor.getString(5))
            assertEquals("2026-08-01T00:00:00Z", cursor.getString(6))
            assertEquals("2026-08-01T00:00:00Z", cursor.getString(7))
            assertEquals(1, cursor.getInt(8))
            // v4 chronology is evidence the legacy database never recorded, so a migrated favorite
            // stays unknown instead of borrowing created_at or a watch timestamp.
            assertEquals(true, cursor.isNull(9))
            cursor.moveToNext()
            assertEquals("MOVIE", cursor.getString(0))
            assertEquals("Legacy Movie", cursor.getString(1))
            assertEquals(0, cursor.getInt(8))
            assertEquals(true, cursor.isNull(9))
        }
        migrated.query("SELECT source, external_id FROM external_refs ORDER BY source, external_id").use { cursor ->
            val refs = buildList {
                while (cursor.moveToNext()) add("${cursor.getString(0)}:${cursor.getString(1)}")
            }
            assertEquals(listOf("IMDB:tt9001", "TMDB:9001", "TMDB:9002"), refs)
        }
        migrated.query("SELECT local_media_id, added_at FROM library_entries ORDER BY local_media_id").use { cursor ->
            cursor.moveToFirst()
            assertEquals(1, cursor.getInt(0))
            assertEquals("2026-08-01T00:00:00Z", cursor.getString(1))
            cursor.moveToNext()
            assertEquals(2, cursor.getInt(0))
            assertEquals("2026-08-02T00:00:00Z", cursor.getString(1))
        }
        // v3 added canonical genre identity. Localised names carry no identity evidence, so the rows
        // keep their names and order while source and genre_id stay unknown.
        migrated.query(
            "SELECT local_media_id, genre_order, name, source, genre_id FROM media_genres " +
                "ORDER BY local_media_id, genre_order"
        ).use { cursor ->
            listOf(
                Triple(1, 0, "Dramma"),
                Triple(1, 1, "Fantascienza"),
                Triple(2, 0, "Commedia")
            ).forEach { (mediaId, order, genreName) ->
                cursor.moveToNext()
                assertEquals(mediaId, cursor.getInt(0))
                assertEquals(order, cursor.getInt(1))
                assertEquals(genreName, cursor.getString(2))
                assertEquals(true, cursor.isNull(3))
                assertEquals(true, cursor.isNull(4))
            }
        }
        migrated.query("SELECT external_id, season_number, episode_count FROM seasons").use { cursor ->
            cursor.moveToFirst()
            assertEquals("8001", cursor.getString(0))
            assertEquals(1, cursor.getInt(1))
            assertEquals(2, cursor.getInt(2))
        }
        migrated.query(
            "SELECT external_id, episode_number, title, air_date, runtime_minutes FROM episodes " +
                "ORDER BY episode_number"
        ).use { cursor ->
            cursor.moveToFirst()
            assertEquals("7001", cursor.getString(0))
            assertEquals("Primo", cursor.getString(2))
            assertEquals("2026-03-01", cursor.getString(3))
            assertEquals(45, cursor.getInt(4))
            cursor.moveToNext()
            assertEquals("7002", cursor.getString(0))
            assertEquals(2, cursor.getInt(1))
        }
        migrated.query("SELECT local_episode_id, watched_at FROM episode_watch_progress").use { cursor ->
            cursor.moveToFirst()
            assertEquals(1, cursor.getInt(0))
            assertEquals("2026-07-01T20:00:00Z", cursor.getString(1))
        }
        migrated.query("SELECT watched_at, watched_date FROM movie_watch_progress WHERE local_media_id = 2").use {
            it.moveToFirst()
            assertEquals("2026-07-02T21:00:00Z", it.getString(0))
            assertEquals("2026-07-02", it.getString(1))
        }
        migrated.query("SELECT watched_date, completed_at FROM series_watch_progress WHERE local_media_id = 1").use {
            it.moveToFirst()
            assertEquals("2026-08-01", it.getString(0))
            assertEquals("2026-08-01T00:00:00Z", it.getString(1))
        }
        migrated.query("SELECT rating_value, rated_at, updated_at FROM media_ratings WHERE local_media_id = 1").use {
            it.moveToFirst()
            assertEquals(9, it.getInt(0))
            assertEquals("2026-06-01T09:00:00Z", it.getString(1))
            assertEquals("2026-06-10T09:00:00Z", it.getString(2))
        }
        // v2 introduced the explicit Abandoned override. A v1 database carries no such statement, so
        // the chain must leave the table empty rather than deriving one from completion state.
        migrated.query("SELECT COUNT(*) FROM series_state_overrides").use { cursor ->
            cursor.moveToFirst()
            assertEquals(0, cursor.getInt(0))
        }
        migrated.close()
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
