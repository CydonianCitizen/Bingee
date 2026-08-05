package com.cydoniancitizen.bingee.core.model

enum class MediaSource {
    TMDB,
    JIKAN
}

enum class MediaType {
    MOVIE,
    SERIES,
    ANIME
}

data class ExternalMediaRef(val source: MediaSource, val externalId: String) {
    init {
        require(externalId.isNotBlank()) { "External media ID must not be blank" }
    }
}
