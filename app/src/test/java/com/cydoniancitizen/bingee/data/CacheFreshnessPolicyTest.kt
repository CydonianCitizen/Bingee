package com.cydoniancitizen.bingee.data

import com.cydoniancitizen.bingee.core.model.CacheFreshness
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class CacheFreshnessPolicyTest {
    private val now = Instant.parse("2026-08-03T12:00:00Z")
    private val policy = CacheFreshnessPolicy(Clock.fixed(now, ZoneOffset.UTC))

    @Test
    fun youngerThanTwentyFourHoursIsFresh() {
        assertEquals(CacheFreshness.FRESH, policy.classify(now.minusSeconds(86_399)))
    }

    @Test
    fun exactlyAtTwentyFourHourBoundaryIsStale() {
        assertEquals(CacheFreshness.STALE, policy.classify(now.minusSeconds(86_400)))
    }

    @Test
    fun olderThanTwentyFourHoursIsStale() {
        assertEquals(CacheFreshness.STALE, policy.classify(now.minusSeconds(86_401)))
    }

    @Test
    fun futureTimestampIsStaleInsteadOfIndefinitelyFresh() {
        assertEquals(CacheFreshness.STALE, policy.classify(now.plusSeconds(1)))
    }

    @Test
    fun textCachedInAnotherLanguageIsStaleWhileSameOrUnknownLanguageAgesNormally() {
        val young = now.minusSeconds(60)

        assertEquals(CacheFreshness.STALE, policy.classify(young, "en-US", "it-IT"))
        assertEquals(CacheFreshness.FRESH, policy.classify(young, "it-IT", "it-IT"))
        assertEquals(CacheFreshness.FRESH, policy.classify(young, null, "it-IT"))
        assertEquals(CacheFreshness.STALE, policy.classify(now.minusSeconds(86_400), "it-IT", "it-IT"))
    }
}
