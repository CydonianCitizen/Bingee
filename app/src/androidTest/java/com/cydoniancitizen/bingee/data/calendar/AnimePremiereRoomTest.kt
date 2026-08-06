package com.cydoniancitizen.bingee.data.calendar

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cydoniancitizen.bingee.core.common.TestingAnimeFeatureAvailability
import com.cydoniancitizen.bingee.core.model.AnimeDetails
import com.cydoniancitizen.bingee.core.model.AnimeFormat
import com.cydoniancitizen.bingee.core.model.AnimeStatus
import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.MediaDetails
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.core.model.ReleaseEventType
import com.cydoniancitizen.bingee.core.model.ReleaseSubjectType
import com.cydoniancitizen.bingee.core.result.AppResult
import com.cydoniancitizen.bingee.data.library.local.AnimeDetailsEntity
import com.cydoniancitizen.bingee.data.library.local.BingeeDatabase
import com.cydoniancitizen.bingee.data.library.local.MediaEntity
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AnimePremiereRoomTest {
    private lateinit var database: BingeeDatabase
    private val projector = ReleaseEventProjector()
    private val now = Instant.parse("2026-08-05T10:00:00Z")
    private val premiereDate = LocalDate.of(2026, 8, 20)

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BingeeDatabase::class.java).build()
    }

    @After
    fun closeDatabase() = database.close()

    @Test
    fun startDateProjectsChangesAndRemovalWhileMembershipOnlyControlsVisibility() = runBlocking {
        val ref = ExternalMediaRef(MediaSource.JIKAN, "42")
        storeAnime(ref, "Zulu", premiereDate)
        database.releaseEventDao().reconcileAnime(
            ref,
            projector.anime(AnimeDetails(ref, "Zulu", startDate = premiereDate), now)
        )

        assertEquals(1, count("release_events"))
        assertTrue(database.releaseEventDao().observeActiveEvents(premiereDate).first().isEmpty())

        database.libraryDao().addExistingToLibrary(MediaSource.JIKAN, "42", now)
        val active = database.releaseEventDao().observeActiveEvents(premiereDate).first()
        assertEquals(1, active.size)
        assertEquals(ReleaseEventType.ANIME_PREMIERE, active.single().eventType)
        assertEquals(MediaSource.JIKAN, active.single().source)
        assertEquals("42", active.single().parentExternalId)

        val changed = premiereDate.plusDays(1)
        database.releaseEventDao().reconcileAnime(
            ref,
            projector.anime(AnimeDetails(ref, "Zulu", startDate = changed), now.plusSeconds(1))
        )
        assertEquals(changed.toString(), eventDate(ref))
        assertEquals(1, count("release_events"))

        database.libraryDao().removeMembership(MediaSource.JIKAN, "42")
        assertTrue(database.releaseEventDao().observeActiveEvents(LocalDate.MIN).first().isEmpty())
        assertEquals(1, count("release_events"))
        database.libraryDao().addExistingToLibrary(MediaSource.JIKAN, "42", now)
        assertEquals(1, database.releaseEventDao().observeActiveEvents(LocalDate.MIN).first().size)

        database.releaseEventDao().reconcileAnime(ref, null)
        assertEquals(0, count("release_events"))
    }

    @Test
    fun missingStartDateCreatesNoEventAndCalendarKeepsProviderQualifiedNavigationIdentity() = runBlocking {
        val missing = ExternalMediaRef(MediaSource.JIKAN, "7")
        storeAnime(missing, "Missing", null)
        database.releaseEventDao().reconcileAnime(
            missing,
            projector.anime(AnimeDetails(missing, "Missing", startDate = null), now)
        )
        assertEquals(0, count("release_events"))

        val anime = ExternalMediaRef(MediaSource.JIKAN, "42")
        val tmdb = ExternalMediaRef(MediaSource.TMDB, "42")
        storeAnime(anime, "Anime Alpha", premiereDate)
        database.libraryDao().addExistingToLibrary(MediaSource.JIKAN, "42", now)
        database.releaseEventDao().reconcileAnime(
            anime,
            projector.anime(AnimeDetails(anime, "Anime Alpha", startDate = premiereDate), now)
        )
        database.libraryDao().addToLibrary(
            MediaEntity(
                mediaType = MediaType.MOVIE,
                title = "Movie Alpha",
                originalTitle = null,
                overview = null,
                posterUrl = null,
                releaseDate = premiereDate,
                createdAt = now,
                metadataUpdatedAt = now
            ),
            MediaSource.TMDB,
            "42",
            now
        )
        database.releaseEventDao().reconcileMovie(
            tmdb,
            projector.movie(MediaDetails(tmdb, MediaType.MOVIE, "Movie Alpha", releaseDate = premiereDate), now)
        )

        val calendar = DefaultReleaseCalendarRepository(
            database.releaseEventDao(),
            Clock.fixed(now, ZoneOffset.UTC),
            TestingAnimeFeatureAvailability(isAvailable = true)
        )
        val result = calendar.getEvents(premiereDate, premiereDate, 20) as AppResult.Success
        assertEquals(2, result.value.size)
        assertEquals(listOf("Anime Alpha", "Movie Alpha"), result.value.map { it.title })
        val animeEvent = result.value.first()
        assertEquals(anime, animeEvent.mediaRef)
        assertEquals(anime, ExternalMediaRef(animeEvent.subject.source, animeEvent.subject.externalId))
        assertEquals(ReleaseSubjectType.MEDIA, animeEvent.subject.subjectType)
        assertEquals(ReleaseEventType.ANIME_PREMIERE, animeEvent.subject.eventType)
        assertFalse(result.value.any { it.mediaRef == tmdb && it.mediaType == MediaType.ANIME })
    }

    private suspend fun storeAnime(ref: ExternalMediaRef, title: String, startDate: LocalDate?) {
        database.animeDao().storeAnime(
            media = MediaEntity(
                mediaType = MediaType.ANIME,
                title = title,
                originalTitle = null,
                overview = null,
                posterUrl = null,
                releaseDate = startDate,
                createdAt = now,
                metadataUpdatedAt = now
            ),
            externalId = ref.externalId,
            details = AnimeDetailsEntity(
                localMediaId = 0,
                format = AnimeFormat.TV,
                providerStatus = AnimeStatus.FINISHED,
                englishTitle = title,
                japaneseTitle = null,
                synopsis = null,
                episodeCount = 12,
                duration = null,
                startDate = startDate,
                endDate = null,
                season = null,
                year = startDate?.year,
                providerScore = null,
                imageUrl = null,
                detailsUpdatedAt = now
            ),
            relations = emptyList()
        )
    }

    private fun eventDate(ref: ExternalMediaRef): String? = database.openHelper.readableDatabase.query(
        "SELECT event_date FROM release_events WHERE source = '${ref.source}' " +
            "AND subject_external_id = '${ref.externalId}'"
    ).use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }

    private fun count(table: String): Int =
        database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use {
            it.moveToFirst()
            it.getInt(0)
        }
}
