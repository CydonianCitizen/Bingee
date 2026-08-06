package com.cydoniancitizen.bingee.domain.model

import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.LibraryEntry
import com.cydoniancitizen.bingee.core.model.LibraryProgress
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.core.model.MovieWatchState
import com.cydoniancitizen.bingee.core.model.PersonalRating
import com.cydoniancitizen.bingee.core.model.SeriesProgress
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchedStatisticsTest {

    @Test
    fun calculateWatchedStatisticsHandlesEmptyList() {
        val stats = calculateWatchedStatistics(emptyList())
        assertEquals(0, stats.moviesWatchedCount)
        assertEquals(0, stats.tvSeriesCompletedCount)
        assertEquals(0, stats.episodesWatchedCount)
        assertEquals(0L, stats.estimatedWatchTimeMinutes)
        assertNull(stats.averagePersonalRating)
        assertEquals(0.0, stats.ratedTitlesPercentage, 0.01)
        assertTrue(stats.watchedByMonthYear.isEmpty())
        assertTrue(stats.watchedByYear.isEmpty())
        assertTrue(stats.isEmpty)
    }

    @Test
    fun oneWatchedMovieMakesStatisticsNonEmpty() {
        val movie = LibraryEntry(
            mediaRef = ExternalMediaRef(MediaSource.TMDB, "watched-movie"),
            mediaType = MediaType.MOVIE,
            title = "Watched Movie",
            addedAt = Instant.EPOCH,
            progress = LibraryProgress.Movie(MovieWatchState.Watched(Instant.EPOCH))
        )

        assertFalse(calculateWatchedStatistics(listOf(movie)).isEmpty)
    }

    @Test
    fun oneCompletedTvSeriesMakesStatisticsNonEmpty() {
        val series = LibraryEntry(
            mediaRef = ExternalMediaRef(MediaSource.TMDB, "completed-series"),
            mediaType = MediaType.SERIES,
            title = "Completed Series",
            addedAt = Instant.EPOCH,
            progress = LibraryProgress.Series(SeriesProgress(1, 1, 1, 1, isComplete = true))
        )

        assertFalse(calculateWatchedStatistics(listOf(series)).isEmpty)
    }

    @Test
    fun watchedEpisodesWithoutCompletedSeriesMakeStatisticsNonEmpty() {
        val series = LibraryEntry(
            mediaRef = ExternalMediaRef(MediaSource.TMDB, "started-series"),
            mediaType = MediaType.SERIES,
            title = "Started Series",
            addedAt = Instant.EPOCH,
            progress = LibraryProgress.Series(SeriesProgress(1, 2, 0, 1, isComplete = false))
        )

        val stats = calculateWatchedStatistics(listOf(series))

        assertEquals(0, stats.tvSeriesCompletedCount)
        assertEquals(1, stats.episodesWatchedCount)
        assertFalse(stats.isEmpty)
    }

    @Test
    fun ratingsAndMetadataAloneDoNotMakeStatisticsNonEmpty() {
        val stats = WatchedStatistics(
            estimatedWatchTimeMinutes = 90,
            isWatchTimeIncomplete = true,
            averagePersonalRating = 8.0,
            ratedTitlesPercentage = 100.0,
            mostWatchedGenres = listOf(GenreCount("Drama", 1)),
            mediaTypeDistribution = MediaTypeDistribution(0, 0),
            watchedByMonthYear = listOf(MonthYearCount(2026, 1, 1)),
            watchedByYear = listOf(YearCount(2026, 1))
        )

        assertTrue(stats.isEmpty)
    }

    @Test
    fun calculateWatchedStatisticsComputesCountsAndTemporalHistoryWithoutFabrication() {
        val date1 = LocalDate.of(2026, 5, 10)
        val date2 = LocalDate.of(2026, 6, 15)

        val watchedMovieWithDate = LibraryEntry(
            mediaRef = ExternalMediaRef(MediaSource.TMDB, "1"),
            mediaType = MediaType.MOVIE,
            title = "Movie 1",
            addedAt = Instant.EPOCH,
            progress = LibraryProgress.Movie(MovieWatchState.Watched(Instant.EPOCH, date1)),
            personalRating = PersonalRating(8),
            watchedDate = date1
        )

        val watchedMovieWithoutDate = LibraryEntry(
            mediaRef = ExternalMediaRef(MediaSource.TMDB, "2"),
            mediaType = MediaType.MOVIE,
            title = "Movie 2",
            addedAt = Instant.EPOCH,
            progress = LibraryProgress.Movie(MovieWatchState.Watched(Instant.EPOCH)),
            personalRating = null,
            watchedDate = null
        )

        val completedTvSeries = LibraryEntry(
            mediaRef = ExternalMediaRef(MediaSource.TMDB, "3"),
            mediaType = MediaType.SERIES,
            title = "Series 1",
            addedAt = Instant.EPOCH,
            progress = LibraryProgress.Series(SeriesProgress(10, 10, 1, 1, isComplete = true)),
            personalRating = PersonalRating(10),
            watchedDate = date2
        )

        val unwatchedMovie = LibraryEntry(
            mediaRef = ExternalMediaRef(MediaSource.TMDB, "4"),
            mediaType = MediaType.MOVIE,
            title = "Unwatched",
            addedAt = Instant.EPOCH,
            progress = LibraryProgress.Movie(MovieWatchState.Unwatched),
            watchedDate = null
        )

        val entries = listOf(watchedMovieWithDate, watchedMovieWithoutDate, completedTvSeries, unwatchedMovie)
        val stats = calculateWatchedStatistics(entries, animeAvailable = false)

        assertEquals(2, stats.moviesWatchedCount)
        assertEquals(1, stats.tvSeriesCompletedCount)
        assertEquals(10, stats.episodesWatchedCount)
        assertEquals(2, stats.mediaTypeDistribution.movieCount)
        assertEquals(1, stats.mediaTypeDistribution.tvSeriesCount)

        // Rating average: (8 + 10) / 2 = 9.0
        assertEquals(9.0, stats.averagePersonalRating!!, 0.01)
        // 2 of 3 watched entries are rated -> 66.67%
        assertEquals(66.67, stats.ratedTitlesPercentage, 0.1)

        // Temporal history must ONLY include entries with an explicit watchedDate (2 titles)
        assertEquals(2, stats.watchedByMonthYear.size)
        assertEquals(1, stats.watchedByYear.size)
        assertEquals(2026, stats.watchedByYear.first().year)
        assertEquals(2, stats.watchedByYear.first().count)
    }

    @Test
    fun calculateWatchedStatisticsExcludesHiddenAnimeWhenDisabled() {
        val animeMovie = LibraryEntry(
            mediaRef = ExternalMediaRef(MediaSource.JIKAN, "1"),
            mediaType = MediaType.ANIME,
            title = "Anime Movie",
            addedAt = Instant.EPOCH,
            progress = LibraryProgress.Movie(MovieWatchState.Watched(Instant.EPOCH)),
            watchedDate = LocalDate.of(2026, 1, 1)
        )

        val statsDisabled = calculateWatchedStatistics(listOf(animeMovie), animeAvailable = false)
        assertEquals(0, statsDisabled.moviesWatchedCount)

        val statsEnabled = calculateWatchedStatistics(listOf(animeMovie), animeAvailable = true)
        assertEquals(1, statsEnabled.moviesWatchedCount)
    }
}
