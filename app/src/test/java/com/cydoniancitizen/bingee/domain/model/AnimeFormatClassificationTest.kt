package com.cydoniancitizen.bingee.domain.model

import com.cydoniancitizen.bingee.core.model.AnimeFormat
import com.cydoniancitizen.bingee.core.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AnimeFormatClassificationTest {

    @Test
    fun movie_classifiesToMovie() {
        assertEquals(MediaType.MOVIE, AnimeFormatClassifier.toMediaType(AnimeFormat.MOVIE))
        assertEquals(MediaType.MOVIE, AnimeFormat.MOVIE.toMediaType())
    }

    @Test
    fun tv_classifiesToSeries() {
        assertEquals(MediaType.SERIES, AnimeFormatClassifier.toMediaType(AnimeFormat.TV))
        assertEquals(MediaType.SERIES, AnimeFormat.TV.toMediaType())
    }

    @Test
    fun ona_classifiesToSeries() {
        assertEquals(MediaType.SERIES, AnimeFormatClassifier.toMediaType(AnimeFormat.ONA))
        assertEquals(MediaType.SERIES, AnimeFormat.ONA.toMediaType())
    }

    @Test
    fun ova_classifiesToSeries() {
        assertEquals(MediaType.SERIES, AnimeFormatClassifier.toMediaType(AnimeFormat.OVA))
        assertEquals(MediaType.SERIES, AnimeFormat.OVA.toMediaType())
    }

    @Test
    fun special_classifiesToSeries() {
        assertEquals(MediaType.SERIES, AnimeFormatClassifier.toMediaType(AnimeFormat.SPECIAL))
        assertEquals(MediaType.SERIES, AnimeFormat.SPECIAL.toMediaType())
    }

    @Test
    fun tvSpecial_classifiesToSeries() {
        assertEquals(MediaType.SERIES, AnimeFormatClassifier.toMediaType(AnimeFormat.TV_SPECIAL))
        assertEquals(MediaType.SERIES, AnimeFormat.TV_SPECIAL.toMediaType())
    }

    @Test
    fun music_unsupported() {
        assertNull(AnimeFormatClassifier.toMediaType(AnimeFormat.MUSIC))
        assertNull(AnimeFormat.MUSIC.toMediaType())
    }

    @Test
    fun pv_unsupported() {
        assertNull(AnimeFormatClassifier.toMediaType(AnimeFormat.PV))
        assertNull(AnimeFormat.PV.toMediaType())
    }

    @Test
    fun cm_unsupported() {
        assertNull(AnimeFormatClassifier.toMediaType(AnimeFormat.CM))
        assertNull(AnimeFormat.CM.toMediaType())
    }

    @Test
    fun unknown_unsupported() {
        assertNull(AnimeFormatClassifier.toMediaType(AnimeFormat.UNKNOWN))
        assertNull(AnimeFormat.UNKNOWN.toMediaType())
    }

    @Test
    fun parseFormat_mapsRawStringsCorrectly() {
        assertEquals(AnimeFormat.TV, AnimeFormatClassifier.parseFormat("TV"))
        assertEquals(AnimeFormat.MOVIE, AnimeFormatClassifier.parseFormat("Movie"))
        assertEquals(AnimeFormat.OVA, AnimeFormatClassifier.parseFormat("ova"))
        assertEquals(AnimeFormat.ONA, AnimeFormatClassifier.parseFormat("ONA"))
        assertEquals(AnimeFormat.SPECIAL, AnimeFormatClassifier.parseFormat("Special"))
        assertEquals(AnimeFormat.TV_SPECIAL, AnimeFormatClassifier.parseFormat("TV Special"))
        assertEquals(AnimeFormat.MUSIC, AnimeFormatClassifier.parseFormat("Music"))
        assertEquals(AnimeFormat.PV, AnimeFormatClassifier.parseFormat("PV"))
        assertEquals(AnimeFormat.CM, AnimeFormatClassifier.parseFormat("CM"))
        assertEquals(AnimeFormat.UNKNOWN, AnimeFormatClassifier.parseFormat("Unknown"))
        assertEquals(AnimeFormat.UNKNOWN, AnimeFormatClassifier.parseFormat(null))
        assertEquals(AnimeFormat.UNKNOWN, AnimeFormatClassifier.parseFormat("Invalid"))
    }

    @Test
    fun allFormatsCovered() {
        AnimeFormat.entries.forEach { format ->
            when (format) {
                AnimeFormat.MOVIE -> assertEquals(MediaType.MOVIE, format.toMediaType())
                AnimeFormat.TV, AnimeFormat.ONA, AnimeFormat.OVA, AnimeFormat.SPECIAL, AnimeFormat.TV_SPECIAL ->
                    assertEquals(MediaType.SERIES, format.toMediaType())
                AnimeFormat.MUSIC, AnimeFormat.PV, AnimeFormat.CM, AnimeFormat.UNKNOWN ->
                    assertNull(format.toMediaType())
            }
        }
    }
}
