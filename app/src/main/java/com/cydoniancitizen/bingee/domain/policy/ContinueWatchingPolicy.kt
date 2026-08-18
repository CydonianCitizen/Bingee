package com.cydoniancitizen.bingee.domain.policy

import com.cydoniancitizen.bingee.core.model.ContinueWatchingItem
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.core.model.SeriesTrackingState
import com.cydoniancitizen.bingee.core.model.resolveSeriesTrackingState
import java.time.Instant
import java.util.Locale

object ContinueWatchingPolicy {
    fun select(items: Iterable<ContinueWatchingItem>): List<ContinueWatchingItem> = items
        .filter(::isContinueWatching)
        .sortedWith(
            compareByDescending<ContinueWatchingItem> { it.updatedAt ?: Instant.MIN }
                .thenBy { it.title.lowercase(Locale.ROOT) }
                .thenBy { it.mediaRef.source.name }
                .thenBy { it.mediaRef.externalId }
        )

    fun isContinueWatching(item: ContinueWatchingItem): Boolean = item.mediaType == MediaType.SERIES &&
        resolveSeriesTrackingState(item.inLibrary, item.progress, item.isAbandoned) ==
        SeriesTrackingState.WATCHING &&
        item.progress.watchedEpisodes < item.progress.trackableEpisodes
}
