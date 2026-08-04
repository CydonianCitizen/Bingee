package com.cydoniancitizen.bingee.core.model

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class ReleaseCalendarTest {
    private val today = LocalDate.of(2026, 8, 3)

    @Test
    fun windowStartsSevenCalendarDaysAgoAndHasNoFutureCutoff() {
        assertEquals(LocalDate.of(2026, 7, 27), ReleaseCalendarWindow().startDate(today))
    }

    @Test
    fun groupingOrdersDatesAndSameDateEpisodeSeasonMovieThenTitle() {
        val date = today.plusDays(1)
        val events = listOf(
            event("2", "Zulu", ReleaseSubjectType.MEDIA, ReleaseEventType.MOVIE_RELEASE, MediaType.MOVIE, date),
            event("3", "Alpha", ReleaseSubjectType.SEASON, ReleaseEventType.SEASON_PREMIERE, MediaType.SERIES, date),
            event("4", "Zulu", ReleaseSubjectType.EPISODE, ReleaseEventType.EPISODE_AIRING, MediaType.SERIES, date),
            event("1", "Older", ReleaseSubjectType.MEDIA, ReleaseEventType.MOVIE_RELEASE, MediaType.MOVIE, today)
        )

        val groups = groupReleaseEvents(events, today)

        assertEquals(listOf(today, date), groups.map { it.date })
        assertEquals(ReleaseDateCategory.TODAY, groups.first().category)
        assertEquals(
            listOf(ReleaseEventType.EPISODE_AIRING, ReleaseEventType.SEASON_PREMIERE, ReleaseEventType.MOVIE_RELEASE),
            groups.last().events.map { it.subject.eventType }
        )
    }

    private fun event(
        id: String,
        title: String,
        subjectType: ReleaseSubjectType,
        type: ReleaseEventType,
        mediaType: MediaType,
        date: LocalDate
    ) = ReleaseEvent(
        mediaRef = ExternalMediaRef(MediaSource.TMDB, "parent$id"),
        subject = ReleaseSubjectIdentity(MediaSource.TMDB, subjectType, id, type),
        mediaType = mediaType,
        eventDate = date,
        title = title,
        seasonNumber = if (subjectType == ReleaseSubjectType.MEDIA) null else 1,
        episodeNumber = if (subjectType == ReleaseSubjectType.EPISODE) 1 else null
    )
}
