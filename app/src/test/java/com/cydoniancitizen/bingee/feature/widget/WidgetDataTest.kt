package com.cydoniancitizen.bingee.feature.widget

import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.core.model.ReleaseEvent
import com.cydoniancitizen.bingee.core.model.ReleaseEventType
import com.cydoniancitizen.bingee.core.model.ReleaseSubjectIdentity
import com.cydoniancitizen.bingee.core.model.ReleaseSubjectType
import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetDataTest {
    private val today = LocalDate.of(2026, 9, 12)

    @Test
    fun upcomingSkipsPastReleasesAndKeepsTheNextTwoInCalendarOrder() {
        val events = listOf(
            movie("later", today.plusDays(5)),
            movie("yesterday", today.minusDays(1)),
            movie("tomorrow", today.plusDays(1)),
            movie("today", today)
        )

        assertEquals(listOf("today", "tomorrow"), selectUpcoming(events, today).map { it.title })
    }

    @Test
    fun dayLabelNamesTodayAndTomorrowAndFormatsLaterDates() {
        fun label(date: LocalDate) = widgetDayLabel(date, today, "Today", "Tomorrow", Locale.ENGLISH)

        assertEquals("Today", label(today))
        assertEquals("Tomorrow", label(today.plusDays(1)))
        assertEquals("Fri 18 Sep", label(today.plusDays(6)))
    }

    private fun movie(id: String, date: LocalDate) = ReleaseEvent(
        mediaRef = ExternalMediaRef(MediaSource.TMDB, "parent-$id"),
        subject = ReleaseSubjectIdentity(
            MediaSource.TMDB,
            ReleaseSubjectType.MEDIA,
            id,
            ReleaseEventType.MOVIE_RELEASE
        ),
        mediaType = MediaType.MOVIE,
        eventDate = date,
        title = id
    )
}
