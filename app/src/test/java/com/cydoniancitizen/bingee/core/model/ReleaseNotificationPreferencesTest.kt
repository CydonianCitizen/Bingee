package com.cydoniancitizen.bingee.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseNotificationPreferencesTest {
    @Test
    fun defaultsAreDisabledOneDayAndAllCategoriesSelected() {
        val preferences = ReleaseNotificationPreferences()
        assertFalse(preferences.enabled)
        assertEquals(ReleaseNotificationLeadTime.ONE_DAY, preferences.leadTime)
        ReleaseEventType.entries.forEach { assertTrue(preferences.includes(it)) }
    }

    @Test
    fun categoriesAreIndependentAndEnumNamesStable() {
        val preferences = ReleaseNotificationPreferences(
            movieReleases = false,
            seasonPremieres = true,
            episodeAirings = false
        )
        assertFalse(preferences.includes(ReleaseEventType.MOVIE_RELEASE))
        assertTrue(preferences.includes(ReleaseEventType.SEASON_PREMIERE))
        assertFalse(preferences.includes(ReleaseEventType.EPISODE_AIRING))
        assertEquals(
            listOf("SAME_DAY", "ONE_DAY", "THREE_DAYS", "SEVEN_DAYS"),
            ReleaseNotificationLeadTime.entries.map { it.name }
        )
    }
}
