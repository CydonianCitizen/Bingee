package com.cydoniancitizen.bingee.data.jikan.search

import com.cydoniancitizen.bingee.core.model.AnimeFormat
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JikanSearchMapperTest {
    @Test
    fun mapsQualifiedAnimeIdentityTitlesDatesImagesAndNormalizedSynopsis() {
        val result = JikanSearchMapper.mapResult(
            JikanAnimeSearchResultDto(
                malId = 52991,
                title = "Sousou no Frieren",
                titleEnglish = "Frieren: Beyond Journey's End",
                titleJapanese = "葬送のフリーレン",
                synopsis = " <b>Magic</b>\n journey ",
                images = JikanImagesDto(JikanJpgImageDto("https://image/large.jpg", "https://image/small.jpg")),
                type = "TV",
                status = "Finished Airing",
                episodes = 28,
                score = 9.1,
                aired = JikanAiredDto("2023-09-29T00:00:00+00:00")
            )
        )

        requireNotNull(result)
        assertEquals(MediaSource.JIKAN, result.externalRef.source)
        assertEquals("52991", result.externalRef.externalId)
        assertEquals(MediaType.SERIES, result.mediaType)
        assertEquals(AnimeFormat.TV, result.animeFormat)
        assertEquals("Frieren: Beyond Journey's End", result.title)
        assertEquals("Sousou no Frieren", result.originalTitle)
        assertEquals("https://image/large.jpg", result.posterUrl)
        assertEquals(LocalDate.of(2023, 9, 29), result.releaseDate)
        assertEquals("Magic journey", result.overview)
        assertEquals(28, result.episodes)
        assertEquals("Finished Airing", result.status)
        assertEquals(9.1, result.score!!, 0.01)
    }

    @Test
    fun titlePrecedencePrefersEnglishThenCanonicalThenJapaneseFallback() {
        val canonicalOnly = JikanSearchMapper.mapResult(
            JikanAnimeSearchResultDto(malId = 1, title = "Canonical", type = "TV")
        )
        assertEquals("Canonical", canonicalOnly?.title)
        assertNull(canonicalOnly?.originalTitle)

        val japaneseFallback = JikanSearchMapper.mapResult(
            JikanAnimeSearchResultDto(malId = 2, titleJapanese = "日本語", type = "Movie")
        )
        assertEquals("日本語", japaneseFallback?.title)
        assertNull(japaneseFallback?.originalTitle)
    }

    @Test
    fun scoreZeroIsExcludedFromMappedResult() {
        val zeroScore = JikanSearchMapper.mapResult(
            JikanAnimeSearchResultDto(malId = 1, title = "Test", type = "TV", score = 0.0)
        )
        assertNull(zeroScore?.score)
    }

    @Test
    fun mapsMovieToMovieTypeAndPreservesJikanIdentity() {
        val result = JikanSearchMapper.mapResult(
            JikanAnimeSearchResultDto(
                malId = 37991,
                title = "Kimi no Na wa.",
                type = "Movie"
            )
        )
        requireNotNull(result)
        assertEquals(MediaSource.JIKAN, result.externalRef.source)
        assertEquals("37991", result.externalRef.externalId)
        assertEquals(MediaType.MOVIE, result.mediaType)
        assertEquals(AnimeFormat.MOVIE, result.animeFormat)
    }

    @Test
    fun mapsTvOnaOvaSpecialTvSpecialToSeriesTypeAndPreservesJikanIdentity() {
        val formats = listOf(
            "TV" to AnimeFormat.TV,
            "ONA" to AnimeFormat.ONA,
            "OVA" to AnimeFormat.OVA,
            "Special" to AnimeFormat.SPECIAL,
            "TV Special" to AnimeFormat.TV_SPECIAL
        )
        for ((rawType, expectedFormat) in formats) {
            val result = JikanSearchMapper.mapResult(
                JikanAnimeSearchResultDto(
                    malId = 100,
                    title = "Test Series",
                    type = rawType
                )
            )
            requireNotNull(result)
            assertEquals(MediaSource.JIKAN, result.externalRef.source)
            assertEquals("100", result.externalRef.externalId)
            assertEquals(MediaType.SERIES, result.mediaType)
            assertEquals(expectedFormat, result.animeFormat)
        }
    }

    @Test
    fun excludesUnsupportedFormats() {
        val unsupportedTypes = listOf("Music", "PV", "CM", "Unknown", null, "InvalidFormat")
        for (rawType in unsupportedTypes) {
            val result = JikanSearchMapper.mapResult(
                JikanAnimeSearchResultDto(
                    malId = 200,
                    title = "Test Unsupported",
                    type = rawType
                )
            )
            assertNull("Expected null result for unsupported type: $rawType", result)
        }
    }

    @Test
    fun neverEmitsAnimeMediaTypeInSearchResults() {
        val searchResponse = JikanAnimeSearchResponseDto(
            data = listOf(
                JikanAnimeSearchResultDto(1, "TV Show", type = "TV"),
                JikanAnimeSearchResultDto(2, "Anime Movie", type = "Movie"),
                JikanAnimeSearchResultDto(3, "Music Video", type = "Music")
            ),
            pagination = JikanPaginationDto(lastVisiblePage = 1, hasNextPage = false)
        )
        val page = JikanSearchMapper.map(searchResponse, requestedPage = 1)
        assertEquals(2, page.results.size)
        for (result in page.results) {
            assertNotEquals(MediaType.ANIME, result.mediaType)
            assertTrue(result.mediaType == MediaType.MOVIE || result.mediaType == MediaType.SERIES)
        }
    }

    @Test
    fun rejectsMissingIdentityAndKeepsOptionalMalformedFieldsSafe() {
        assertNull(JikanSearchMapper.mapResult(JikanAnimeSearchResultDto(null, "Anime", null, null, null, null, type = "TV")))
        val result = JikanSearchMapper.mapResult(
            JikanAnimeSearchResultDto(1, "Anime", null, null, " ", null, type = "TV", aired = JikanAiredDto("not-a-date"))
        )
        requireNotNull(result)
        assertNull(result.releaseDate)
        assertNull(result.overview)
    }
}
