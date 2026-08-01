package com.cydoniancitizen.bingee.core.model

import java.time.Duration
import java.time.LocalDate

data class Episode(
    val seriesRef: ExternalMediaRef,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val externalRef: ExternalMediaRef? = null,
    val name: String? = null,
    val airDate: LocalDate? = null,
    val runtime: Duration? = null
) {
    init {
        require(seasonNumber >= 0) { "Season number must not be negative" }
        require(episodeNumber > 0) { "Episode number must be positive" }
        require(externalRef == null || externalRef.source == seriesRef.source) {
            "Episode and series references must use the same provider"
        }
    }
}
