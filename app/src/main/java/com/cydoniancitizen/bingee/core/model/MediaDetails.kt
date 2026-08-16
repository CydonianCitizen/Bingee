package com.cydoniancitizen.bingee.core.model

import java.time.Duration
import java.time.LocalDate

data class Genre(val name: String, val source: MediaSource? = null, val genreId: Long? = null) {
    init {
        require(name.isNotBlank()) { "Genre name must not be blank" }
        require((source == null) == (genreId == null)) { "Genre source and ID must both be present or absent" }
        require(genreId == null || genreId > 0) { "Genre ID must be positive" }
    }
}

enum class ProductionStatus {
    RUMORED,
    PLANNED,
    IN_PRODUCTION,
    POST_PRODUCTION,
    RELEASED,
    RETURNING_SERIES,
    ENDED,
    CANCELED,
    PILOT,
    UNKNOWN
}

data class MediaDetails(
    val externalRef: ExternalMediaRef,
    val mediaType: MediaType,
    val title: String,
    val originalTitle: String? = null,
    val overview: String? = null,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val releaseDate: LocalDate? = null,
    val productionStatus: ProductionStatus = ProductionStatus.UNKNOWN,
    val originalLanguage: String? = null,
    val runtime: Duration? = null,
    val episodeRuntime: Duration? = null,
    val numberOfSeasons: Int? = null,
    val numberOfEpisodes: Int? = null,
    val genres: List<Genre> = emptyList()
) {
    init {
        require(title.isNotBlank()) { "Media title must not be blank" }
        require(runtime == null || (!runtime.isNegative && !runtime.isZero)) {
            "Movie runtime must be positive"
        }
        require(episodeRuntime == null || (!episodeRuntime.isNegative && !episodeRuntime.isZero)) {
            "Episode runtime must be positive"
        }
        require(numberOfSeasons == null || numberOfSeasons >= 0) { "Season count must not be negative" }
        require(numberOfEpisodes == null || numberOfEpisodes >= 0) { "Episode count must not be negative" }
    }
}
