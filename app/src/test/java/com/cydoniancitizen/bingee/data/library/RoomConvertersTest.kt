package com.cydoniancitizen.bingee.data.library

import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.data.library.local.RoomConverters
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RoomConvertersTest {
    private val converters = RoomConverters()

    @Test
    fun enumsPersistByStableNameInsteadOfOrdinal() {
        assertEquals("TMDB", converters.mediaSourceToString(MediaSource.TMDB))
        assertEquals(MediaSource.JIKAN, converters.stringToMediaSource("JIKAN"))
        assertEquals("MOVIE", converters.mediaTypeToString(MediaType.MOVIE))
        assertEquals(MediaType.SERIES, converters.stringToMediaType("SERIES"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun malformedEnumFailsInsteadOfSilentlyChangingMeaning() {
        converters.stringToMediaSource("UNKNOWN")
    }

    @Test
    fun datesAndInstantsUseIsoUtcText() {
        val date = LocalDate.of(2026, 8, 1)
        val instant = Instant.parse("2026-08-01T12:34:56Z")

        assertEquals("2026-08-01", converters.localDateToString(date))
        assertEquals(date, converters.stringToLocalDate("2026-08-01"))
        assertNull(converters.localDateToString(null))
        assertNull(converters.stringToLocalDate(null))
        assertEquals("2026-08-01T12:34:56Z", converters.instantToString(instant))
        assertEquals(instant, converters.stringToInstant("2026-08-01T12:34:56Z"))
    }
}
