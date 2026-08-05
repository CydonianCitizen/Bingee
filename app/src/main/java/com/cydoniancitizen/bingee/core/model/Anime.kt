package com.cydoniancitizen.bingee.core.model

import java.time.Instant
import java.time.LocalDate

enum class AnimeFormat { TV, MOVIE, OVA, ONA, SPECIAL, MUSIC, CM, PV, TV_SPECIAL, UNKNOWN }

enum class AnimeStatus { AIRING, FINISHED, UPCOMING, UNKNOWN }

enum class AnimeCompletionOrigin { INFERRED, EXPLICIT }

data class AnimeRelation(
    val relation: String,
    val animeRef: ExternalMediaRef,
    val title: String,
    val format: AnimeFormat = AnimeFormat.UNKNOWN
) {
    init {
        require(animeRef.source == MediaSource.JIKAN)
        require(title.isNotBlank())
    }
}

data class AnimeDetails(
    val externalRef: ExternalMediaRef,
    val title: String,
    val englishTitle: String? = null,
    val japaneseTitle: String? = null,
    val synopsis: String? = null,
    val posterUrl: String? = null,
    val format: AnimeFormat = AnimeFormat.UNKNOWN,
    val status: AnimeStatus = AnimeStatus.UNKNOWN,
    val episodeCount: Int? = null,
    val duration: String? = null,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val season: String? = null,
    val year: Int? = null,
    val providerScore: Double? = null,
    val relations: List<AnimeRelation> = emptyList()
) {
    init {
        require(externalRef.source == MediaSource.JIKAN)
        require(externalRef.externalId.toLongOrNull()?.let { it > 0 } == true)
        require(title.isNotBlank())
        require(episodeCount == null || episodeCount >= 0)
        require(providerScore == null || providerScore in 0.0..10.0)
    }
}

data class CachedAnimeDetails(val details: AnimeDetails, val fetchedAt: Instant, val freshness: CacheFreshness)

enum class AnimeProgressState { NOT_STARTED, IN_PROGRESS, COMPLETED }

data class AnimeWatchProgress(
    val watchedEpisodes: Int,
    val completedAt: Instant?,
    val completionOrigin: AnimeCompletionOrigin?,
    val updatedAt: Instant
) {
    init {
        require(watchedEpisodes in 0..MAX_WATCHED_EPISODES)
        require((completedAt == null) == (completionOrigin == null))
    }

    fun state(totalEpisodes: Int?): AnimeProgressState = when {
        completedAt != null -> AnimeProgressState.COMPLETED
        totalEpisodes != null && totalEpisodes > 0 && watchedEpisodes >= totalEpisodes -> AnimeProgressState.COMPLETED
        watchedEpisodes > 0 -> AnimeProgressState.IN_PROGRESS
        else -> AnimeProgressState.NOT_STARTED
    }

    companion object {
        const val MAX_WATCHED_EPISODES = 100_000
    }
}
