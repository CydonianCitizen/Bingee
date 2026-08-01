package com.cydoniancitizen.bingee.core.model

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainModelsTest {
    @Test
    fun externalReferencesUseProviderAndIdForEquality() {
        val first = ExternalMediaRef(MediaSource.TMDB, "42")
        val equal = ExternalMediaRef(MediaSource.TMDB, "42")
        val otherProvider = ExternalMediaRef(MediaSource.JIKAN, "42")

        assertEquals(first, equal)
        assertNotEquals(first, otherProvider)
        assertEquals(2, setOf(first, equal, otherProvider).size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun externalReferenceRejectsBlankId() {
        ExternalMediaRef(MediaSource.TMDB, "   ")
    }

    @Test
    fun mediaTypeRepresentsStructureOnly() {
        assertEquals(listOf(MediaType.MOVIE, MediaType.SERIES), MediaType.entries)
        assertTrue(MediaType.entries.none { it.name.contains("ANIME") })
    }

    @Test
    fun seasonZeroRepresentsSpecials() {
        val seriesRef = ExternalMediaRef(MediaSource.TMDB, "100")

        val specials =
            Season(
                seriesRef = seriesRef,
                seasonNumber = 0,
                name = "Specials"
            )

        assertEquals(0, specials.seasonNumber)
    }

    @Test
    fun releaseTimingPreservesDateOnlyValue() {
        val date = LocalDate.of(2027, 3, 10)
        val event =
            ReleaseEvent(
                mediaRef = ExternalMediaRef(MediaSource.TMDB, "200"),
                mediaType = MediaType.MOVIE,
                timing = ReleaseTiming.DateOnly(date)
            )

        assertEquals(ReleaseTiming.DateOnly(date), event.timing)
    }
}
