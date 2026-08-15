package com.cydoniancitizen.bingee.core.model

/** Canonical serial state. Derived from Library membership, episode progress, and one explicit override. */
enum class SeriesTrackingState {
    WATCH_LATER,
    WATCHING,
    WATCHED,
    ABANDONED
}

fun resolveSeriesTrackingState(
    inLibrary: Boolean,
    progress: SeriesProgress?,
    isAbandoned: Boolean = false
): SeriesTrackingState? {
    if (!inLibrary) return null
    if (isAbandoned) return SeriesTrackingState.ABANDONED
    if (progress == null || progress.watchedEpisodes == 0) return SeriesTrackingState.WATCH_LATER
    return if (progress.isComplete) SeriesTrackingState.WATCHED else SeriesTrackingState.WATCHING
}
