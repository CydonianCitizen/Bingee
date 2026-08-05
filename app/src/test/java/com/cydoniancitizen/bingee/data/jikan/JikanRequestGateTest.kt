package com.cydoniancitizen.bingee.data.jikan

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class JikanRequestGateTest {
    @Test
    fun serializesRequestsWithProviderSpacing() = runTest {
        val clock = MutableClock()
        val waits = mutableListOf<Long>()
        val gate = JikanRequestGate(
            clock,
            JikanDelay { millis ->
                waits += millis
                clock.advance(millis)
            }
        )

        gate.awaitTurn()
        gate.awaitTurn()
        gate.awaitTurn()

        assertEquals(
            listOf(
                JikanRequestGate.MINIMUM_SPACING_MILLIS,
                JikanRequestGate.MINIMUM_SPACING_MILLIS
            ),
            waits
        )
    }

    private class MutableClock(private var currentMillis: Long = 0) : Clock() {
        override fun getZone(): ZoneId = ZoneId.of("UTC")

        override fun withZone(zone: ZoneId): Clock = this

        override fun instant(): Instant = Instant.ofEpochMilli(currentMillis)

        override fun millis(): Long = currentMillis

        fun advance(millis: Long) {
            currentMillis += millis
        }
    }
}
