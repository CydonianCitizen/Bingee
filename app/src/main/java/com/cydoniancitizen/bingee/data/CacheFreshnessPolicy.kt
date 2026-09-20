package com.cydoniancitizen.bingee.data

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

    /**
     * Text cached in another language than the one now requested is stale however young it is, so a
     * language change refreshes it while the old text stays readable. An unknown language ages normally.
     */
    fun classify(fetchedAt: Instant, cachedLanguage: String?, requestedLanguage: String): CacheFreshness =
        if (cachedLanguage != null && cachedLanguage != requestedLanguage) CacheFreshness.STALE else classify(fetchedAt)

    companion object {
        val MAX_AGE: Duration = Duration.ofHours(24)
    }
}
