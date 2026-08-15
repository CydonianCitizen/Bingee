package com.cydoniancitizen.bingee.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class SeriesTrackingStateTest {
    @Test
    fun libraryWithNoRegularProgressIsWatchLater() {
        assertEquals(
            SeriesTrackingState.WATCH_LATER,
            resolveSeriesTrackingState(true, SeriesProgress.EMPTY)
        )
    }

    @Test
    fun oneWatchedEpisodeIsWatching() {
        assertEquals(
            SeriesTrackingState.WATCHING,
            resolveSeriesTrackingState(true, SeriesProgress(1, 3, 0, 1, false))
        )
    }

    @Test
    fun trustworthyCompleteProgressIsWatched() {
        assertEquals(
            SeriesTrackingState.WATCHED,
            resolveSeriesTrackingState(true, SeriesProgress(3, 3, 1, 1, true))
        )
    }

    @Test
    fun incompleteCoverageCannotBeWatched() {
        assertEquals(
            SeriesTrackingState.WATCHING,
            resolveSeriesTrackingState(true, SeriesProgress(3, 3, 0, 1, false))
        )
    }

    @Test
    fun specialsOnlyRemainWatchLater() {
        assertEquals(
            SeriesTrackingState.WATCH_LATER,
            resolveSeriesTrackingState(true, SeriesProgress.EMPTY)
        )
    }

    @Test
    fun abandonedOverridesDerivedStateAndClearingRestoresIt() {
        assertEquals(
            SeriesTrackingState.ABANDONED,
            resolveSeriesTrackingState(true, SeriesProgress(1, 3, 0, 1, false), isAbandoned = true)
        )
        assertEquals(
            SeriesTrackingState.ABANDONED,
            resolveSeriesTrackingState(true, SeriesProgress(3, 3, 1, 1, true), isAbandoned = true)
        )
        assertEquals(
            SeriesTrackingState.WATCHING,
            resolveSeriesTrackingState(true, SeriesProgress(1, 3, 0, 1, false), isAbandoned = false)
        )
        assertEquals(
            SeriesTrackingState.WATCHED,
            resolveSeriesTrackingState(true, SeriesProgress(3, 3, 1, 1, true), isAbandoned = false)
        )
    }
}
