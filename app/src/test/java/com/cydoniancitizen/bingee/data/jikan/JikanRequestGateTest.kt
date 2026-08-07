package com.cydoniancitizen.bingee.data.jikan

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JikanRequestGateTest {
    @Test
    fun enforcesThreeRequestsPerSecondMinimumSpacing() = runTest {
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

    @Test
    fun enforcesSixtyRequestsPerMinuteLimit() = runTest {
        val clock = MutableClock()
        val waits = mutableListOf<Long>()
        val gate = JikanRequestGate(
            clock,
            JikanDelay { millis ->
                waits += millis
                clock.advance(millis)
            }
        )

        for (i in 1..60) {
            gate.awaitTurn()
        }

        assertEquals(59, waits.size)
        assertTrue(waits.all { it == JikanRequestGate.MINIMUM_SPACING_MILLIS })
        assertEquals(59 * JikanRequestGate.MINIMUM_SPACING_MILLIS, clock.millis())
    }

    @Test
    fun sixtyFirstRequestWaitsUntilMinuteWindowAllows() = runTest {
        val clock = MutableClock()
        val waits = mutableListOf<Long>()
        val gate = JikanRequestGate(
            clock,
            JikanDelay { millis ->
                waits += millis
                clock.advance(millis)
            }
        )

        for (i in 1..60) {
            gate.awaitTurn()
        }

        gate.awaitTurn()

        assertEquals(60, waits.size)
        assertEquals(40_294L, waits.last())
        assertEquals(60_000L, clock.millis())
    }

    @Test
    fun queuesConcurrentCallersSafely() = runTest {
        val clock = MutableClock()
        val waits = mutableListOf<Long>()
        val gate = JikanRequestGate(
            clock,
            JikanDelay { millis ->
                waits += millis
                clock.advance(millis)
            }
        )

        val jobs = (1..5).map {
            async(Dispatchers.Default) {
                gate.awaitTurn()
            }
        }
        jobs.joinAll()

        assertEquals(4, waits.size)
        assertTrue(waits.all { it == JikanRequestGate.MINIMUM_SPACING_MILLIS })
        assertEquals(4 * JikanRequestGate.MINIMUM_SPACING_MILLIS, clock.millis())
    }

    @Test
    fun handlesCancellationWhileWaitingSafely() = runTest {
        val clock = MutableClock()
        var cancelled = false
        val gate = JikanRequestGate(
            clock,
            JikanDelay { millis ->
                throw CancellationException("Cancelled during delay")
            }
        )

        gate.awaitTurn()

        val job = launch {
            try {
                gate.awaitTurn()
            } catch (_: CancellationException) {
                cancelled = true
            }
        }

        job.join()
        assertTrue(cancelled)
    }

    @Test
    fun noPermanentLockAfterCancellation() = runTest {
        val clock = MutableClock()
        var cancelFirst = true
        val gate = JikanRequestGate(
            clock,
            JikanDelay { millis ->
                if (cancelFirst) {
                    cancelFirst = false
                    throw CancellationException("Cancelled during delay")
                } else {
                    clock.advance(millis)
                }
            }
        )

        gate.awaitTurn()

        val job1 = launch {
            try {
                gate.awaitTurn()
            } catch (_: CancellationException) {
                // Expected cancellation
            }
        }
        job1.join()

        gate.awaitTurn()
        assertEquals(JikanRequestGate.MINIMUM_SPACING_MILLIS, clock.millis())
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
