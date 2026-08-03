package com.cydoniancitizen.bingee.data.tmdb.details

import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.core.model.ProductionStatus
import java.time.Duration
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TmdbTvDetailsMapperTest {
    @Test
    fun completeResponseMapsTvLevelFieldsWithoutSeasonRows() {
        val details = requireNotNull(TmdbTvDetailsMapper.map(tv()))

        assertEquals("1399", details.externalRef.externalId)
        assertEquals(MediaType.SERIES, details.mediaType)
        assertEquals("Localized TV", details.title)
        assertEquals("Original TV", details.originalTitle)
        assertEquals(LocalDate.of(2011, 4, 17), details.releaseDate)
        assertEquals(Duration.ofMinutes(57), details.episodeRuntime)
        assertEquals(8, details.numberOfSeasons)
        assertEquals(73, details.numberOfEpisodes)
        assertEquals(ProductionStatus.ENDED, details.productionStatus)
        assertEquals(listOf("Drama"), details.genres.map { it.name })
        assertEquals("en", details.originalLanguage)
    }

    @Test
    fun episodeRuntimeUsesFirstPositiveProviderValue() {
        assertEquals(
            Duration.ofMinutes(42),
            requireNotNull(TmdbTvDetailsMapper.map(tv(episodeRunTime = listOf(0, -2, 42, 55)))).episodeRuntime
        )
        assertNull(requireNotNull(TmdbTvDetailsMapper.map(tv(episodeRunTime = emptyList()))).episodeRuntime)
        assertNull(requireNotNull(TmdbTvDetailsMapper.map(tv(episodeRunTime = null))).episodeRuntime)
    }

    @Test
    fun zeroCountsAreRetainedAndNegativeCountsBecomeMissing() {
        val zero = requireNotNull(TmdbTvDetailsMapper.map(tv(numberOfSeasons = 0, numberOfEpisodes = 0)))
        assertEquals(0, zero.numberOfSeasons)
        assertEquals(0, zero.numberOfEpisodes)
        val negative = requireNotNull(TmdbTvDetailsMapper.map(tv(numberOfSeasons = -1, numberOfEpisodes = -2)))
        assertNull(negative.numberOfSeasons)
        assertNull(negative.numberOfEpisodes)
    }

    @Test
    fun missingImagesAndMalformedDateRemainRepresentable() {
        val details =
            requireNotNull(TmdbTvDetailsMapper.map(tv(posterPath = null, backdropPath = null, firstAirDate = "bad")))
        assertNull(details.posterUrl)
        assertNull(details.backdropUrl)
        assertNull(details.releaseDate)
    }

    @Test
    fun knownAndUnknownTvStatusesMapExplicitly() {
        val expected = mapOf(
            "Returning Series" to ProductionStatus.RETURNING_SERIES,
            "Planned" to ProductionStatus.PLANNED,
            "In Production" to ProductionStatus.IN_PRODUCTION,
            "Ended" to ProductionStatus.ENDED,
            "Canceled" to ProductionStatus.CANCELED,
            "Pilot" to ProductionStatus.PILOT,
            "unexpected" to ProductionStatus.UNKNOWN
        )
        expected.forEach { (raw, status) ->
            assertEquals(status, requireNotNull(TmdbTvDetailsMapper.map(tv(status = raw))).productionStatus)
        }
    }

    @Test
    fun unusableIdentityOrNameIsRejectedAndOriginalNameCanFallback() {
        assertNull(TmdbTvDetailsMapper.map(tv(id = -1)))
        assertNull(TmdbTvDetailsMapper.map(tv(name = " ", originalName = null)))
        val fallback = requireNotNull(TmdbTvDetailsMapper.map(tv(name = null, originalName = "Fallback TV")))
        assertEquals("Fallback TV", fallback.title)
        assertNull(fallback.originalTitle)
    }

    private fun tv(
        id: Long? = 1399,
        name: String? = "Localized TV",
        originalName: String? = "Original TV",
        posterPath: String? = "/poster.jpg",
        backdropPath: String? = "/backdrop.jpg",
        firstAirDate: String? = "2011-04-17",
        status: String? = "Ended",
        episodeRunTime: List<Int>? = listOf(57),
        numberOfSeasons: Int? = 8,
        numberOfEpisodes: Int? = 73
    ) = TmdbTvDetailsDto(
        id = id,
        name = name,
        originalName = originalName,
        overview = "Overview",
        posterPath = posterPath,
        backdropPath = backdropPath,
        firstAirDate = firstAirDate,
        genres = listOf(TmdbGenreDto("Drama")),
        status = status,
        episodeRunTime = episodeRunTime,
        numberOfSeasons = numberOfSeasons,
        numberOfEpisodes = numberOfEpisodes,
        originalLanguage = "en"
    )
}
