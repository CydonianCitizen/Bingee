package com.cydoniancitizen.bingee.core.common

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimeFeatureAvailabilityTest {
    @Test
    fun productionAvailabilityIsFalse() {
        val policy: AnimeFeatureAvailability = ProductionAnimeFeatureAvailability()
        assertFalse("Production Anime availability must be false in 1.0.0-stable", policy.isAvailable)
    }

    @Test
    fun testingAvailabilityAllowsExplicitControl() {
        val disabled = TestingAnimeFeatureAvailability(isAvailable = false)
        assertFalse(disabled.isAvailable)

        val enabled = TestingAnimeFeatureAvailability(isAvailable = true)
        assertTrue(enabled.isAvailable)
    }
}
