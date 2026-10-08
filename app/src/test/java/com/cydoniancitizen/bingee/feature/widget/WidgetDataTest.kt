package com.cydoniancitizen.bingee.feature.widget

import com.cydoniancitizen.bingee.core.model.ContinueWatchingItem
import com.cydoniancitizen.bingee.core.model.EpisodePosition
import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.core.model.ReleaseEvent
import com.cydoniancitizen.bingee.core.model.ReleaseEventType
import com.cydoniancitizen.bingee.core.model.ReleaseSubjectIdentity
import com.cydoniancitizen.bingee.core.model.ReleaseSubjectType
import com.cydoniancitizen.bingee.core.model.SeriesProgress
import com.cydoniancitizen.bingee.core.result.AppResult
import com.cydoniancitizen.bingee.data.settings.AppTheme
import java.time.LocalDate
import java.util.Locale
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
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

    @Test
    fun activeSnapshotUpdatesProgressReleasesAndThemeWithoutRestartingCollector() = runTest {
        val series = ContinueWatchingItem(
            mediaRef = ExternalMediaRef(MediaSource.TMDB, "1"),
            mediaType = MediaType.SERIES,
            title = "Series",
            posterUrl = null,
            progress = SeriesProgress(1, 2, 0, 1, false),
            nextEpisode = EpisodePosition(1, 2),
            updatedAt = null
        )
        val watching = MutableStateFlow<AppResult<List<ContinueWatchingItem>>>(AppResult.Success(listOf(series)))
        val releases = MutableStateFlow<AppResult<List<ReleaseEvent>>>(AppResult.Success(listOf(movie("first", today))))
        val themes = MutableStateFlow(AppTheme.LIGHT)
        val dates = MutableStateFlow(today)
        val snapshots = mutableListOf<WidgetSnapshot>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            observeWidgetSnapshot(watching, { releases }, dates, themes).collect { snapshots += it }
        }
        runCurrent()
        assertEquals(EpisodePosition(1, 2), snapshots.last().watching?.nextEpisode)

        watching.value = AppResult.Success(emptyList())
        runCurrent()
        assertEquals(null, snapshots.last().watching)
        releases.value = AppResult.Success(listOf(movie("next", today.plusDays(2))))
        runCurrent()
        assertEquals(listOf("next"), snapshots.last().upcoming.map { it.title })
        themes.value = AppTheme.DARK
        runCurrent()
        assertEquals(AppTheme.DARK, snapshots.last().theme)
        assertEquals(4, snapshots.size)

        job.cancel()
        runCurrent()
        assertEquals(0, watching.subscriptionCount.value)
        assertEquals(0, releases.subscriptionCount.value)
        assertEquals(0, themes.subscriptionCount.value)
        assertEquals(0, dates.subscriptionCount.value)
    }

    @Test
    fun dateRolloverReplacesCalendarObservationAndCancelsPreviousDay() = runTest {
        val dates = MutableStateFlow(today)
        val cancelled = mutableListOf<LocalDate>()
        val snapshots = mutableListOf<WidgetSnapshot>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            observeWidgetSnapshot(
                watching = MutableStateFlow(AppResult.Success(emptyList())),
                events = { date ->
                    flow {
                        try {
                            emit(AppResult.Success(listOf(movie("on-$date", date))))
                            awaitCancellation()
                        } finally {
                            cancelled += date
                        }
                    }
                },
                dates = dates,
                themes = MutableStateFlow(AppTheme.SYSTEM_DEFAULT)
            ).collect { snapshots += it }
        }
        runCurrent()
        dates.value = today.plusDays(1)
        runCurrent()
        assertEquals(listOf(today), cancelled)
        assertEquals(today.plusDays(1), snapshots.last().today)
        assertEquals(listOf("on-${today.plusDays(1)}"), snapshots.last().upcoming.map { it.title })
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
