package com.cydoniancitizen.bingee.data.jikan.details

import com.cydoniancitizen.bingee.core.model.AnimeFormat
import com.cydoniancitizen.bingee.core.model.AnimeStatus
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.data.jikan.search.JikanAiredDto
import com.cydoniancitizen.bingee.data.jikan.search.JikanImagesDto
import com.cydoniancitizen.bingee.data.jikan.search.JikanJpgImageDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class JikanDetailsMapperTest {
    @Test
    fun mapsIdentityMetadataRelationsAndNormalizesProviderText() {
        val details = JikanDetailsMapper.map(
            JikanAnimeFullResponseDto(
                JikanAnimeFullDto(
                    malId = 52991,
                    title = "  Frieren  ",
                    titleEnglish = "Frieren: Beyond Journey's End",
                    titleJapanese = "葬送のフリーレン",
                    synopsis = "<b>Magic</b> &amp; memory",
                    images = JikanImagesDto(JikanJpgImageDto("https://image/large.jpg", "https://image/small.jpg")),
                    type = "TV Special",
                    status = "Currently Airing",
                    episodes = 28,
                    duration = "24 min per ep",
                    aired = JikanAiredDto("2023-09-29T00:00:00+00:00", "2024-03-22T00:00:00+00:00"),
                    season = "fall",
                    year = 2023,
                    score = 9.31,
                    relations = listOf(
                        JikanRelationDto(
                            "Sequel",
                            listOf(
                                JikanRelationEntryDto(60000, "anime", "Next"),
                                JikanRelationEntryDto(1, "manga", "Ignored")
                            )
                        )
                    )
                )
            )
        )

        assertEquals(MediaSource.JIKAN, details.externalRef.source)
        assertEquals("52991", details.externalRef.externalId)
        assertEquals(AnimeFormat.TV_SPECIAL, details.format)
        assertEquals(AnimeStatus.AIRING, details.status)
        assertEquals("Magic & memory", details.synopsis)
        assertEquals("2023-09-29", details.startDate.toString())
        assertEquals("2024-03-22", details.endDate.toString())
        assertEquals("60000", details.relations.single().animeRef.externalId)
        assertEquals(9.31, details.providerScore)
    }

    @Test
    fun unknownEnumsAndZeroScoreAreSafeButMissingIdentityFails() {
        val unknown = JikanDetailsMapper.map(
            JikanAnimeFullResponseDto(
                JikanAnimeFullDto(
                    1, "Title", null, null, null, null, "Future format", "Future status",
                    null, null, null, null, null, 0.0, null
                )
            )
        )
        assertEquals(AnimeFormat.UNKNOWN, unknown.format)
        assertEquals(AnimeStatus.UNKNOWN, unknown.status)
        assertNull(unknown.providerScore)

        assertThrows(IllegalStateException::class.java) {
            JikanDetailsMapper.map(
                JikanAnimeFullResponseDto(
                    JikanAnimeFullDto(
                        null, "Title", null, null, null, null, null, null,
                        null, null, null, null, null, null, null
                    )
                )
            )
        }
    }
}
