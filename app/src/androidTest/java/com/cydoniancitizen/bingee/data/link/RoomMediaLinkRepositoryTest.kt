package com.cydoniancitizen.bingee.data.link

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.LinkedMediaIdentity
import com.cydoniancitizen.bingee.core.model.MediaLinkAuditAction
import com.cydoniancitizen.bingee.core.model.MediaLinkAuditOrigin
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.core.result.AppError
import com.cydoniancitizen.bingee.core.result.AppResult
import com.cydoniancitizen.bingee.data.library.local.BingeeDatabase
import com.cydoniancitizen.bingee.data.library.local.ExternalRefEntity
import com.cydoniancitizen.bingee.data.library.local.MediaEntity
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomMediaLinkRepositoryTest {

    private lateinit var database: BingeeDatabase
    private lateinit var repository: RoomMediaLinkRepository
    private val clock = Clock.fixed(Instant.parse("2026-08-05T12:00:00Z"), ZoneOffset.UTC)
    private var testUuidCounter = 1

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            BingeeDatabase::class.java
        ).allowMainThreadQueries().build()

        repository = RoomMediaLinkRepository(
            database = database,
            mediaLinkDao = database.mediaLinkDao(),
            clock = clock,
            uuidGenerator = { "test-uuid-${testUuidCounter++}" }
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun createLinkValidPairSucceedsAndWritesAudit() = runBlocking {
        val tmdbId = insertMediaFixture(MediaSource.TMDB, MediaType.MOVIE, "129", "Spirited Away")
        val jikanId = insertMediaFixture(MediaSource.JIKAN, MediaType.ANIME, "199", "Sen to Chihiro")

        val first = LinkedMediaIdentity(MediaSource.TMDB, MediaType.MOVIE, "129")
        val second = LinkedMediaIdentity(MediaSource.JIKAN, MediaType.ANIME, "199")

        val result = repository.createLink(first, second, preferredPresentation = first)
        assertTrue(result is AppResult.Success)
        val group = (result as AppResult.Success).value

        assertEquals("test-uuid-1", group.groupId.value)
        assertEquals(first, group.first)
        assertEquals(second, group.second)
        assertEquals(first, group.preferredPresentation)

        val observed = repository.observeLinkForMedia(first).first()
        assertNotNull(observed)
        assertEquals(group.groupId, observed!!.groupId)

        val auditList = database.mediaLinkDao().getAuditTrail()
        assertEquals(1, auditList.size)
        assertEquals(MediaLinkAuditAction.LINKED, auditList[0].action)
        assertEquals("test-uuid-1", auditList[0].groupUuid)

        val auditMembers = database.mediaLinkDao().getAuditMembers(auditList[0].auditId)
        assertEquals(2, auditMembers.size)
    }

    @Test
    fun createLinkSelfLinkOrSameMediaIdRejected() = runBlocking {
        val first = LinkedMediaIdentity(MediaSource.TMDB, MediaType.MOVIE, "129")
        val result = repository.createLink(first, first, preferredPresentation = first)
        assertTrue(result is AppResult.Failure)
        assertEquals(AppError.LinkError.SelfLinkProhibited, (result as AppResult.Failure).error)
    }

    @Test
    fun createLinkMissingMediaRejected() = runBlocking {
        val first = LinkedMediaIdentity(MediaSource.TMDB, MediaType.MOVIE, "999999")
        val second = LinkedMediaIdentity(MediaSource.JIKAN, MediaType.ANIME, "199")
        insertMediaFixture(MediaSource.JIKAN, MediaType.ANIME, "199", "Anime")

        val result = repository.createLink(first, second, preferredPresentation = second)
        assertTrue(result is AppResult.Failure)
        assertEquals(AppError.LinkError.MediaNotFound, (result as AppResult.Failure).error)
    }

    @Test
    fun createLinkAlreadyLinkedMediaRejected() = runBlocking {
        insertMediaFixture(MediaSource.TMDB, MediaType.MOVIE, "101", "Movie 1")
        insertMediaFixture(MediaSource.JIKAN, MediaType.ANIME, "201", "Anime 1")
        insertMediaFixture(MediaSource.TMDB, MediaType.MOVIE, "102", "Movie 2")

        val m1 = LinkedMediaIdentity(MediaSource.TMDB, MediaType.MOVIE, "101")
        val a1 = LinkedMediaIdentity(MediaSource.JIKAN, MediaType.ANIME, "201")
        val m2 = LinkedMediaIdentity(MediaSource.TMDB, MediaType.MOVIE, "102")

        val res1 = repository.createLink(m1, a1, preferredPresentation = m1)
        assertTrue(res1 is AppResult.Success)

        val res2 = repository.createLink(m1, m2, preferredPresentation = m1)
        assertTrue(res2 is AppResult.Failure)
        assertEquals(AppError.LinkError.AlreadyLinked, (res2 as AppResult.Failure).error)
    }

    @Test
    fun changePreferredPresentationSwitchesPreferredMemberAndWritesAudit() = runBlocking {
        insertMediaFixture(MediaSource.TMDB, MediaType.MOVIE, "129", "Spirited Away")
        insertMediaFixture(MediaSource.JIKAN, MediaType.ANIME, "199", "Sen to Chihiro")

        val first = LinkedMediaIdentity(MediaSource.TMDB, MediaType.MOVIE, "129")
        val second = LinkedMediaIdentity(MediaSource.JIKAN, MediaType.ANIME, "199")

        val createRes = repository.createLink(first, second, preferredPresentation = first)
        val group = (createRes as AppResult.Success).value

        val changeRes = repository.changePreferredPresentation(group.groupId, preferredPresentation = second)
        assertTrue(changeRes is AppResult.Success)
        val updatedGroup = (changeRes as AppResult.Success).value

        assertEquals(second, updatedGroup.preferredPresentation)

        val auditList = database.mediaLinkDao().getAuditTrail()
        assertEquals(2, auditList.size)
        assertEquals(MediaLinkAuditAction.PREFERRED_PRESENTATION_CHANGED, auditList[1].action)
        assertEquals(MediaSource.JIKAN, auditList[1].preferredSource)
    }

    @Test
    fun changePreferredPresentationSamePreferredIsIdempotent() = runBlocking {
        insertMediaFixture(MediaSource.TMDB, MediaType.MOVIE, "129", "Spirited Away")
        insertMediaFixture(MediaSource.JIKAN, MediaType.ANIME, "199", "Sen to Chihiro")

        val first = LinkedMediaIdentity(MediaSource.TMDB, MediaType.MOVIE, "129")
        val second = LinkedMediaIdentity(MediaSource.JIKAN, MediaType.ANIME, "199")

        val createRes = repository.createLink(first, second, preferredPresentation = first)
        val group = (createRes as AppResult.Success).value

        val changeRes = repository.changePreferredPresentation(group.groupId, preferredPresentation = first)
        assertTrue(changeRes is AppResult.Success)

        val auditList = database.mediaLinkDao().getAuditTrail()
        assertEquals(1, auditList.size)
    }

    @Test
    fun unlinkRemovesLinkGroupAndPreservesAuditAndMediaRows() = runBlocking {
        val tmdbId = insertMediaFixture(MediaSource.TMDB, MediaType.MOVIE, "129", "Spirited Away")
        val jikanId = insertMediaFixture(MediaSource.JIKAN, MediaType.ANIME, "199", "Sen to Chihiro")

        val first = LinkedMediaIdentity(MediaSource.TMDB, MediaType.MOVIE, "129")
        val second = LinkedMediaIdentity(MediaSource.JIKAN, MediaType.ANIME, "199")

        val createRes = repository.createLink(first, second, preferredPresentation = first)
        val group = (createRes as AppResult.Success).value

        val unlinkRes = repository.unlink(group.groupId)
        assertTrue(unlinkRes is AppResult.Success)

        val observed = repository.observeLinkForMedia(first).first()
        assertNull(observed)

        assertEquals(1, countTable("media_entries"))
        assertEquals(2, database.mediaLinkDao().getAuditTrail().size)
        assertEquals(MediaLinkAuditAction.UNLINKED, database.mediaLinkDao().getAuditTrail()[1].action)

        val repeatUnlink = repository.unlink(group.groupId)
        assertTrue(repeatUnlink is AppResult.Failure)
        assertEquals(AppError.LinkError.LinkGroupNotFound, (repeatUnlink as AppResult.Failure).error)
    }

    @Test
    fun failureInjectionProvesRollbackOnCreateLink() = runBlocking {
        insertMediaFixture(MediaSource.TMDB, MediaType.MOVIE, "129", "Spirited Away")
        insertMediaFixture(MediaSource.JIKAN, MediaType.ANIME, "199", "Sen to Chihiro")

        val first = LinkedMediaIdentity(MediaSource.TMDB, MediaType.MOVIE, "129")
        val second = LinkedMediaIdentity(MediaSource.JIKAN, MediaType.ANIME, "199")

        repository.failureInjector = LinkTransactionFailureInjector { stage ->
            if (stage == LinkTransactionStage.AFTER_MEMBERS_INSERT) {
                throw IllegalStateException("TEST_INJECTED_FAILURE")
            }
        }

        try {
            repository.createLink(first, second, preferredPresentation = first)
        } catch (_: IllegalStateException) {
        }

        assertEquals(0, countTable("media_link_groups"))
        assertEquals(0, countTable("media_link_members"))
        assertEquals(0, countTable("media_link_audit"))
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
