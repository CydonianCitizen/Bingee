package com.cydoniancitizen.bingee.core.model

import java.time.Duration
import java.time.LocalDate

data class Episode(
    val seriesRef: ExternalMediaRef,
    val seasonRef: ExternalMediaRef,
    val externalRef: ExternalMediaRef,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val title: String,
    val overview: String? = null,
    val airDate: LocalDate? = null,
    val runtime: Duration? = null,
    val stillUrl: String? = null
) {
    init {
        require(seasonNumber >= 0) { "Season number must not be negative" }
        require(episodeNumber > 0) { "Episode number must be positive" }
        require(title.isNotBlank()) { "Episode title must not be blank" }
        require(seriesRef.source == seasonRef.source && seasonRef.source == externalRef.source) {
            "Series, season, and episode references must use the same provider"
        }
        require(runtime == null || (!runtime.isNegative && !runtime.isZero)) {
            "Episode runtime must be positive"
        }
    }
}
