package com.cydoniancitizen.bingee.data.calendar

import com.cydoniancitizen.bingee.core.model.AnimeDetails
import com.cydoniancitizen.bingee.core.model.Episode
import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.MediaDetails
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.core.model.ReleaseEventType
import com.cydoniancitizen.bingee.core.model.ReleaseSubjectType
import com.cydoniancitizen.bingee.core.model.Season
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReleaseEventProjectorTest {
    private val projector = ReleaseEventProjector()
    private val updatedAt = Instant.parse("2026-08-03T12:00:00Z")
    private val seriesRef = ref("100")
    private val seasonRef = ref("200")

    @Test
    fun movieProjectionUsesDateOnlyStableMediaIdentityAndTracksChanges() {
        val first = projector.movie(movie(LocalDate.of(2027, 1, 2)), updatedAt)
        val changed = projector.movie(movie(LocalDate.of(2027, 1, 3)), updatedAt.plusSeconds(1))

        assertEquals(ReleaseSubjectType.MEDIA, first?.identity?.subjectType)
        assertEquals(ReleaseEventType.MOVIE_RELEASE, first?.identity?.eventType)
        assertEquals(first?.identity, changed?.identity)
        assertEquals(LocalDate.of(2027, 1, 3), changed?.eventDate)
        assertNull(projector.movie(movie(null), updatedAt))
    }

    @Test
    fun seasonProjectionSupportsSeasonZeroAndMissingDate() {
        val special = Season(
            seriesRef = seriesRef,
            externalRef = seasonRef,
            seasonNumber = 0,
            name = null,
            airDate = LocalDate.of(2026, 8, 3)
        )

        val event = projector.season(special, updatedAt)

        assertEquals(ReleaseSubjectType.SEASON, event?.identity?.subjectType)
        assertEquals(ReleaseEventType.SEASON_PREMIERE, event?.identity?.eventType)
        assertNull(projector.season(special.copy(airDate = null), updatedAt))
    }

    @Test
    fun episodeProjectionSupportsPastFutureAndMissingDateWithoutPersonalState() {
        val past = episode("300", 1, LocalDate.of(2020, 1, 1))
        val future = episode("301", 2, LocalDate.of(2030, 1, 1))

        assertEquals(LocalDate.of(2020, 1, 1), projector.episode(past, updatedAt)?.eventDate)
        assertEquals(ReleaseSubjectType.EPISODE, projector.episode(future, updatedAt)?.identity?.subjectType)
        assertEquals(ReleaseEventType.EPISODE_AIRING, projector.episode(future, updatedAt)?.identity?.eventType)
        assertNull(projector.episode(future.copy(airDate = null), updatedAt))
    }

    @Test
    fun animePremiereUsesOnlyReliableStartDateAndProviderQualifiedIdentity() {
        val animeRef = ExternalMediaRef(MediaSource.JIKAN, "52991")
        val first = projector.anime(
            AnimeDetails(
                externalRef = animeRef,
                title = "Anime",
                startDate = LocalDate.of(2026, 8, 5)
            ),
            updatedAt
        )
        val changed = projector.anime(
            AnimeDetails(
                externalRef = animeRef,
                title = "Anime",
                startDate = LocalDate.of(2026, 8, 6)
            ),
            updatedAt.plusSeconds(1)
        )

        assertEquals(MediaSource.JIKAN, first?.identity?.source)
        assertEquals(ReleaseSubjectType.MEDIA, first?.identity?.subjectType)
        assertEquals(ReleaseEventType.ANIME_PREMIERE, first?.identity?.eventType)
        assertEquals(first?.identity, changed?.identity)
        assertEquals(LocalDate.of(2026, 8, 6), changed?.eventDate)
        assertNull(
            projector.anime(
                AnimeDetails(externalRef = animeRef, title = "Anime", startDate = null),
                updatedAt
            )
        )
    }

    private fun movie(date: LocalDate?) = MediaDetails(
        externalRef = ref("10"),
        mediaType = MediaType.MOVIE,
        title = "Movie",
        releaseDate = date
    )

    private fun episode(id: String, number: Int, date: LocalDate?) = Episode(
        seriesRef = seriesRef,
        seasonRef = seasonRef,
        externalRef = ref(id),
        seasonNumber = 0,
        episodeNumber = number,
        title = "Episode $number",
        airDate = date
    )

    private fun ref(id: String) = ExternalMediaRef(MediaSource.TMDB, id)
}
