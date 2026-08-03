package com.cydoniancitizen.bingee.data.series

import com.cydoniancitizen.bingee.core.model.CacheFreshness
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class SeasonCacheFreshnessPolicyTest {
    private val now = Instant.parse("2026-08-03T12:00:00Z")
    private val policy = SeasonCacheFreshnessPolicy(Clock.fixed(now, ZoneOffset.UTC))

    @Test
    fun cacheIsFreshOnlyInsideTwentyFourHourWindow() {
        assertEquals(CacheFreshness.FRESH, policy.classify(now.minusSeconds(86_399)))
        assertEquals(CacheFreshness.STALE, policy.classify(now.minusSeconds(86_400)))
        assertEquals(CacheFreshness.STALE, policy.classify(now.plusSeconds(1)))
    }
}
