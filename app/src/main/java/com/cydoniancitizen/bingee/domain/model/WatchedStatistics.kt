package com.cydoniancitizen.bingee.domain.model

import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.core.model.PersonalViewingEntry
import java.time.ZoneId

data class MediaTypeDistribution(val movieCount: Int, val tvSeriesCount: Int) {
    val total: Int get() = movieCount + tvSeriesCount
    val moviePercentage: Double get() = if (total == 0) 0.0 else (movieCount.toDouble() / total) * 100.0
    val tvSeriesPercentage: Double get() = if (total == 0) 0.0 else (tvSeriesCount.toDouble() / total) * 100.0
}

data class MonthYearCount(val year: Int, val month: Int, val count: Int)

data class GenreStatistic(val source: MediaSource, val genreId: Long, val name: String, val titleCount: Int) {
    init {
        require(genreId > 0) { "Genre ID must be positive" }
        require(name.isNotBlank()) { "Genre name must not be blank" }
        require(titleCount > 0) { "Genre count must be positive" }
    }
}

data class WatchedStatistics(
    val moviesWatchedCount: Int = 0,
    val tvSeriesCompletedCount: Int = 0,
    val episodesWatchedCount: Int = 0,
    val movieWatchTimeMinutes: Long = 0L,
    val seriesWatchTimeMinutes: Long = 0L,
    val movieWatchTimeIncomplete: Boolean = false,
    val seriesWatchTimeIncomplete: Boolean = false,
    val averagePersonalRating: Double? = null,
    val ratedTitlesPercentage: Double = 0.0,
    val mediaTypeDistribution: MediaTypeDistribution = MediaTypeDistribution(0, 0),
    val watchedByMonthYear: List<MonthYearCount> = emptyList(),
    val recentlyCompletedTitles: List<PersonalViewingEntry> = emptyList(),
    val movieGenres: List<GenreStatistic> = emptyList(),
    val seriesGenres: List<GenreStatistic> = emptyList()
) {
    val watchTimeMinutes: Long get() = movieWatchTimeMinutes + seriesWatchTimeMinutes
    val isWatchTimeIncomplete: Boolean get() = movieWatchTimeIncomplete || seriesWatchTimeIncomplete

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
    val uniqueEntries = entries.distinctBy { it.mediaRef }
    val completedTitles = uniqueEntries.filter(PersonalViewingEntry::isCompletedTitle)
    val viewedTitles = uniqueEntries.filter(PersonalViewingEntry::isViewingTasteEligible)
    if (completedTitles.isEmpty() && viewedTitles.isEmpty()) return WatchedStatistics()

    val moviesWatchedCount = completedTitles.count { it.mediaType == MediaType.MOVIE }
    val tvSeriesCompletedCount = completedTitles.count { it.mediaType == MediaType.SERIES }
    val episodesWatchedCount = viewedTitles.sumOf(PersonalViewingEntry::watchedRegularEpisodes)
    val movieTitles = viewedTitles.filter { it.mediaType == MediaType.MOVIE }
    val seriesTitles = viewedTitles.filter { it.mediaType == MediaType.SERIES }
    val movieWatchTimeMinutes = movieTitles.sumOf { it.movieRuntimeMinutes?.toLong() ?: 0L }
    val seriesWatchTimeMinutes = seriesTitles.sumOf { it.watchedRegularRuntimeMinutes }
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
        movieWatchTimeMinutes = movieWatchTimeMinutes,
        seriesWatchTimeMinutes = seriesWatchTimeMinutes,
        movieWatchTimeIncomplete = movieTitles.any { it.movieRuntimeMinutes == null },
        seriesWatchTimeIncomplete = seriesTitles.any { it.watchedRegularEpisodesWithoutRuntime > 0 },
        averagePersonalRating = averagePersonalRating,
        ratedTitlesPercentage = ratedTitlesPercentage,
        mediaTypeDistribution = MediaTypeDistribution(
            movieCount = viewedTitles.count { it.mediaType == MediaType.MOVIE },
            tvSeriesCount = viewedTitles.count { it.mediaType == MediaType.SERIES }
        ),
        watchedByMonthYear = watchedByMonthYear,
        recentlyCompletedTitles = recentlyCompletedTitles,
        movieGenres = topGenreStatistics(movieTitles),
        seriesGenres = topGenreStatistics(seriesTitles)
    )
}

private fun topGenreStatistics(entries: List<PersonalViewingEntry>): List<GenreStatistic> {
    val counts = linkedMapOf<Pair<MediaSource, Long>, GenreStatistic>()
    entries.forEach { entry ->
        entry.genres
            .filter { it.source != null && it.genreId != null }
            .distinctBy { it.source!! to it.genreId!! }
            .forEach { genre ->
                val source = requireNotNull(genre.source)
                val genreId = requireNotNull(genre.genreId)
                val key = source to genreId
                counts[key] = counts[key]?.copy(titleCount = counts.getValue(key).titleCount + 1)
                    ?: GenreStatistic(source, genreId, genre.name, 1)
            }
    }
    return counts.values
        .sortedWith(
            compareByDescending<GenreStatistic> { it.titleCount }
                .thenBy { it.source.name }
                .thenBy { it.genreId }
        )
        .take(3)
}
