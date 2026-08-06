package com.cydoniancitizen.bingee.data.importexport

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cydoniancitizen.bingee.core.model.LinkedMediaIdentity
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.data.library.local.BingeeDatabase
import com.cydoniancitizen.bingee.data.library.local.ExternalRefEntity
import com.cydoniancitizen.bingee.data.library.local.MediaEntity
import com.cydoniancitizen.bingee.data.link.RoomMediaLinkRepository
import com.cydoniancitizen.bingee.data.settings.DataStoreReleaseNotificationPreferences
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupLinkRestoreTest {

    private lateinit var database: BingeeDatabase
    private lateinit var backupDataStore: BackupDataStore
    private lateinit var linkRepository: RoomMediaLinkRepository
    private val clock = Clock.fixed(Instant.parse("2026-08-05T12:00:00Z"), ZoneOffset.UTC)

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            BingeeDatabase::class.java
        ).allowMainThreadQueries().build()

        val notificationPrefs = DataStoreReleaseNotificationPreferences(
            ApplicationProvider.getApplicationContext(),
            database,
            database.portableSnapshotDao()
        )

        backupDataStore = BackupDataStore(
            database = database,
            snapshotDao = database.portableSnapshotDao(),
            releaseEventDao = database.releaseEventDao(),
            notificationPreferences = notificationPrefs,
            mediaLinkDao = database.mediaLinkDao()
        )

        linkRepository = RoomMediaLinkRepository(
            database = database,
            mediaLinkDao = database.mediaLinkDao(),
            clock = clock,
            uuidGenerator = { "link-1" }
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun restoreV2BackupClearsPreExistingLinksAndAudit() = runBlocking {
        val tmdbId = insertMediaFixture(MediaSource.TMDB, MediaType.MOVIE, "129", "Spirited Away")
        val jikanId = insertMediaFixture(MediaSource.JIKAN, MediaType.ANIME, "199", "Sen to Chihiro")

        val first = LinkedMediaIdentity(MediaSource.TMDB, MediaType.MOVIE, "129")
        val second = LinkedMediaIdentity(MediaSource.JIKAN, MediaType.ANIME, "199")

        linkRepository.createLink(first, second, preferredPresentation = first)
        assertEquals(1, countTable("media_link_groups"))
        assertEquals(2, countTable("media_link_members"))
        assertEquals(1, countTable("media_link_audit"))

        val backupData = BackupData(
            media = listOf(
                BackupMedia(
                    primaryRef = BackupRef(MediaSource.TMDB, "129"),
                    externalRefs = listOf(BackupRef(MediaSource.TMDB, "129")),
                    mediaType = MediaType.MOVIE,
                    title = "Restored Movie",
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

        val doc = BackupDocument(
            formatId = BACKUP_FORMAT_ID,
            schemaVersion = 2,
            exportedAt = Instant.parse("2026-08-05T12:00:00Z"),
            data = backupData
        )

        val plan = ValidatedBackupPlan(doc)
        backupDataStore.restore(plan)

        assertEquals(0, countTable("media_link_groups"))
        assertEquals(0, countTable("media_link_members"))
        assertEquals(0, countTable("media_link_audit"))
        assertEquals(1, countTable("media_entries"))
    }

    @Test
    fun failedRestoreRollsBackAndPreservesOriginalLinks() = runBlocking {
        insertMediaFixture(MediaSource.TMDB, MediaType.MOVIE, "129", "Spirited Away")
        insertMediaFixture(MediaSource.JIKAN, MediaType.ANIME, "199", "Sen to Chihiro")

        val first = LinkedMediaIdentity(MediaSource.TMDB, MediaType.MOVIE, "129")
        val second = LinkedMediaIdentity(MediaSource.JIKAN, MediaType.ANIME, "199")

        linkRepository.createLink(first, second, preferredPresentation = first)
        assertEquals(1, countTable("media_link_groups"))

        val backupData = BackupData(
            media = listOf(
                BackupMedia(
                    primaryRef = BackupRef(MediaSource.TMDB, "129"),
                    externalRefs = listOf(BackupRef(MediaSource.TMDB, "129")),
                    mediaType = MediaType.MOVIE,
                    title = "Restored Movie",
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

        val doc = BackupDocument(
            formatId = BACKUP_FORMAT_ID,
            schemaVersion = 2,
            exportedAt = Instant.parse("2026-08-05T12:00:00Z"),
            data = backupData
        )

        val plan = ValidatedBackupPlan(doc)

        try {
            backupDataStore.restore(plan, failureInjector = { stage ->
                if (stage == RestoreStage.EXTERNAL_REFERENCES) {
                    throw IllegalStateException("TEST_INJECTED_RESTORE_FAILURE")
                }
            })
            fail("Expected restore failure")
        } catch (_: IllegalStateException) {
        }

        assertEquals(1, countTable("media_link_groups"))
        assertEquals(2, countTable("media_link_members"))
        assertEquals(1, countTable("media_link_audit"))
        assertEquals(2, countTable("media_entries"))
    }

    @Test
    fun exportAndRestoreV3PreservesLinksAndAudit() = runBlocking {
        insertMediaFixture(MediaSource.TMDB, MediaType.MOVIE, "129", "Spirited Away")
        insertMediaFixture(MediaSource.JIKAN, MediaType.ANIME, "199", "Sen to Chihiro")

        val first = LinkedMediaIdentity(MediaSource.TMDB, MediaType.MOVIE, "129")
        val second = LinkedMediaIdentity(MediaSource.JIKAN, MediaType.ANIME, "199")

        linkRepository.createLink(first, second, preferredPresentation = first)

        val exportedData = backupDataStore.readPortableData()
        assertEquals(1, exportedData.mediaLinkGroups.size)
        assertEquals(1, exportedData.mediaLinkAudit.size)
        assertEquals(2, exportedData.media.size) // Both linked members included in media snapshot!

        val doc = BackupDocument(BACKUP_FORMAT_ID, BACKUP_SCHEMA_VERSION, clock.instant(), exportedData)
        val plan = ValidatedBackupPlan(doc)

        backupDataStore.restore(plan)

        assertEquals(1, countTable("media_link_groups"))
        assertEquals(2, countTable("media_link_members"))
        assertEquals(1, countTable("media_link_audit"))
        assertEquals(2, countTable("media_entries"))
    }

    @Test
    fun v3RollbackOnLinkFailurePreservesOriginalState() = runBlocking {
        insertMediaFixture(MediaSource.TMDB, MediaType.MOVIE, "129", "Spirited Away")
        insertMediaFixture(MediaSource.JIKAN, MediaType.ANIME, "199", "Sen to Chihiro")

        val first = LinkedMediaIdentity(MediaSource.TMDB, MediaType.MOVIE, "129")
        val second = LinkedMediaIdentity(MediaSource.JIKAN, MediaType.ANIME, "199")
        linkRepository.createLink(first, second, preferredPresentation = first)

        val exportedData = backupDataStore.readPortableData()
        val doc = BackupDocument(BACKUP_FORMAT_ID, BACKUP_SCHEMA_VERSION, clock.instant(), exportedData)
        val plan = ValidatedBackupPlan(doc)

        for (targetStage in listOf(
            RestoreStage.ACTIVE_LINK_GROUPS,
            RestoreStage.ACTIVE_LINK_MEMBERS,
            RestoreStage.LINK_AUDIT,
            RestoreStage.LINK_AUDIT_MEMBERS
        )) {
            try {
                backupDataStore.restore(plan, failureInjector = { stage ->
                    if (stage == targetStage) throw IllegalStateException("INJECTED_FAILURE_$stage")
                })
                fail("Expected failure at $targetStage")
            } catch (_: IllegalStateException) {
            }

            assertEquals(1, countTable("media_link_groups"))
            assertEquals(2, countTable("media_link_members"))
            assertEquals(1, countTable("media_link_audit"))
        }
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

    private fun countTable(table: String): Int =
        database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use { c ->
            c.moveToFirst()
            c.getInt(0)
        }
}
