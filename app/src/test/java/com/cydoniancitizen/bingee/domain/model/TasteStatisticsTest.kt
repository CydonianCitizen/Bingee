package com.cydoniancitizen.bingee.domain.model

import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.Genre
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.core.model.PersonalViewingEntry
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TasteStatisticsTest {

    @Test
    fun scopeCombinesOrSeparatesMoviesAndSeries() {
        val entries = listOf(
            movie("movie", "Drama", 18),
            series("series", 1, "Comedy", 35)
        )

        assertEquals(
            listOf(18L, 35L),
            calculateTasteStatistics(entries).rankedGenres.map { it.genreId }
        )
        assertEquals(
            listOf(18L),
            calculateTasteStatistics(entries, StatisticsMediaScope.MOVIES).rankedGenres.map { it.genreId }
        )
        assertEquals(
            listOf(35L),
            calculateTasteStatistics(entries, StatisticsMediaScope.SERIES).rankedGenres.map { it.genreId }
        )
    }

    @Test
    fun seriesContributesOnceRegardlessOfRegularEpisodeCount() {
        val oneEpisode = calculateTasteStatistics(listOf(series("series", 1, "Drama", 18)))
        val manyEpisodes = calculateTasteStatistics(listOf(series("series", 100, "Drama", 18)))

        assertEquals(oneEpisode.rankedGenres, manyEpisodes.rankedGenres)
    }

    @Test
    fun seriesTasteCohortIncludesAbandonedAndExcludesZeroRegularProgress() {
        val stats = calculateTasteStatistics(
            listOf(
                series("watching", 1, "Drama", 18),
                series("abandoned", 2, "Drama", 18, isAbandoned = true),
                series("watch-later", 0, "Comedy", 35),
                series("specials-only", 0, "Thriller", 53)
            ),
            StatisticsMediaScope.SERIES
        )

        assertEquals(listOf(18L), stats.rankedGenres.map { it.genreId })
        assertEquals(2, stats.rankedGenres.single().titleCount)
    }

    @Test
    fun movieTasteCohortUsesHistoryNotLibraryMembership() {
        val stats = calculateTasteStatistics(
            listOf(
                movie("watched-removed", "Drama", 18, watched = true, inLibrary = false),
                movie("unwatched", "Comedy", 35, watched = false)
            ),
            StatisticsMediaScope.MOVIES
        )

        assertEquals(listOf(18L), stats.rankedGenres.map { it.genreId })
    }

    @Test
    fun canonicalIdentityMergesLocalizedNamesAndKeepsDifferentIdsDistinct() {
        val stats = calculateTasteStatistics(
            listOf(
                movie(
                    "one",
                    genres = listOf(
                        Genre("Drama", MediaSource.TMDB, 18),
                        Genre("Dramma", MediaSource.TMDB, 18),
                        Genre("Comedy", MediaSource.TMDB, 35),
                        Genre("Legacy", null, null)
                    )
                ),
                movie(
                    "two",
                    genres = listOf(
                        Genre("Dramma", MediaSource.TMDB, 18),
                        Genre("Drama", MediaSource.TMDB, 53)
                    )
                )
            )
        )

        assertEquals(listOf(18L, 35L, 53L), stats.rankedGenres.map { it.genreId })
        assertEquals(listOf(2, 1, 1), stats.rankedGenres.map { it.titleCount })
    }

    @Test
    fun oneTitleContributesOnceToEachCanonicalGenre() {
        val stats = calculateTasteStatistics(
            listOf(
                movie(
                    "one",
                    genres = listOf(
                        Genre("Drama", MediaSource.TMDB, 18),
                        Genre("Dramma", MediaSource.TMDB, 18),
                        Genre("Thriller", MediaSource.TMDB, 53)
                    )
                )
            )
        )

        assertEquals(listOf(18L, 53L), stats.rankedGenres.map { it.genreId })
        assertTrue(stats.rankedGenres.all { it.titleCount == 1 })
    }

    @Test
    fun rankingUsesCountThenCanonicalIdentityAndRadarKeepsOnlyTopSix() {
        val entries = (1..8).map { id ->
            movie(
                "movie-$id",
                genres = listOf(
                    Genre("Drama", MediaSource.TMDB, 18),
                    Genre("Genre $id", MediaSource.TMDB, id.toLong() + 100)
                )
            )
        }
        val stats = calculateTasteStatistics(entries)

        assertEquals(18L, stats.rankedGenres.first().genreId)
        assertEquals(
            listOf(101L, 102L, 103L, 104L, 105L, 106L, 107L, 108L),
            stats.rankedGenres.drop(1).map { it.genreId }
        )
        assertEquals(6, stats.radarGenres.size)
        assertEquals(9, stats.rankedGenres.size)
    }

    @Test
    fun relativeNormalizationHasNoDivisionByZero() {
        assertEquals(listOf(1f, 0.75f, 0.5f, 0.25f), relativeGenreNormalization(listOf(20, 15, 10, 5)))
        assertEquals(listOf(1f, 2f / 3f, 1f / 3f), relativeGenreNormalization(listOf(3, 2, 1)))
        assertEquals(List(6) { 1f }, relativeGenreNormalization(List(6) { 5 }))
        assertEquals(List(7) { 1f }, relativeGenreNormalization(List(7) { 5 }))
        assertEquals(listOf(1f, 1f), relativeGenreNormalization(listOf(4, 4)))
        assertEquals(listOf(1f), relativeGenreNormalization(listOf(9)))
        assertEquals(emptyList<Float>(), relativeGenreNormalization(emptyList()))
        assertEquals(listOf(0f, 0f), relativeGenreNormalization(listOf(0, 0)))
    }

    private fun movie(
        id: String,
        genreName: String = "Drama",
        genreId: Long = 18,
        watched: Boolean = true,
        inLibrary: Boolean = true,
        genres: List<Genre> = listOf(Genre(genreName, MediaSource.TMDB, genreId))
    ) = entry(
        id = id,
        mediaType = MediaType.MOVIE,
        movieWatchedAt = if (watched) Instant.EPOCH else null,
        inLibrary = inLibrary,
        genres = genres
    )

    private fun series(
        id: String,
        watchedEpisodes: Int,
        genreName: String = "Drama",
        genreId: Long = 18,
        isAbandoned: Boolean = false
    ) = entry(
        id = id,
        mediaType = MediaType.SERIES,
        watchedRegularEpisodes = watchedEpisodes,
        isAbandoned = isAbandoned,
        genres = listOf(Genre(genreName, MediaSource.TMDB, genreId))
    )

    private fun entry(
        id: String,
        mediaType: MediaType,
        inLibrary: Boolean = true,
        isAbandoned: Boolean = false,
        movieWatchedAt: Instant? = null,
        watchedRegularEpisodes: Int = 0,
        genres: List<Genre>
    ) = PersonalViewingEntry(
        mediaRef = ExternalMediaRef(MediaSource.TMDB, id),
        mediaType = mediaType,
        title = id,
        addedAt = Instant.EPOCH,
        inLibrary = inLibrary,
        isFavorite = false,
        isAbandoned = isAbandoned,
        movieWatchedAt = movieWatchedAt,
        watchedRegularEpisodes = watchedRegularEpisodes,
        genres = genres
    )
}
