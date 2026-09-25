package com.cydoniancitizen.bingee.core.model

enum class MediaSource {
    TMDB,
    IMDB
}

enum class MediaType {
    MOVIE,
    SERIES
}

data class ExternalMediaRef(val source: MediaSource, val externalId: String) {
    init {
        require(externalId.isNotBlank()) { "External media ID must not be blank" }
    }
}

fun ExternalMediaRef.toNavigableDetailsRef(): ExternalMediaRef? = takeIf { tmdbIdOrNull() != null }

fun ExternalMediaRef.tmdbIdOrNull(): Long? = takeIf { source == MediaSource.TMDB }
    ?.externalId
    ?.trim()
    ?.toLongOrNull()
    ?.takeIf { it > 0 }

fun Iterable<ExternalMediaRef>.resolveTmdbRef(): ExternalMediaRef? = asSequence()
    .filter { it.source == MediaSource.TMDB }
    .minByOrNull { it.externalId }
