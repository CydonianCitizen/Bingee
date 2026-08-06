package com.cydoniancitizen.bingee.domain.equivalence

import com.cydoniancitizen.bingee.core.model.AnimeFormat
import com.cydoniancitizen.bingee.core.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaFormatCompatibilityTest {

    @Test
    fun movieToJikanMovie_compatible() {
        val result = MediaFormatCompatibility.evaluate(MediaType.MOVIE, AnimeFormat.MOVIE)
        assertEquals(FormatCompatibilityResult.COMPATIBLE, result)
    }

    @Test
    fun seriesToJikanTv_singleSeason_compatible() {
        val result = MediaFormatCompatibility.evaluate(MediaType.SERIES, AnimeFormat.TV, tmdbSeasonCount = 1)
        assertEquals(FormatCompatibilityResult.COMPATIBLE, result)
    }

    @Test
    fun seriesToJikanTv_multiSeason_granularityRisk() {
        val result = MediaFormatCompatibility.evaluate(MediaType.SERIES, AnimeFormat.TV, tmdbSeasonCount = 3)
        assertEquals(FormatCompatibilityResult.GRANULARITY_RISK, result)
    }

    @Test
    fun movieToJikanTv_incompatible() {
        val result = MediaFormatCompatibility.evaluate(MediaType.MOVIE, AnimeFormat.TV)
        assertEquals(FormatCompatibilityResult.INCOMPATIBLE, result)
    }

    @Test
    fun seriesToJikanMovie_incompatible() {
        val result = MediaFormatCompatibility.evaluate(MediaType.SERIES, AnimeFormat.MOVIE)
        assertEquals(FormatCompatibilityResult.INCOMPATIBLE, result)
    }

    @Test
    fun ovaAndSpecial_specialOrOvaRisk() {
        assertEquals(
            FormatCompatibilityResult.SPECIAL_OR_OVA_RISK,
            MediaFormatCompatibility.evaluate(MediaType.MOVIE, AnimeFormat.OVA)
        )
        assertEquals(
            FormatCompatibilityResult.SPECIAL_OR_OVA_RISK,
            MediaFormatCompatibility.evaluate(MediaType.SERIES, AnimeFormat.SPECIAL)
        )
    }
}
