package com.cydoniancitizen.bingee.core.model

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AnimeWatchProgressTest {
    private val now = Instant.parse("2026-08-05T10:00:00Z")

    @Test
    fun knownTotalDerivesNotStartedInProgressAndCompletedWithoutReducingCount() {
        assertEquals(AnimeProgressState.NOT_STARTED, AnimeWatchProgress(0, null, null, now).state(12))
        assertEquals(AnimeProgressState.IN_PROGRESS, AnimeWatchProgress(5, null, null, now).state(12))
        assertEquals(
            AnimeProgressState.COMPLETED,
            AnimeWatchProgress(12, now, AnimeCompletionOrigin.INFERRED, now).state(12)
        )
        assertEquals(AnimeProgressState.COMPLETED, AnimeWatchProgress(15, null, null, now).state(12))
    }

    @Test
    fun unknownTotalRequiresExplicitCompletion() {
        assertEquals(AnimeProgressState.IN_PROGRESS, AnimeWatchProgress(3, null, null, now).state(null))
        assertEquals(
            AnimeProgressState.COMPLETED,
            AnimeWatchProgress(3, now, AnimeCompletionOrigin.EXPLICIT, now).state(null)
        )
    }

    @Test
    fun negativeAndAboveCentralSafetyBoundAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            AnimeWatchProgress(-1, null, null, now)
        }
        assertThrows(IllegalArgumentException::class.java) {
            AnimeWatchProgress(AnimeWatchProgress.MAX_WATCHED_EPISODES + 1, null, null, now)
        }
    }
}
