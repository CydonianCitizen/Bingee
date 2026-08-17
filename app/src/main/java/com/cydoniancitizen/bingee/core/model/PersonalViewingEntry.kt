package com.cydoniancitizen.bingee.core.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class PersonalViewingEntry(
    val mediaRef: ExternalMediaRef,
    val mediaType: MediaType,
    val title: String,
    val originalTitle: String? = null,
    val posterUrl: String? = null,
    val addedAt: Instant,
    val inLibrary: Boolean,
    val isFavorite: Boolean,
    val isAbandoned: Boolean = false,
    val personalRating: PersonalRating? = null,
    val movieWatchedAt: Instant? = null,
    val watchedRegularEpisodes: Int = 0,
    val seriesCompletedAt: Instant? = null,
    val watchedDate: LocalDate? = null,
    val movieRuntimeMinutes: Int? = null,
    val watchedRegularRuntimeMinutes: Long = 0L,
    val watchedRegularEpisodesWithoutRuntime: Int = 0,
    val seriesIsCurrentlyComplete: Boolean? = null,
    val genres: List<Genre> = emptyList()
) {
    init {
        require(title.isNotBlank()) { "Viewing-history title must not be blank" }
        require(watchedRegularEpisodes >= 0) { "Watched episode count must not be negative" }
        require(movieRuntimeMinutes == null || movieRuntimeMinutes > 0) {
            "Movie runtime must be positive"
        }
        require(watchedRegularRuntimeMinutes >= 0) {
            "Watched episode runtime must not be negative"
        }
        require(watchedRegularEpisodesWithoutRuntime >= 0) {
            "Missing episode runtime count must not be negative"
        }
    }

    val completionTimestamp: Instant?
        get() = when (mediaType) {
            MediaType.MOVIE -> movieWatchedAt
            MediaType.SERIES -> seriesCompletedAt
        }

    val isCompletedTitle: Boolean
        get() = when (mediaType) {
            MediaType.MOVIE -> movieWatchedAt != null
            MediaType.SERIES -> !isAbandoned && if (seriesIsCurrentlyComplete != null) {
                seriesIsCurrentlyComplete
            } else {
                seriesCompletedAt != null
            }
        }

    val isViewingTasteEligible: Boolean
        get() = when (mediaType) {
            MediaType.MOVIE -> movieWatchedAt != null
            MediaType.SERIES -> watchedRegularEpisodes > 0
        }

    fun displayWatchedDate(zoneId: ZoneId): LocalDate? =
        watchedDate ?: completionTimestamp?.atZone(zoneId)?.toLocalDate()
}
