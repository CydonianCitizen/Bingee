package com.cydoniancitizen.bingee.core.model

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchedDateValidationTest {

    private val today = LocalDate.of(2026, 8, 6)
    private val releaseDate = LocalDate.of(2025, 5, 20)

    @Test
    fun nullWatchedDate_isValid() {
        val result = validateWatchedDate(
            watchedDate = null,
            releaseDate = releaseDate,
            today = today
        )
        assertTrue(result.isValid())
    }

    @Test
    fun todayWatchedDate_isValid() {
        val result = validateWatchedDate(
            watchedDate = today,
            releaseDate = releaseDate,
            today = today
        )
        assertTrue(result.isValid())
    }

    @Test
    fun releaseDateWatchedDate_isValid() {
        val result = validateWatchedDate(
            watchedDate = releaseDate,
            releaseDate = releaseDate,
            today = today
        )
        assertTrue(result.isValid())
    }

    @Test
    fun dateBetweenReleaseAndToday_isValid() {
        val watchedDate = LocalDate.of(2025, 12, 25)
        val result = validateWatchedDate(
            watchedDate = watchedDate,
            releaseDate = releaseDate,
            today = today
        )
        assertTrue(result.isValid())
    }

    @Test
    fun futureDate_isInvalidFutureDate() {
        val futureDate = today.plusDays(1)
        val result = validateWatchedDate(
            watchedDate = futureDate,
            releaseDate = releaseDate,
            today = today
        )
        assertFalse(result.isValid())
        assertEquals(WatchedDateValidationResult.FutureDateRejected(futureDate, today), result)
    }

    @Test
    fun dateBeforeReleaseDate_isInvalidPrecedesReleaseDate() {
        val beforeRelease = releaseDate.minusDays(1)
        val result = validateWatchedDate(
            watchedDate = beforeRelease,
            releaseDate = releaseDate,
            today = today
        )
        assertFalse(result.isValid())
        assertEquals(WatchedDateValidationResult.DatePrecedesReleaseRejected(beforeRelease, releaseDate), result)
    }

    @Test
    fun unknownReleaseDate_futureDateIsInvalid_pastDateIsValid() {
        val pastDate = LocalDate.of(2010, 1, 1)
        val validResult = validateWatchedDate(
            watchedDate = pastDate,
            releaseDate = null,
            today = today
        )
        assertTrue(validResult.isValid())

        val futureDate = today.plusDays(5)
        val invalidResult = validateWatchedDate(
            watchedDate = futureDate,
            releaseDate = null,
            today = today
        )
        assertFalse(invalidResult.isValid())
        assertEquals(WatchedDateValidationResult.FutureDateRejected(futureDate, today), invalidResult)
    }
}
