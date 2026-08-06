package com.cydoniancitizen.bingee.data.library.local

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.cydoniancitizen.bingee.core.model.AnimeCompletionOrigin
import com.cydoniancitizen.bingee.core.model.AnimeFormat
import com.cydoniancitizen.bingee.core.model.AnimeStatus
import com.cydoniancitizen.bingee.core.model.LinkedMediaIdentity
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.data.importexport.BACKUP_FORMAT_ID
import com.cydoniancitizen.bingee.data.importexport.BACKUP_SCHEMA_VERSION
import com.cydoniancitizen.bingee.data.importexport.BACKUP_SCHEMA_VERSION_V3
import com.cydoniancitizen.bingee.data.importexport.BackupAnimeDetails
import com.cydoniancitizen.bingee.data.importexport.BackupAnimeProgress
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
import com.cydoniancitizen.bingee.data.link.RoomMediaLinkRepository
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
            "media_ratings",
            "release_events",
            "calendar_refresh_state",
            "notification_deliveries",
            "portable_preferences",
            "import_provenance_refs",
            "anime_details",
            "anime_progress",
            "anime_relations",
            "media_link_groups",
            "media_link_members",
            "media_link_audit",
            "media_link_audit_members"
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
    fun daoSmokeTestsOnVersionOneBaseline() = runBlocking {
        val mediaId = database.portableSnapshotDao().insertMedia(
            MediaEntity(
                mediaType = MediaType.MOVIE,
                title = "Baseline Movie",
                originalTitle = "Baseline Original",
                overview = "Overview",
                posterUrl = null,
                releaseDate = LocalDate.parse("2026-01-01"),
                createdAt = clock.instant(),
                metadataUpdatedAt = clock.instant()
            )
        )
        database.portableSnapshotDao().insertExternalRef(
            ExternalRefEntity(localMediaId = mediaId, source = MediaSource.TMDB, externalId = "1001")
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

        database.portableSnapshotDao().insertAnimeDetails(
            AnimeDetailsEntity(
                localMediaId = mediaId,
                format = AnimeFormat.TV,
                providerStatus = AnimeStatus.FINISHED,
                englishTitle = "Baseline Anime",
                japaneseTitle = null,
                synopsis = "Anime Overview",
                episodeCount = 12,
                duration = "24 min",
                startDate = LocalDate.parse("2026-01-01"),
                endDate = LocalDate.parse("2026-03-31"),
                season = "WINTER",
                year = 2026,
                providerScore = 8.5,
                imageUrl = null,
                detailsUpdatedAt = clock.instant()
            )
        )
        val animeRelation = database.animeDao().observeAnime("1001").first()
        assertNotNull(animeRelation)
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
                preferences = BackupPreferences(1, true, true, true),
                animeDetails = emptyList(),
                animeRelations = emptyList(),
                animeProgress = emptyList()
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

    @Test
    fun backupV2RestoreAgainstVersionOneBaseline() = runBlocking {
        val backupDataStore = createBackupDataStore()
        val now = clock.instant()
        val animeRef = BackupRef(MediaSource.JIKAN, "5001")
        val doc = BackupDocument(
            formatId = BACKUP_FORMAT_ID,
            schemaVersion = BACKUP_SCHEMA_VERSION,
            exportedAt = now,
            data = BackupData(
                media = listOf(
                    BackupMedia(
                        primaryRef = animeRef,
                        externalRefs = listOf(animeRef),
                        mediaType = MediaType.ANIME,
                        title = "V2 Anime Movie",
                        originalTitle = null,
                        overview = null,
                        posterUrl = null,
                        releaseDate = LocalDate.parse("2026-01-01")
                    )
                ),
                seasons = emptyList(),
                episodes = emptyList(),
                library = listOf(BackupLibraryEntry(animeRef, now)),
                movieProgress = emptyList(),
                episodeProgress = emptyList(),
                ratings = listOf(BackupRating(animeRef, 10, now, now)),
                preferences = BackupPreferences(1, true, true, true),
                animeDetails = listOf(
                    BackupAnimeDetails(
                        mediaRef = animeRef,
                        format = AnimeFormat.MOVIE,
                        status = AnimeStatus.FINISHED,
                        englishTitle = "V2 Anime Movie English",
                        japaneseTitle = "V2 Japanese",
                        synopsis = "Synopsis",
                        episodeCount = 1,
                        duration = "120 min",
                        startDate = LocalDate.parse("2026-01-01"),
                        endDate = LocalDate.parse("2026-01-01"),
                        season = null,
                        year = 2026,
                        providerScore = 9.0,
                        posterUrl = null
                    )
                ),
                animeRelations = emptyList(),
                animeProgress = listOf(
                    BackupAnimeProgress(
                        mediaRef = animeRef,
                        watchedEpisodeCount = 1,
                        completedAt = now,
                        completionOrigin = AnimeCompletionOrigin.EXPLICIT,
                        updatedAt = now
                    )
                )
            )
        )

        val plan = ValidatedBackupPlan(doc)
        backupDataStore.restore(plan)

        val anime = database.animeDao().observeAnime("5001").first()
        assertNotNull(anime)
        assertEquals("V2 Anime Movie English", anime?.details?.englishTitle)
        assertEquals(1, anime?.progress?.watchedEpisodeCount)
    }

    @Test
    fun backupV3RestoreAgainstVersionOneBaseline() = runBlocking {
        val backupDataStore = createBackupDataStore()
        val linkRepo = RoomMediaLinkRepository(
            database = database,
            mediaLinkDao = database.mediaLinkDao(),
            clock = clock,
            uuidGenerator = { "link-uuid-v3" }
        )

        val tmdbMediaId = insertMediaFixture(MediaSource.TMDB, MediaType.MOVIE, "201", "Spirited Away TMDB")
        val jikanMediaId = insertMediaFixture(MediaSource.JIKAN, MediaType.ANIME, "202", "Spirited Away JIKAN")

        val first = LinkedMediaIdentity(MediaSource.TMDB, MediaType.MOVIE, "201")
        val second = LinkedMediaIdentity(MediaSource.JIKAN, MediaType.ANIME, "202")
        linkRepo.createLink(first, second, preferredPresentation = first)

        val exported = backupDataStore.readPortableData()
        assertEquals(1, exported.mediaLinkGroups.size)
        assertEquals(1, exported.mediaLinkAudit.size)

        val doc = BackupDocument(BACKUP_FORMAT_ID, BACKUP_SCHEMA_VERSION_V3, clock.instant(), exported)
        val plan = ValidatedBackupPlan(doc)

        backupDataStore.restore(plan)

        val countGroups = database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM media_link_groups").use { c ->
            c.moveToFirst()
            c.getInt(0)
        }
        assertEquals(1, countGroups)
    }

    @Test
    fun failedRestoreRollsBackTransactionOnVersionOneBaseline() = runBlocking {
        val backupDataStore = createBackupDataStore()
        insertMediaFixture(MediaSource.TMDB, MediaType.MOVIE, "301", "Pre-existing Movie")

        val doc = BackupDocument(
            formatId = BACKUP_FORMAT_ID,
            schemaVersion = 1,
            exportedAt = clock.instant(),
            data = BackupData(
                media = listOf(
                    BackupMedia(
                        primaryRef = BackupRef(MediaSource.TMDB, "999"),
                        externalRefs = listOf(BackupRef(MediaSource.TMDB, "999")),
                        mediaType = MediaType.MOVIE,
                        title = "New Movie",
                        originalTitle = null,
                        overview = null,
                        posterUrl = null,
                        releaseDate = null
                    )
                ),
                seasons = emptyList(),
                episodes = emptyList(),
                library = emptyList(),
                movieProgress = emptyList(),
                episodeProgress = emptyList(),
                ratings = emptyList(),
                preferences = BackupPreferences(1, true, true, true),
                animeDetails = emptyList(),
                animeRelations = emptyList(),
                animeProgress = emptyList()
            )
        )

        val plan = ValidatedBackupPlan(doc)
        try {
            backupDataStore.restore(plan, failureInjector = { stage ->
                if (stage == RestoreStage.EXTERNAL_REFERENCES) {
                    throw IllegalStateException("TEST_RESTORE_FAILURE")
                }
            })
            fail("Expected restore failure")
        } catch (_: IllegalStateException) {
        }

        val item = database.libraryDao().observeLibraryItem(MediaSource.TMDB, "301").first()
        assertNotNull(item)
        assertEquals("Pre-existing Movie", item?.media?.title)
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
            notificationPreferences = notificationPrefs,
            mediaLinkDao = database.mediaLinkDao()
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
