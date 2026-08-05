package com.cydoniancitizen.bingee.data.jikan.search

import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
                aired = JikanAiredDto("2023-09-29T00:00:00+00:00")
            )
        )

        requireNotNull(result)
        assertEquals(MediaSource.JIKAN, result.externalRef.source)
        assertEquals("52991", result.externalRef.externalId)
        assertEquals(MediaType.ANIME, result.mediaType)
        assertEquals("Sousou no Frieren", result.title)
        assertEquals("葬送のフリーレン", result.originalTitle)
        assertEquals("https://image/large.jpg", result.posterUrl)
        assertEquals(LocalDate.of(2023, 9, 29), result.releaseDate)
        assertEquals("Magic journey", result.overview)
    }

    @Test
    fun rejectsMissingIdentityAndKeepsOptionalMalformedFieldsSafe() {
        assertNull(JikanSearchMapper.mapResult(JikanAnimeSearchResultDto(null, "Anime", null, null, null, null, null)))
        val result = JikanSearchMapper.mapResult(
            JikanAnimeSearchResultDto(1, "Anime", null, null, " ", null, JikanAiredDto("not-a-date"))
        )
        requireNotNull(result)
        assertNull(result.releaseDate)
        assertNull(result.overview)
    }
}
