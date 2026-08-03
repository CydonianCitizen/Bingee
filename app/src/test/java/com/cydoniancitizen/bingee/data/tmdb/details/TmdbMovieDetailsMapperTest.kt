package com.cydoniancitizen.bingee.data.tmdb.details

import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.core.model.ProductionStatus
import java.time.Duration
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TmdbMovieDetailsMapperTest {
    @Test
    fun completeResponseMapsProviderIdentityMetadataAndImages() {
        val details = requireNotNull(TmdbMovieDetailsMapper.map(movie()))

        assertEquals(MediaSource.TMDB, details.externalRef.source)
        assertEquals("550", details.externalRef.externalId)
        assertEquals(MediaType.MOVIE, details.mediaType)
        assertEquals("Localized", details.title)
        assertEquals("Original", details.originalTitle)
        assertEquals("Overview", details.overview)
        assertEquals("https://image.tmdb.org/t/p/w500/poster.jpg", details.posterUrl)
        assertEquals("https://image.tmdb.org/t/p/w780/backdrop.jpg", details.backdropUrl)
        assertEquals(LocalDate.of(1999, 10, 15), details.releaseDate)
        assertEquals(listOf("Drama", "Thriller"), details.genres.map { it.name })
        assertEquals(ProductionStatus.RELEASED, details.productionStatus)
        assertEquals(Duration.ofMinutes(139), details.runtime)
        assertEquals("en", details.originalLanguage)
    }

    @Test
    fun optionalTextImagesAndDatesAreSafeAndOriginalTitleCanSupplyDisplayTitle() {
        val details = requireNotNull(
            TmdbMovieDetailsMapper.map(
                movie(
                    title = " ",
                    originalTitle = "Fallback",
                    overview = " ",
                    posterPath = null,
                    backdropPath = "bad",
                    releaseDate = "not-a-date"
                )
            )
        )

        assertEquals("Fallback", details.title)
        assertNull(details.originalTitle)
        assertNull(details.overview)
        assertNull(details.posterUrl)
        assertNull(details.backdropUrl)
        assertNull(details.releaseDate)
    }

    @Test
    fun zeroAndNegativeRuntimeAreNormalizedToMissing() {
        assertNull(requireNotNull(TmdbMovieDetailsMapper.map(movie(runtime = 0))).runtime)
        assertNull(requireNotNull(TmdbMovieDetailsMapper.map(movie(runtime = -1))).runtime)
        assertNull(requireNotNull(TmdbMovieDetailsMapper.map(movie(runtime = null))).runtime)
    }

    @Test
    fun knownAndUnknownStatusesMapWithoutRawProviderText() {
        val expected = mapOf(
            "Rumored" to ProductionStatus.RUMORED,
            "Planned" to ProductionStatus.PLANNED,
            "In Production" to ProductionStatus.IN_PRODUCTION,
            "Post Production" to ProductionStatus.POST_PRODUCTION,
            "Released" to ProductionStatus.RELEASED,
            "Canceled" to ProductionStatus.CANCELED,
            "new provider value" to ProductionStatus.UNKNOWN
        )
        expected.forEach { (raw, status) ->
            assertEquals(status, requireNotNull(TmdbMovieDetailsMapper.map(movie(status = raw))).productionStatus)
        }
    }

    @Test
    fun unusableIdentityOrTitleIsRejected() {
        assertNull(TmdbMovieDetailsMapper.map(movie(id = 0)))
        assertNull(TmdbMovieDetailsMapper.map(movie(id = null)))
        assertNull(TmdbMovieDetailsMapper.map(movie(title = " ", originalTitle = null)))
    }

    private fun movie(
        id: Long? = 550,
        title: String? = "Localized",
        originalTitle: String? = "Original",
        overview: String? = "Overview",
        posterPath: String? = "/poster.jpg",
        backdropPath: String? = "/backdrop.jpg",
        releaseDate: String? = "1999-10-15",
        status: String? = "Released",
        runtime: Int? = 139
    ) = TmdbMovieDetailsDto(
        id = id,
        title = title,
        originalTitle = originalTitle,
        overview = overview,
        posterPath = posterPath,
        backdropPath = backdropPath,
        releaseDate = releaseDate,
        genres = listOf(TmdbGenreDto("Drama"), TmdbGenreDto("Thriller"), TmdbGenreDto(" ")),
        status = status,
        runtime = runtime,
        originalLanguage = "en"
    )
}
