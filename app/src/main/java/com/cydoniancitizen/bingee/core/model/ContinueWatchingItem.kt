package com.cydoniancitizen.bingee.core.model

import java.time.Instant

data class ContinueWatchingItem(
    val mediaRef: ExternalMediaRef,
    val mediaType: MediaType,
    val title: String,
    val posterUrl: String?,
    val progress: SeriesProgress,
    val nextEpisode: EpisodePosition?,
    val updatedAt: Instant?,
    val isAbandoned: Boolean = false,
    val inLibrary: Boolean = true
) {
    init {
        require(title.isNotBlank()) { "Continue Watching title must not be blank" }
    }
}

data class EpisodePosition(val seasonNumber: Int, val episodeNumber: Int) {
    init {
        require(seasonNumber > 0) { "Continue Watching season number must be positive" }
        require(episodeNumber > 0) { "Continue Watching episode number must be positive" }
    }
}

fun LibraryEntry.toContinueWatchingItem(): ContinueWatchingItem? = (progress as? LibraryProgress.Series)?.let {
    ContinueWatchingItem(
        mediaRef = mediaRef,
        mediaType = mediaType,
        title = title,
        posterUrl = posterUrl,
        progress = it.progress,
        nextEpisode = it.progress.nextEpisode,
        updatedAt = it.progress.lastWatchedAt,
        isAbandoned = isAbandoned,
        inLibrary = inLibrary
    )
}
