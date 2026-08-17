package com.cydoniancitizen.bingee.domain.model

import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.core.model.PersonalViewingEntry
import com.cydoniancitizen.bingee.core.model.WatchedEpisodeActivity
import java.time.LocalDate
import java.time.ZoneId

const val TASTE_RADAR_GENRE_LIMIT = 6

data class MediaTypeDistribution(val movieCount: Int, val tvSeriesCount: Int) {
    val total: Int get() = movieCount + tvSeriesCount
    val moviePercentage: Double get() = if (total == 0) 0.0 else (movieCount.toDouble() / total) * 100.0
    val tvSeriesPercentage: Double get() = if (total == 0) 0.0 else (tvSeriesCount.toDouble() / total) * 100.0
}

data class MonthYearCount(val year: Int, val month: Int, val count: Int)

data class MonthlyViewingData(
    val year: Int,
    val month: Int,
    val movieMinutes: Long = 0L,
    val seriesMinutes: Long = 0L,
    val movieTimeIncomplete: Boolean = false,
    val seriesTimeIncomplete: Boolean = false
) {
    init {
        require(month in 1..12) { "Viewing month must be between 1 and 12" }
        require(movieMinutes >= 0) { "Movie viewing minutes must not be negative" }
        require(seriesMinutes >= 0) { "Series viewing minutes must not be negative" }
    }

    val totalMinutes: Long get() = movieMinutes + seriesMinutes
    val isIncomplete: Boolean get() = movieTimeIncomplete || seriesTimeIncomplete
}

data class MonthlyViewingStatistics(
    val currentYear: Int = 0,
    val currentMonth: Int = 0,
    val selectedYear: Int = 0,
    val availableYears: List<Int> = emptyList(),
    val months: List<MonthlyViewingData> = emptyList()
)

data class GenreStatistic(val source: MediaSource, val genreId: Long, val name: String, val titleCount: Int) {
    init {
        require(genreId > 0) { "Genre ID must be positive" }
        require(name.isNotBlank()) { "Genre name must not be blank" }
        require(titleCount > 0) { "Genre count must be positive" }
    }
}

enum class StatisticsMediaScope {
    ALL,
    MOVIES,
    SERIES
}

data class TasteStatistics(
    val scope: StatisticsMediaScope = StatisticsMediaScope.ALL,
    val rankedGenres: List<GenreStatistic> = emptyList()
) {
    val radarGenres: List<GenreStatistic> get() = rankedGenres.take(TASTE_RADAR_GENRE_LIMIT)
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
    val seriesGenres: List<GenreStatistic> = emptyList(),
    val monthlyViewing: MonthlyViewingStatistics = MonthlyViewingStatistics()
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
    val currentDate = LocalDate.now(zoneId)
    return calculateWatchedStatistics(entries, zoneId, currentDate, currentDate.year)
}

fun calculateWatchedStatistics(
    entries: List<PersonalViewingEntry>,
    zoneId: ZoneId,
    currentDate: LocalDate,
    selectedYear: Int
): WatchedStatistics {
    val uniqueEntries = entries.distinctBy { it.mediaRef }
    val completedTitles = uniqueEntries.filter(PersonalViewingEntry::isCompletedTitle)
    val viewedTitles = uniqueEntries.filter(PersonalViewingEntry::isViewingTasteEligible)

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
        seriesGenres = topGenreStatistics(seriesTitles),
        monthlyViewing = calculateMonthlyViewing(
            entries = uniqueEntries,
            zoneId = zoneId,
            currentDate = currentDate,
            selectedYear = selectedYear
        )
    )
}

private data class DatedViewingActivity(val mediaType: MediaType, val watchedDate: LocalDate, val runtimeMinutes: Int?)

private fun calculateMonthlyViewing(
    entries: List<PersonalViewingEntry>,
    zoneId: ZoneId,
    currentDate: LocalDate,
    selectedYear: Int
): MonthlyViewingStatistics {
    val datedActivities = entries.flatMap { entry ->
        when (entry.mediaType) {
            MediaType.MOVIE -> entry.movieWatchedAt?.let {
                listOf(
                    DatedViewingActivity(MediaType.MOVIE, it.atZone(zoneId).toLocalDate(), entry.movieRuntimeMinutes)
                )
            }.orEmpty()
            MediaType.SERIES -> entry.watchedRegularEpisodeActivities.map { activity ->
                activity.toDatedViewingActivity(zoneId)
            }
        }
    }.filter { !it.watchedDate.isAfter(currentDate) }

    val availableYears = datedActivities
        .map { it.watchedDate.year }
        .filter { it <= currentDate.year }
        .minOrNull()
        ?.let { firstYear -> (firstYear..currentDate.year).toList().asReversed() }
        ?: listOf(currentDate.year)
    val year = selectedYear.takeIf { it in availableYears } ?: currentDate.year
    val months = (1..12).map { month ->
        val activities = datedActivities.filter { it.watchedDate.year == year && it.watchedDate.monthValue == month }
        val movieActivities = activities.filter { it.mediaType == MediaType.MOVIE }
        val seriesActivities = activities.filter { it.mediaType == MediaType.SERIES }
        MonthlyViewingData(
            year = year,
            month = month,
            movieMinutes = movieActivities.sumOf { it.runtimeMinutes?.toLong() ?: 0L },
            seriesMinutes = seriesActivities.sumOf { it.runtimeMinutes?.toLong() ?: 0L },
            movieTimeIncomplete = movieActivities.any { it.runtimeMinutes == null },
            seriesTimeIncomplete = seriesActivities.any { it.runtimeMinutes == null }
        )
    }
    return MonthlyViewingStatistics(
        currentYear = currentDate.year,
        currentMonth = currentDate.monthValue,
        selectedYear = year,
        availableYears = availableYears,
        months = months
    )
}

private fun WatchedEpisodeActivity.toDatedViewingActivity(zoneId: ZoneId): DatedViewingActivity =
    DatedViewingActivity(MediaType.SERIES, watchedAt.atZone(zoneId).toLocalDate(), runtimeMinutes)

fun relativeViewingNormalization(months: List<MonthlyViewingData>): List<Float> {
    val maximum = months.maxOfOrNull { it.totalMinutes } ?: return emptyList()
    if (maximum == 0L) return months.map { 0f }
    return months.map { it.totalMinutes.toFloat() / maximum }
}

fun calculateTasteStatistics(
    entries: List<PersonalViewingEntry>,
    scope: StatisticsMediaScope = StatisticsMediaScope.ALL
): TasteStatistics = TasteStatistics(
    scope = scope,
    rankedGenres = rankedGenreStatistics(
        entries
            .asSequence()
            .distinctBy { it.mediaRef }
            .filter(PersonalViewingEntry::isViewingTasteEligible)
            .filter { entry ->
                when (scope) {
                    StatisticsMediaScope.ALL -> true
                    StatisticsMediaScope.MOVIES -> entry.mediaType == MediaType.MOVIE
                    StatisticsMediaScope.SERIES -> entry.mediaType == MediaType.SERIES
                }
            }
            .toList()
    )
)

fun relativeGenreNormalization(counts: List<Int>): List<Float> {
    val maximum = counts.maxOrNull()?.coerceAtLeast(0) ?: return emptyList()
    if (maximum == 0) return counts.map { 0f }
    return counts.map { it.coerceAtLeast(0).toFloat() / maximum }
}

private fun topGenreStatistics(entries: List<PersonalViewingEntry>): List<GenreStatistic> =
    rankedGenreStatistics(entries).take(3)

private fun rankedGenreStatistics(entries: List<PersonalViewingEntry>): List<GenreStatistic> {
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
}
