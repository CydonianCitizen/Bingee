package com.cydoniancitizen.bingee.data.tmdb.search

import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TmdbMovieSearchMapperTest {
    @Test
    fun completeMovieMapsProviderIdentityAndListFields() {
        val page =
            TmdbMovieSearchMapper.map(
                TmdbMovieSearchResponseDto(
                    page = 2,
                    results =
                    listOf(
                        movie(
                            id = 550,
                            title = "Localized",
                            originalTitle = "Original",
                            posterPath = "/poster.jpg",
                            releaseDate = "1999-10-15",
                            overview = " Overview "
                        )
                    ),
                    totalPages = 4,
                    totalResults = 61
                ),
                requestedPage = 2
            )

        val result = page.results.single()
        assertEquals(MediaSource.TMDB, result.externalRef.source)
        assertEquals("550", result.externalRef.externalId)
        assertEquals(MediaType.MOVIE, result.mediaType)
        assertEquals("Localized", result.title)
        assertEquals("Original", result.originalTitle)
        assertEquals("https://image.tmdb.org/t/p/w342/poster.jpg", result.posterUrl)
        assertEquals(LocalDate.of(1999, 10, 15), result.releaseDate)
        assertEquals("Overview", result.overview)
        assertEquals(2, page.page)
        assertEquals(4, page.totalPages)
        assertEquals(61, page.totalResults)
    }

    @Test
    fun optionalFieldsAndMalformedDateStayUsable() {
        val result =
            TmdbMovieSearchMapper.mapResult(
                movie(
                    title = "Same",
                    originalTitle = "same",
                    posterPath = null,
                    releaseDate = "not-a-date",
                    overview = " "
                )
            )

        requireNotNull(result)
        assertNull(result.originalTitle)
        assertNull(result.posterUrl)
        assertNull(result.releaseDate)
        assertNull(result.overview)
    }

    @Test
    fun originalTitleIsFallbackAndRowsWithoutIdentityOrTitleAreSkipped() {
        val originalFallback =
            TmdbMovieSearchMapper.mapResult(
                movie(title = " ", originalTitle = "Original fallback")
            )

        assertEquals("Original fallback", originalFallback?.title)
        assertNull(TmdbMovieSearchMapper.mapResult(movie(id = null)))
        assertNull(TmdbMovieSearchMapper.mapResult(movie(id = 0)))
        assertNull(
            TmdbMovieSearchMapper.mapResult(
                movie(title = null, originalTitle = " ")
            )
        )
    }

    @Test
    fun mixedRowsPreserveUsableResultsAndEmptyResponseMapsEmpty() {
        val mixed =
            TmdbMovieSearchMapper.map(
                TmdbMovieSearchResponseDto(
                    page = 1,
                    results =
                    listOf(
                        movie(id = null),
                        movie(id = 2, title = null, originalTitle = null),
                        movie(id = 3, title = "Usable")
                    ),
                    totalPages = 1,
                    totalResults = 3
                ),
                requestedPage = 1
            )

        assertEquals(listOf("3"), mixed.results.map { it.externalRef.externalId })
        val empty =
            TmdbMovieSearchMapper.map(
                TmdbMovieSearchResponseDto(1, emptyList(), 1, 0),
                requestedPage = 1
            )
        assertTrue(empty.results.isEmpty())
    }

    private fun movie(
        id: Long? = 1,
        title: String? = "Movie",
        originalTitle: String? = "Original Movie",
        posterPath: String? = "/movie.jpg",
        releaseDate: String? = "2024-01-02",
        overview: String? = "Overview"
    ) = TmdbMovieSearchResultDto(
        id = id,
        title = title,
        originalTitle = originalTitle,
        posterPath = posterPath,
        releaseDate = releaseDate,
        overview = overview
    )
}
