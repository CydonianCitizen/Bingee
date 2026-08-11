package com.cydoniancitizen.bingee.domain.policy

import com.cydoniancitizen.bingee.core.model.ContinueWatchingItem
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

    fun isContinueWatching(item: ContinueWatchingItem): Boolean =
        SeriesFollowPolicy.isFollowedSeries(item.mediaType, item.inLibrary) &&
            item.progress.watchedEpisodes > 0 &&
            !item.progress.isComplete
}
