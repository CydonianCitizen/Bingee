package com.cydoniancitizen.bingee.domain.model

import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.Genre
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.core.model.PersonalRating
import com.cydoniancitizen.bingee.core.model.PersonalViewingEntry
import com.cydoniancitizen.bingee.core.model.WatchedEpisodeActivity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
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
        assertEquals(0, stats.personalRatingStatistics.ratedTitleCount)
        assertEquals(List(10) { 0 }, stats.personalRatingStatistics.histogram.map { it.titleCount })
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

        assertEquals(7.0, stats.personalRatingStatistics.averageRating!!, 0.01)
        assertEquals(2, stats.personalRatingStatistics.ratedTitleCount)
    }

    @Test
    fun personalRatingStatisticsKeepTenBucketsPreciseAverageAndRecentOrdering() {
        val stats = calculateWatchedStatistics(
            listOf(
                movie(
                    "new",
                    watchedAt = instant("2026-01-01T00:00:00Z"),
                    rating = 8,
                    ratingUpdatedAt = instant("2026-08-03T00:00:00Z")
                ),
                movie(
                    "old",
                    watchedAt = instant("2026-08-02T00:00:00Z"),
                    rating = 8,
                    ratingUpdatedAt = instant("2026-08-02T00:00:00Z")
                ),
                series(
                    "series",
                    watchedEpisodes = 1,
                    rating = 9,
                    ratingUpdatedAt = instant("2026-08-01T00:00:00Z")
                ),
                movie("legacy-b", watchedAt = Instant.EPOCH, rating = 9),
                movie("legacy-a", watchedAt = instant("2026-08-04T00:00:00Z"), rating = 8)
            ),
            zone
        )

        val ratingStats = stats.personalRatingStatistics
        assertEquals(8.4, ratingStats.averageRating!!, 0.001)
        assertEquals(5, ratingStats.ratedTitleCount)
        assertEquals((1..10).toList(), ratingStats.histogram.map { it.rating })
        assertEquals(listOf(0, 0, 0, 0, 0, 0, 0, 3, 2, 0), ratingStats.histogram.map { it.titleCount })
        assertEquals(
            listOf("new", "old", "series", "legacy-a", "legacy-b"),
            ratingStats.ratedTitles.map {
                it.mediaRef.externalId
            }
        )
    }

    @Test
    fun personalRatingDisplayRoundsOnlyAtOneLocalizedDecimal() {
        val stats = calculateWatchedStatistics(
            listOf(
                movie("a", watchedAt = Instant.EPOCH, rating = 8),
                movie("b", watchedAt = Instant.EPOCH, rating = 8),
                movie("c", watchedAt = Instant.EPOCH, rating = 9),
                movie("d", watchedAt = Instant.EPOCH, rating = 6)
            ),
            zone
        )

        assertEquals(7.75, stats.personalRatingStatistics.averageRating!!, 0.001)
        assertEquals("7.8", formatPersonalRatingAverage(7.75, Locale.US))
        assertEquals("7,8", formatPersonalRatingAverage(7.75, Locale.ITALY))
    }

    @Test
    fun ratingNormalizationHandlesAllZeroAndExactRatios() {
        val buckets = (1..10).map { rating ->
            RatingHistogramBucket(
                rating,
                when (rating) {
                    8 -> 20
                    9 -> 10
                    7 -> 5
                    else -> 0
                }
            )
        }

        assertEquals(
            listOf(0f, 0f, 0f, 0f, 0f, 0f, 0.25f, 1f, 0.5f, 0f),
            relativeRatingNormalization(buckets)
        )
        assertEquals(
            List(10) { 0f },
            relativeRatingNormalization((1..10).map { RatingHistogramBucket(it, 0) })
        )
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
    }

    @Test
    fun explicitWatchedDateControlsDisplayWhileCompletionTimestampStaysPrecise() {
        val completion = instant("2026-08-01T23:30:00Z")
        val explicit = LocalDate.of(2020, 2, 3)
        val entry = movie("movie", watchedAt = completion, watchedDate = explicit)

        assertEquals(explicit, entry.displayWatchedDate(zone))
        assertEquals(completion, entry.completionTimestamp)
    }

    @Test
    fun completionTimestampSuppliesLocalDisplayDateWhenUserDateMissing() {
        val entry = movie("movie", watchedAt = instant("2026-08-01T23:30:00Z"))

        val stats = calculateWatchedStatistics(listOf(entry), zone)

        assertEquals(LocalDate.of(2026, 8, 2), entry.displayWatchedDate(zone))
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

    @Test
    fun monthlyViewingUsesGenuineMovieAndEpisodeDates() {
        val stats = calculateWatchedStatistics(
            listOf(
                movie("movie", watchedAt = instant("2026-03-10T10:00:00Z"), runtimeMinutes = 120),
                series(
                    "series",
                    watchedEpisodes = 2,
                    watchedRuntimeMinutes = 90,
                    activities = listOf(
                        WatchedEpisodeActivity(instant("2026-01-04T10:00:00Z"), 45),
                        WatchedEpisodeActivity(instant("2026-02-04T10:00:00Z"), 45)
                    )
                )
            ),
            zone,
            LocalDate.of(2026, 8, 17),
            2026
        )

        assertEquals(12, stats.monthlyViewing.months.size)
        assertEquals(120L, stats.monthlyViewing.months[2].movieMinutes)
        assertEquals(45L, stats.monthlyViewing.months[0].seriesMinutes)
        assertEquals(45L, stats.monthlyViewing.months[1].seriesMinutes)
        assertEquals(0L, stats.monthlyViewing.months[7].totalMinutes)
        assertEquals(listOf(2026), stats.monthlyViewing.availableYears)
    }

    @Test
    fun viewingYearsIncludeIntermediateEmptyYearsAndExcludeFutureYears() {
        val stats = calculateWatchedStatistics(
            listOf(
                movie("old", watchedAt = instant("2024-05-01T10:00:00Z"), runtimeMinutes = 100)
            ),
            zone,
            LocalDate.of(2026, 8, 17),
            2025
        )

        assertEquals(listOf(2026, 2025, 2024), stats.monthlyViewing.availableYears)
        assertEquals(2025, stats.monthlyViewing.selectedYear)
        assertTrue(stats.monthlyViewing.months.all { it.totalMinutes == 0L })
    }

    @Test
    fun undatedLegacyEpisodeActivityStaysOutOfMonthlyBuckets() {
        val stats = calculateWatchedStatistics(
            listOf(series("legacy", watchedEpisodes = 2, watchedRuntimeMinutes = 80)),
            zone,
            LocalDate.of(2026, 8, 17),
            2026
        )

        assertEquals(80L, stats.seriesWatchTimeMinutes)
        assertEquals(listOf(2026), stats.monthlyViewing.availableYears)
        assertTrue(stats.monthlyViewing.months.all { it.totalMinutes == 0L })
    }

    @Test
    fun missingRuntimeMarksAffectedMonthUnavailableWithoutFabricatingMinutes() {
        val stats = calculateWatchedStatistics(
            listOf(
                series(
                    "partial",
                    watchedEpisodes = 1,
                    activities = listOf(WatchedEpisodeActivity(instant("2026-03-02T10:00:00Z"), null))
                )
            ),
            zone,
            LocalDate.of(2026, 8, 17),
            2026
        )

        val march = stats.monthlyViewing.months[2]
        assertEquals(0L, march.seriesMinutes)
        assertTrue(march.seriesTimeIncomplete)
        assertTrue(march.isIncomplete)
    }

    @Test
    fun viewingNormalizationHandlesZerosAndExactRatios() {
        val months = listOf(
            MonthlyViewingData(2026, 3, movieMinutes = 2_400),
            MonthlyViewingData(2026, 4, movieMinutes = 1_200),
            MonthlyViewingData(2026, 5, movieMinutes = 600)
        )

        assertEquals(listOf(1f, 0.5f, 0.25f), relativeViewingNormalization(months))
        assertEquals(
            listOf(0f, 0f),
            relativeViewingNormalization(listOf(MonthlyViewingData(2026, 1), MonthlyViewingData(2026, 2)))
        )
    }

    private fun movie(
        id: String,
        watchedAt: Instant? = null,
        watchedDate: LocalDate? = null,
        rating: Int? = null,
        addedAt: Instant = Instant.EPOCH,
        inLibrary: Boolean = true,
        runtimeMinutes: Int? = null,
        ratingUpdatedAt: Instant? = null,
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
        ratingUpdatedAt = ratingUpdatedAt,
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
        activities: List<WatchedEpisodeActivity> = emptyList(),
        ratingUpdatedAt: Instant? = null,
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
        watchedRegularEpisodeActivities = activities,
        seriesIsCurrentlyComplete = seriesIsCurrentlyComplete,
        ratingUpdatedAt = ratingUpdatedAt,
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
        watchedRegularEpisodeActivities: List<WatchedEpisodeActivity> = emptyList(),
        seriesIsCurrentlyComplete: Boolean? = null,
        ratingUpdatedAt: Instant? = null,
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
        watchedRegularEpisodeActivities = watchedRegularEpisodeActivities,
        seriesIsCurrentlyComplete = seriesIsCurrentlyComplete,
        personalRatingUpdatedAt = ratingUpdatedAt,
        genres = genres
    )

    private fun instant(value: String): Instant = Instant.parse(value)
}
