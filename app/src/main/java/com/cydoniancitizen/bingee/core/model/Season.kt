package com.cydoniancitizen.bingee.core.model

import java.time.LocalDate

data class Season(
    val seriesRef: ExternalMediaRef,
    val seasonNumber: Int,
    val externalRef: ExternalMediaRef? = null,
    val name: String? = null,
    val airDate: LocalDate? = null,
    val episodeCount: Int? = null
) {
    init {
        require(seasonNumber >= 0) { "Season number must not be negative" }
        require(episodeCount == null || episodeCount >= 0) { "Episode count must not be negative" }
        require(externalRef == null || externalRef.source == seriesRef.source) {
            "Season and series references must use the same provider"
        }
    }
}
