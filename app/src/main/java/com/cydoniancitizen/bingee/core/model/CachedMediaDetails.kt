package com.cydoniancitizen.bingee.core.model

import java.time.Instant

enum class CacheFreshness {
    FRESH,
    STALE
}

data class CachedMediaDetails(val details: MediaDetails, val fetchedAt: Instant, val freshness: CacheFreshness)
