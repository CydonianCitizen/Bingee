package com.cydoniancitizen.bingee.domain.model

import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.core.model.PersonalViewingEntry
import java.time.ZoneId

data class MediaTypeDistribution(val movieCount: Int, val tvSeriesCount: Int) {
    val total: Int get() = movieCount + tvSeriesCount
    val moviePercentage: Double get() = if (total == 0) 0.0 else (movieCount.toDouble() / total) * 100.0
    val tvSeriesPercentage: Double get() = if (total == 0) 0.0 else (tvSeriesCount.toDouble() / total) * 100.0
}

data class MonthYearCount(val year: Int, val month: Int, val count: Int)

data class WatchedStatistics(
    val moviesWatchedCount: Int = 0,
    val tvSeriesCompletedCount: Int = 0,
    val episodesWatchedCount: Int = 0,
    val estimatedWatchTimeMinutes: Long = 0L,
    val isWatchTimeIncomplete: Boolean = false,
    val averagePersonalRating: Double? = null,
    val ratedTitlesPercentage: Double = 0.0,
    val mediaTypeDistribution: MediaTypeDistribution = MediaTypeDistribution(0, 0),
    val watchedByMonthYear: List<MonthYearCount> = emptyList(),
    val recentlyCompletedTitles: List<PersonalViewingEntry> = emptyList()
) {
    val isEmpty: Boolean
        get() =
            moviesWatchedCount == 0 &&
                tvSeriesCompletedCount == 0 &&
                episodesWatchedCount == 0
}

fun calculateWatchedStatistics(
    entries: List<PersonalViewingEntry>,
    zoneId: ZoneId = ZoneId.systemDefault()
): WatchedStatistics {
    val completedTitles = entries.filter(PersonalViewingEntry::isCompletedTitle)
    val viewedTitles = entries.filter(PersonalViewingEntry::isViewingTasteEligible)
    if (completedTitles.isEmpty() && viewedTitles.isEmpty()) return WatchedStatistics()

    val moviesWatchedCount = completedTitles.count { it.mediaType == MediaType.MOVIE }
    val tvSeriesCompletedCount = completedTitles.count { it.mediaType == MediaType.SERIES }
    val episodesWatchedCount = entries.sumOf(PersonalViewingEntry::watchedRegularEpisodes)

    // ponytail: fixed movie/episode durations; use persisted runtime data when available.
    val estimatedWatchTimeMinutes =
        moviesWatchedCount * 110L + episodesWatchedCount * 45L
    val ratedTitles = viewedTitles.filter { it.personalRating != null }
    val averagePersonalRating = ratedTitles.map { it.personalRating!!.value }.average().takeUnless(Double::isNaN)
    val ratedTitlesPercentage = if (viewedTitles.isEmpty()) {
        0.0
    } else {
        ratedTitles.size.toDouble() / viewedTitles.size * 100.0
    }

    val watchedByMonthYear = completedTitles
        .mapNotNull { it.displayWatchedDate(zoneId)?.let { date -> date.year to date.monthValue } }
        .groupingBy { it }
        .eachCount()
        .map { (yearMonth, count) -> MonthYearCount(yearMonth.first, yearMonth.second, count) }
        .sortedWith(compareByDescending<MonthYearCount> { it.year }.thenByDescending { it.month })

    val recentlyCompletedTitles = completedTitles.sortedWith(
        compareByDescending<PersonalViewingEntry> { it.displayWatchedDate(zoneId) }
            .thenByDescending { it.completionTimestamp }
            .thenBy { it.mediaRef.source.name }
            .thenBy { it.mediaRef.externalId }
    ).take(10)

    return WatchedStatistics(
        moviesWatchedCount = moviesWatchedCount,
        tvSeriesCompletedCount = tvSeriesCompletedCount,
        episodesWatchedCount = episodesWatchedCount,
        estimatedWatchTimeMinutes = estimatedWatchTimeMinutes,
        isWatchTimeIncomplete = moviesWatchedCount > 0 || episodesWatchedCount > 0,
        averagePersonalRating = averagePersonalRating,
        ratedTitlesPercentage = ratedTitlesPercentage,
        mediaTypeDistribution = MediaTypeDistribution(
            movieCount = viewedTitles.count { it.mediaType == MediaType.MOVIE },
            tvSeriesCount = viewedTitles.count { it.mediaType == MediaType.SERIES }
        ),
        watchedByMonthYear = watchedByMonthYear,
        recentlyCompletedTitles = recentlyCompletedTitles
    )
}
