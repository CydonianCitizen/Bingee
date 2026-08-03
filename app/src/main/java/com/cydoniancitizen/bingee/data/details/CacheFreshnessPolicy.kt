package com.cydoniancitizen.bingee.data.details

import com.cydoniancitizen.bingee.core.model.CacheFreshness
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

internal class CacheFreshnessPolicy @Inject constructor(private val clock: Clock) {
    fun classify(fetchedAt: Instant): CacheFreshness {
        val now = clock.instant()
        if (fetchedAt.isAfter(now)) return CacheFreshness.STALE
        return if (Duration.between(fetchedAt, now) < MAX_AGE) {
            CacheFreshness.FRESH
        } else {
            CacheFreshness.STALE
        }
    }

    companion object {
        val MAX_AGE: Duration = Duration.ofHours(24)
    }
}
