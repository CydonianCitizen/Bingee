package com.cydoniancitizen.bingee.core.model

import java.time.Instant
import java.time.LocalDate

data class Season(
    val seriesRef: ExternalMediaRef,
    val externalRef: ExternalMediaRef,
    val seasonNumber: Int,
    val name: String? = null,
    val overview: String? = null,
    val posterUrl: String? = null,
    val airDate: LocalDate? = null,
    val episodeCount: Int = 0
) {
    init {
        require(seasonNumber >= 0) { "Season number must not be negative" }
        require(episodeCount >= 0) { "Episode count must not be negative" }
        require(externalRef.source == seriesRef.source) {
            "Season and series references must use the same provider"
        }
    }
}

data class CachedSeason(
    val season: Season,
    val metadataUpdatedAt: Instant,
    val episodesFetchedAt: Instant?,
    val episodes: List<TrackedEpisode>,
    val progress: SeasonProgress,
    val episodeCacheFreshness: CacheFreshness?
)
