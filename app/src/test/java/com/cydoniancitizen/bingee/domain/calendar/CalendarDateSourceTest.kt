package com.cydoniancitizen.bingee.domain.calendar

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class CalendarDateSourceTest {
    private val instant = Instant.parse("2026-08-18T22:30:00Z")
    private val clock = Clock.fixed(instant, ZoneOffset.UTC)

    @Test
    fun currentLocalDateUsesZoneAtBoundary() {
        assertEquals(
            "2026-08-19",
            currentLocalDate(clock, ZoneId.of("Europe/Rome")).toString()
        )
        assertEquals(
            "2026-08-18",
            currentLocalDate(clock, ZoneId.of("America/New_York")).toString()
        )
    }

    @Test
    fun currentLocalDateRecomputesWhenZoneChanges() {
        var zone = ZoneId.of("Europe/Rome")
        assertEquals("2026-08-19", currentLocalDate(clock, zone).toString())

        zone = ZoneId.of("America/New_York")
        assertEquals("2026-08-18", currentLocalDate(clock, zone).toString())
    }

    @Test
    fun nextMidnightHandlesNewYearWithoutSpecialCase() {
        val newYearEve = Instant.parse("2026-12-31T23:59:00Z")

        assertEquals(
            60_000L,
            millisUntilNextLocalMidnight(newYearEve, ZoneOffset.UTC)
        )
    }
}
