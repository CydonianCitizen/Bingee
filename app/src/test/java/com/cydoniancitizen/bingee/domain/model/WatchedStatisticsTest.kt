package com.cydoniancitizen.bingee.domain.model

import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.Genre
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.core.model.PersonalRating
import com.cydoniancitizen.bingee.core.model.PersonalViewingEntry
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchedStatisticsTest {
    private val zone = ZoneId.of("Europe/Rome")

    @Test
    fun emptyHistoryProducesEmptyStatistics() {
        val stats = calculateWatchedStatistics(emptyList(), zone)

        assertEquals(0, stats.moviesWatchedCount)
        assertEquals(0, stats.tvSeriesCompletedCount)
        assertEquals(0, stats.episodesWatchedCount)
        assertNull(stats.averagePersonalRating)
        assertTrue(stats.isEmpty)
    }

    @Test
    fun completedCohortUsesOnlyGenuineCompletionEvidence() {
        val watchedMovie = movie("movie", watchedAt = instant("2026-05-10T10:00:00Z"))
        val completedSeries = series(
            "complete",
            watchedEpisodes = 10,
            completedAt = instant("2026-06-15T10:00:00Z")
        )
        val watching = series("watching", watchedEpisodes = 2)
        val abandoned = series("abandoned", watchedEpisodes = 3, isAbandoned = true)
        val specialsOnly = series("specials", watchedEpisodes = 0)

        val stats = calculateWatchedStatistics(
            listOf(watchedMovie, completedSeries, watching, abandoned, specialsOnly),
            zone
        )

        assertEquals(1, stats.moviesWatchedCount)
        assertEquals(1, stats.tvSeriesCompletedCount)
        assertEquals(listOf("complete", "movie"), stats.recentlyCompletedTitles.map { it.mediaRef.externalId })
    }

    @Test
    fun viewedTasteCohortExcludesRatingsAndSpecialsWithoutRegularProgress() {
        val entries = listOf(
            movie("watched-movie", watchedAt = Instant.EPOCH),
            movie("unwatched-movie", rating = 10),
            series("watch-later"),
            series("watching", watchedEpisodes = 1),
            series("watched", watchedEpisodes = 6, completedAt = Instant.EPOCH),
            series("abandoned", watchedEpisodes = 1, isAbandoned = true),
            series("specials-only")
        )

        assertEquals(
            setOf("watched-movie", "watching", "watched", "abandoned"),
            entries.filter(PersonalViewingEntry::isViewingTasteEligible).map { it.mediaRef.externalId }.toSet()
        )
    }

    @Test
    fun ratingAndMediaDistributionUseSameViewedTitleCohort() {
        val entries = listOf(
            movie("watched-rated", watchedAt = Instant.EPOCH, rating = 8),
            movie("unwatched-rated", rating = 10),
            series("started-rated", watchedEpisodes = 1, rating = 6),
            series("watch-later-rated", rating = 1)
        )

        val stats = calculateWatchedStatistics(entries, zone)

        assertEquals(7.0, stats.averagePersonalRating!!, 0.01)
        assertEquals(100.0, stats.ratedTitlesPercentage, 0.01)
        assertEquals(MediaTypeDistribution(movieCount = 1, tvSeriesCount = 1), stats.mediaTypeDistribution)
    }

    @Test
    fun episodeActivityIncludesAbandonedAndRemovedSeriesProgress() {
        val entries = listOf(
            series("watching", watchedEpisodes = 2),
            series("abandoned", watchedEpisodes = 3, isAbandoned = true),
            series("removed", watchedEpisodes = 4, inLibrary = false)
        )

        val stats = calculateWatchedStatistics(entries, zone)

        assertEquals(9, stats.episodesWatchedCount)
        assertEquals(3, stats.mediaTypeDistribution.tvSeriesCount)
    }

    @Test
    fun completionOrderingNeverUsesAddedAt() {
        val earlierCompletionAddedLater = movie(
            "earlier",
            watchedAt = instant("2026-01-01T12:00:00Z"),
            addedAt = instant("2026-12-01T12:00:00Z")
        )
        val laterCompletionAddedEarlier = movie(
            "later",
            watchedAt = instant("2026-02-01T12:00:00Z"),
            addedAt = instant("2025-01-01T12:00:00Z")
        )

        val recent = calculateWatchedStatistics(
            listOf(earlierCompletionAddedLater, laterCompletionAddedEarlier),
            zone
        ).recentlyCompletedTitles

        assertEquals(listOf("later", "earlier"), recent.map { it.mediaRef.externalId })
    }

    @Test
    fun explicitWatchedDateControlsDisplayWhileCompletionTimestampStaysPrecise() {
        val completion = instant("2026-08-01T23:30:00Z")
        val explicit = LocalDate.of(2020, 2, 3)
        val entry = movie("movie", watchedAt = completion, watchedDate = explicit)

        val stats = calculateWatchedStatistics(listOf(entry), zone)

        assertEquals(explicit, entry.displayWatchedDate(zone))
        assertEquals(completion, entry.completionTimestamp)
        assertEquals(listOf(MonthYearCount(2020, 2, 1)), stats.watchedByMonthYear)
    }

    @Test
    fun completionTimestampSuppliesLocalDisplayDateWhenUserDateMissing() {
        val entry = movie("movie", watchedAt = instant("2026-08-01T23:30:00Z"))

        val stats = calculateWatchedStatistics(listOf(entry), zone)

        assertEquals(LocalDate.of(2026, 8, 2), entry.displayWatchedDate(zone))
        assertEquals(listOf(MonthYearCount(2026, 8, 1)), stats.watchedByMonthYear)
        assertFalse(stats.isEmpty)
    }

    @Test
    fun currentSeriesCompletionUsesCurrentRegularProgress() {
        assertTrue(
            series(
                "caught-up",
                watchedEpisodes = 10,
                completedAt = Instant.EPOCH,
                seriesIsCurrentlyComplete = true
            ).isCompletedTitle
        )
        assertFalse(
            series(
                "new-episode",
                watchedEpisodes = 10,
                completedAt = Instant.EPOCH,
                seriesIsCurrentlyComplete = false
            ).isCompletedTitle
        )
        val stats = calculateWatchedStatistics(
            listOf(
                series(
                    "caught-up",
                    watchedEpisodes = 10,
                    completedAt = Instant.EPOCH,
                    seriesIsCurrentlyComplete = true
                ),
                series(
                    "new-episode",
                    watchedEpisodes = 10,
                    completedAt = Instant.EPOCH,
                    seriesIsCurrentlyComplete = false
                ),
                series("watching", watchedEpisodes = 9),
                series(
                    "abandoned-complete",
                    watchedEpisodes = 10,
                    completedAt = Instant.EPOCH,
                    isAbandoned = true,
                    seriesIsCurrentlyComplete = true
                )
            ),
            zone
        )

        assertEquals(1, stats.tvSeriesCompletedCount)
    }

    @Test
    fun viewingTimeUsesPersistedMinutesWithoutRoundingOrDefaults() {
        val stats = calculateWatchedStatistics(
            listOf(
                movie("removed", watchedAt = Instant.EPOCH, inLibrary = false, runtimeMinutes = 137),
                series(
                    "series",
                    watchedEpisodes = 2,
                    watchedRuntimeMinutes = 73
                )
            ),
            zone
        )

        assertEquals(137L, stats.movieWatchTimeMinutes)
        assertEquals(73L, stats.seriesWatchTimeMinutes)
        assertEquals(210L, stats.watchTimeMinutes)
        assertFalse(stats.isWatchTimeIncomplete)
    }

    @Test
    fun missingRuntimeNeverBecomesFabricatedTime() {
        val stats = calculateWatchedStatistics(
            listOf(
                movie("known", watchedAt = Instant.EPOCH, runtimeMinutes = 48),
                movie("missing", watchedAt = Instant.EPOCH),
                series("partial", watchedEpisodes = 2, watchedRuntimeMinutes = 50, missingRuntimeEpisodes = 1)
            ),
            zone
        )

        assertEquals(48L, stats.movieWatchTimeMinutes)
        assertEquals(50L, stats.seriesWatchTimeMinutes)
        assertTrue(stats.isWatchTimeIncomplete)
    }

    @Test
    fun genreAggregationUsesCanonicalIdentityAndCountsEachTitleOnce() {
        val stats = calculateWatchedStatistics(
            listOf(
                movie(
                    "one",
                    watchedAt = Instant.EPOCH,
                    genres = listOf(
                        Genre("Drama", MediaSource.TMDB, 18),
                        Genre("Dramma", MediaSource.TMDB, 18),
                        Genre("Thriller", MediaSource.TMDB, 53)
                    )
                ),
                movie(
                    "two",
                    watchedAt = Instant.EPOCH,
                    genres = listOf(
                        Genre("Dramma", MediaSource.TMDB, 18),
                        Genre("Comedy", MediaSource.TMDB, 35)
                    )
                ),
                movie(
                    "three",
                    watchedAt = Instant.EPOCH,
                    genres = listOf(
                        Genre("Drama", MediaSource.TMDB, 18),
                        Genre("Legacy", null, null),
                        Genre("Science Fiction", MediaSource.TMDB, 878)
                    )
                )
            ),
            zone
        )

        assertEquals(listOf(18L, 35L, 53L), stats.movieGenres.map { it.genreId })
        assertEquals(listOf(3, 1, 1), stats.movieGenres.map { it.titleCount })
        assertEquals("Drama", stats.movieGenres.first().name)
    }

    @Test
    fun duplicateCanonicalViewingRowsCountOnce() {
        val stats = calculateWatchedStatistics(
            listOf(
                movie("same", watchedAt = Instant.EPOCH, runtimeMinutes = 100),
                movie("same", watchedAt = Instant.EPOCH, runtimeMinutes = 100)
            ),
            zone
        )

        assertEquals(1, stats.moviesWatchedCount)
        assertEquals(100L, stats.movieWatchTimeMinutes)
    }

    private fun movie(
        id: String,
        watchedAt: Instant? = null,
        watchedDate: LocalDate? = null,
        rating: Int? = null,
        addedAt: Instant = Instant.EPOCH,
        inLibrary: Boolean = true,
        runtimeMinutes: Int? = null,
        genres: List<Genre> = emptyList()
    ) = entry(
        id = id,
        mediaType = MediaType.MOVIE,
        addedAt = addedAt,
        inLibrary = inLibrary,
        rating = rating,
        movieWatchedAt = watchedAt,
        watchedDate = watchedDate,
        movieRuntimeMinutes = runtimeMinutes,
        genres = genres
    )

    private fun series(
        id: String,
        watchedEpisodes: Int = 0,
        completedAt: Instant? = null,
        rating: Int? = null,
        isAbandoned: Boolean = false,
        inLibrary: Boolean = true,
        watchedRuntimeMinutes: Long = 0L,
        missingRuntimeEpisodes: Int = 0,
        seriesIsCurrentlyComplete: Boolean? = null,
        genres: List<Genre> = emptyList()
    ) = entry(
        id = id,
        mediaType = MediaType.SERIES,
        inLibrary = inLibrary,
        rating = rating,
        watchedRegularEpisodes = watchedEpisodes,
        seriesCompletedAt = completedAt,
        isAbandoned = isAbandoned,
        watchedRegularRuntimeMinutes = watchedRuntimeMinutes,
        watchedRegularEpisodesWithoutRuntime = missingRuntimeEpisodes,
        seriesIsCurrentlyComplete = seriesIsCurrentlyComplete,
        genres = genres
    )

    private fun entry(
        id: String,
        mediaType: MediaType,
        addedAt: Instant = Instant.EPOCH,
        inLibrary: Boolean = true,
        rating: Int? = null,
        movieWatchedAt: Instant? = null,
        watchedRegularEpisodes: Int = 0,
        seriesCompletedAt: Instant? = null,
        watchedDate: LocalDate? = null,
        isAbandoned: Boolean = false,
        movieRuntimeMinutes: Int? = null,
        watchedRegularRuntimeMinutes: Long = 0L,
        watchedRegularEpisodesWithoutRuntime: Int = 0,
        seriesIsCurrentlyComplete: Boolean? = null,
        genres: List<Genre> = emptyList()
    ) = PersonalViewingEntry(
        mediaRef = ExternalMediaRef(MediaSource.TMDB, id),
        mediaType = mediaType,
        title = "Title $id",
        addedAt = addedAt,
        inLibrary = inLibrary,
        isFavorite = false,
        isAbandoned = isAbandoned,
        personalRating = rating?.let(::PersonalRating),
        movieWatchedAt = movieWatchedAt,
        watchedRegularEpisodes = watchedRegularEpisodes,
        seriesCompletedAt = seriesCompletedAt,
        watchedDate = watchedDate,
        movieRuntimeMinutes = movieRuntimeMinutes,
        watchedRegularRuntimeMinutes = watchedRegularRuntimeMinutes,
        watchedRegularEpisodesWithoutRuntime = watchedRegularEpisodesWithoutRuntime,
        seriesIsCurrentlyComplete = seriesIsCurrentlyComplete,
        genres = genres
    )

    private fun instant(value: String): Instant = Instant.parse(value)
}
