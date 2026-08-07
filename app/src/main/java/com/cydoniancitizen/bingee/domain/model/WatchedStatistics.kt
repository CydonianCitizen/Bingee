package com.cydoniancitizen.bingee.domain.model

import com.cydoniancitizen.bingee.core.model.LibraryEntry
import com.cydoniancitizen.bingee.core.model.LibraryProgress
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.data.settings.ProfileCategory
import com.cydoniancitizen.bingee.feature.profile.belongsToCategory
import com.cydoniancitizen.bingee.feature.profile.isWatched
import java.time.ZoneId

data class GenreCount(val genre: String, val count: Int)

data class MediaTypeDistribution(val movieCount: Int, val tvSeriesCount: Int) {
    val total: Int get() = movieCount + tvSeriesCount
    val moviePercentage: Double get() = if (total == 0) 0.0 else (movieCount.toDouble() / total) * 100.0
    val tvSeriesPercentage: Double get() = if (total == 0) 0.0 else (tvSeriesCount.toDouble() / total) * 100.0
}

data class MonthYearCount(val year: Int, val month: Int, val count: Int)

data class YearCount(val year: Int, val count: Int)

data class WatchedStatistics(
    val moviesWatchedCount: Int = 0,
    val tvSeriesCompletedCount: Int = 0,
    val episodesWatchedCount: Int = 0,
    val estimatedWatchTimeMinutes: Long = 0L,
    val isWatchTimeIncomplete: Boolean = false,
    val averagePersonalRating: Double? = null,
    val ratedTitlesPercentage: Double = 0.0,
    val mostWatchedGenres: List<GenreCount> = emptyList(),
    val mediaTypeDistribution: MediaTypeDistribution = MediaTypeDistribution(0, 0),
    val watchedByMonthYear: List<MonthYearCount> = emptyList(),
    val watchedByYear: List<YearCount> = emptyList(),
    val recentlyCompletedTitles: List<LibraryEntry> = emptyList()
) {
    val isEmpty: Boolean
        get() =
            moviesWatchedCount == 0 &&
                tvSeriesCompletedCount == 0 &&
                episodesWatchedCount == 0
}

fun calculateWatchedStatistics(entries: List<LibraryEntry>): WatchedStatistics {
    val watchedEntries = entries.filter { entry ->
        entry.isWatched()
    }

    if (watchedEntries.isEmpty()) {
        return WatchedStatistics()
    }

    val movies = watchedEntries.filter { it.belongsToCategory(ProfileCategory.MOVIES) }
    val tvSeries = watchedEntries.filter { it.belongsToCategory(ProfileCategory.TV_SERIES) }

    val moviesWatchedCount = movies.size
    val tvSeriesCompletedCount = tvSeries.count { entry ->
        when (val p = entry.progress) {
            is LibraryProgress.Series -> p.progress.isComplete
            else -> false
        }
    }

    var totalEpisodesWatched = 0
    var totalWatchTimeMinutes = 0L
    var isIncompleteWatchTime = false

    watchedEntries.forEach { entry ->
        when (val p = entry.progress) {
            is LibraryProgress.Movie -> {
                totalWatchTimeMinutes += 110L
                isIncompleteWatchTime = true
            }
            is LibraryProgress.Series -> {
                totalEpisodesWatched += p.progress.watchedEpisodes
                totalWatchTimeMinutes += p.progress.watchedEpisodes * 45L
                isIncompleteWatchTime = true
            }
            LibraryProgress.Unavailable -> {
                if (entry.mediaType == MediaType.MOVIE) {
                    totalWatchTimeMinutes += 110L
                }
                isIncompleteWatchTime = true
            }
        }
    }

    val ratedEntries = watchedEntries.filter { it.personalRating != null }
    val averageRating = if (ratedEntries.isNotEmpty()) {
        ratedEntries.map { it.personalRating!!.value }.average()
    } else {
        null
    }
    val ratedPercentage = (ratedEntries.size.toDouble() / watchedEntries.size) * 100.0

    // Only titles with explicit watchedDate contribute to temporal history (not fabricated from addedAt)
    val monthYearCounts = watchedEntries.mapNotNull { entry ->
        entry.watchedDate?.let { it.year to it.monthValue }
    }.groupingBy { it }.eachCount()
        .map { (ym, count) -> MonthYearCount(ym.first, ym.second, count) }
        .sortedWith(compareByDescending<MonthYearCount> { it.year }.thenByDescending { it.month })

    val yearCounts = watchedEntries.mapNotNull { entry ->
        entry.watchedDate?.year
    }.groupingBy { it }.eachCount()
        .map { (year, count) -> YearCount(year, count) }
        .sortedByDescending { it.year }

    val recentlyCompleted = watchedEntries.sortedWith(
        compareByDescending<LibraryEntry> {
            it.watchedDate ?: it.addedAt.atZone(ZoneId.systemDefault()).toLocalDate()
        }.thenByDescending { it.addedAt }
    ).take(10)

    return WatchedStatistics(
        moviesWatchedCount = moviesWatchedCount,
        tvSeriesCompletedCount = tvSeriesCompletedCount,
        episodesWatchedCount = totalEpisodesWatched,
        estimatedWatchTimeMinutes = totalWatchTimeMinutes,
        isWatchTimeIncomplete = isIncompleteWatchTime,
        averagePersonalRating = averageRating,
        ratedTitlesPercentage = ratedPercentage,
        mostWatchedGenres = emptyList(),
        mediaTypeDistribution = MediaTypeDistribution(moviesWatchedCount, tvSeries.size),
        watchedByMonthYear = monthYearCounts,
        watchedByYear = yearCounts,
        recentlyCompletedTitles = recentlyCompleted
    )
}
