package com.cydoniancitizen.bingee.core.model

import java.time.LocalDate
import java.util.Locale

data class ReleaseCalendarWindow(val lookbackDays: Long = DEFAULT_LOOKBACK_DAYS) {
    init {
        require(lookbackDays >= 0) { "Calendar lookback must not be negative" }
    }

    fun startDate(today: LocalDate): LocalDate = today.minusDays(lookbackDays)

    companion object {
        const val DEFAULT_LOOKBACK_DAYS = 7L
    }
}

enum class ReleaseDateCategory { RECENT, TODAY, UPCOMING }

data class ReleaseDateGroup(val date: LocalDate, val category: ReleaseDateCategory, val events: List<ReleaseEvent>) {
    init {
        require(events.isNotEmpty()) { "Release date group must not be empty" }
        require(events.all { it.eventDate == date }) { "Release date group contains another date" }
    }
}

fun groupReleaseEvents(events: List<ReleaseEvent>, today: LocalDate): List<ReleaseDateGroup> = events
    .sortedWith(releaseEventComparator)
    .groupBy(ReleaseEvent::eventDate)
    .toSortedMap()
    .map { (date, rows) ->
        ReleaseDateGroup(
            date = date,
            category = when {
                date.isBefore(today) -> ReleaseDateCategory.RECENT
                date == today -> ReleaseDateCategory.TODAY
                else -> ReleaseDateCategory.UPCOMING
            },
            events = rows
        )
    }

val releaseEventComparator: Comparator<ReleaseEvent> = compareBy<ReleaseEvent>(
    ReleaseEvent::eventDate,
    { it.subject.eventType.sortOrder },
    { it.title.trim().lowercase(Locale.ROOT) },
    { it.subject.source.name },
    { it.subject.subjectType.name },
    { it.subject.externalId },
    { it.subject.eventType.name }
)

private val ReleaseEventType.sortOrder: Int
    get() = when (this) {
        ReleaseEventType.EPISODE_AIRING -> 0
        ReleaseEventType.SEASON_PREMIERE -> 1
        ReleaseEventType.MOVIE_RELEASE -> 2
    }
