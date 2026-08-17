package com.cydoniancitizen.bingee.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ViewingDurationTest {
    private val labels = ViewingDurationLabels(day = "d", hour = "h", minute = "m")

    @Test
    fun formatsMinutesOnly() {
        assertEquals("48m", formatViewingDuration(48, labels))
    }

    @Test
    fun formatsHoursAndMinutes() {
        assertEquals("14h 32m", formatViewingDuration(872, labels))
    }

    @Test
    fun formatsDaysAndHours() {
        assertEquals("3d 14h", formatViewingDuration(5_160, labels))
    }

    @Test
    fun formatsDaysHoursAndMinutes() {
        assertEquals("3d 14h 27m", formatViewingDuration(5_187, labels))
    }

    @Test
    fun keepsExactMinutePrecision() {
        assertEquals("1h 1m", formatViewingDuration(61, labels))
        assertEquals("3d", formatViewingDuration(4_320, labels))
    }
}
