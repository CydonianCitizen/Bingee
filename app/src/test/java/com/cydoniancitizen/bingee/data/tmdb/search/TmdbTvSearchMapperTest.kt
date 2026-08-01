package com.cydoniancitizen.bingee.data.tmdb.search

import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TmdbTvSearchMapperTest {
    @Test
    fun completeTvResultMapsProviderIdentityAndListFields() {
        val page =
            TmdbTvSearchMapper.map(
                TmdbTvSearchResponseDto(
                    page = 1,
                    results =
                    listOf(
                        tv(
                            id = 1399,
                            name = "Localized series",
                            originalName = "Original series",
                            posterPath = "/series.png",
                            firstAirDate = "2011-04-17",
                            overview = " Story "
                        )
                    ),
                    totalPages = 3,
                    totalResults = 45
                ),
                requestedPage = 1
            )

        val result = page.results.single()
        assertEquals(MediaSource.TMDB, result.externalRef.source)
        assertEquals("1399", result.externalRef.externalId)
        assertEquals(MediaType.SERIES, result.mediaType)
        assertEquals("Localized series", result.title)
        assertEquals("Original series", result.originalTitle)
        assertEquals("https://image.tmdb.org/t/p/w342/series.png", result.posterUrl)
        assertEquals(LocalDate.of(2011, 4, 17), result.releaseDate)
        assertEquals("Story", result.overview)
        assertEquals(3, page.totalPages)
    }

    @Test
    fun missingFieldsAndMalformedDateStayUsable() {
        val result =
            TmdbTvSearchMapper.mapResult(
                tv(
                    name = "Same",
                    originalName = "SAME",
                    posterPath = null,
                    firstAirDate = "2011",
                    overview = null
                )
            )

        requireNotNull(result)
        assertNull(result.originalTitle)
        assertNull(result.posterUrl)
        assertNull(result.releaseDate)
        assertNull(result.overview)
    }

    @Test
    fun originalNameIsFallbackAndUnusableRowsAreSkipped() {
        assertEquals(
            "Original fallback",
            TmdbTvSearchMapper.mapResult(
                tv(name = null, originalName = "Original fallback")
            )?.title
        )
        assertNull(TmdbTvSearchMapper.mapResult(tv(id = null)))
        assertNull(TmdbTvSearchMapper.mapResult(tv(id = -4)))
        assertNull(TmdbTvSearchMapper.mapResult(tv(name = " ", originalName = null)))
    }

    @Test
    fun mixedAndEmptyResponsesOnlyKeepUsableRows() {
        val mixed =
            TmdbTvSearchMapper.map(
                TmdbTvSearchResponseDto(
                    page = 1,
                    results =
                    listOf(
                        tv(id = null),
                        tv(id = 7, name = "", originalName = ""),
                        tv(id = 8, name = "Usable")
                    ),
                    totalPages = 1,
                    totalResults = 3
                ),
                requestedPage = 1
            )
        assertEquals(listOf("8"), mixed.results.map { it.externalRef.externalId })

        val empty =
            TmdbTvSearchMapper.map(
                TmdbTvSearchResponseDto(1, null, null, null),
                requestedPage = 1
            )
        assertTrue(empty.results.isEmpty())
        assertEquals(1, empty.totalPages)
    }

    private fun tv(
        id: Long? = 1,
        name: String? = "Series",
        originalName: String? = "Original Series",
        posterPath: String? = "/series.jpg",
        firstAirDate: String? = "2024-01-02",
        overview: String? = "Overview"
    ) = TmdbTvSearchResultDto(
        id = id,
        name = name,
        originalName = originalName,
        posterPath = posterPath,
        firstAirDate = firstAirDate,
        overview = overview
    )
}
