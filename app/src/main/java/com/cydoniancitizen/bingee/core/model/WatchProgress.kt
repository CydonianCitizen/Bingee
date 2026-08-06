package com.cydoniancitizen.bingee.core.model

import java.time.Instant
import java.time.LocalDate

sealed interface EpisodeWatchState {
    data object Unwatched : EpisodeWatchState

    data class Watched(val watchedAt: Instant) : EpisodeWatchState

    data object Unavailable : EpisodeWatchState
}

data class TrackedEpisode(val episode: Episode, val watchState: EpisodeWatchState)

data class SeasonProgress(val watchedEpisodes: Int, val trackableEpisodes: Int, val isComplete: Boolean) {
    init {
        require(watchedEpisodes >= 0) { "Watched episode count must not be negative" }
        require(trackableEpisodes >= 0) { "Trackable episode count must not be negative" }
        require(watchedEpisodes <= trackableEpisodes) { "Watched episodes cannot exceed trackable episodes" }
        require(isComplete == (trackableEpisodes > 0 && watchedEpisodes == trackableEpisodes)) {
            "Season completion must be derived from episode counts"
        }
    }

    val fraction: Float get() = if (trackableEpisodes == 0) 0f else watchedEpisodes.toFloat() / trackableEpisodes

    companion object {
        val EMPTY = SeasonProgress(0, 0, false)
    }
}

data class SeriesProgress(
    val watchedEpisodes: Int,
    val trackableEpisodes: Int,
    val completedSeasons: Int,
    val trackableSeasons: Int,
    val isComplete: Boolean,
    val watchedDate: LocalDate? = null
) {
    init {
        require(watchedEpisodes in 0..trackableEpisodes) { "Invalid series episode counts" }
        require(completedSeasons in 0..trackableSeasons) { "Invalid series season counts" }
        require(isComplete == (trackableEpisodes > 0 && watchedEpisodes == trackableEpisodes)) {
            "Series completion must be derived from regular episode counts"
        }
    }

    val fraction: Float get() = if (trackableEpisodes == 0) 0f else watchedEpisodes.toFloat() / trackableEpisodes

    companion object {
        val EMPTY = SeriesProgress(0, 0, 0, 0, false, null)
    }
}

sealed interface MovieWatchState {
    data object Unwatched : MovieWatchState

    data class Watched(val watchedAt: Instant, val watchedDate: LocalDate? = null) : MovieWatchState
}

sealed interface LibraryProgress {
    data object Unavailable : LibraryProgress

    data class Movie(val state: MovieWatchState) : LibraryProgress

    data class Series(val progress: SeriesProgress) : LibraryProgress

    data class Anime(val watchedEpisodes: Int, val totalEpisodes: Int?, val completed: Boolean) : LibraryProgress {
        init {
            require(watchedEpisodes >= 0)
        }
    }
}

fun deriveEpisodeWatchState(episode: Episode, watchedAt: Instant?, today: LocalDate): EpisodeWatchState = when {
    episode.airDate?.isAfter(today) == true -> EpisodeWatchState.Unavailable
    watchedAt != null -> EpisodeWatchState.Watched(watchedAt)
    else -> EpisodeWatchState.Unwatched
}

fun deriveSeasonProgress(episodes: List<TrackedEpisode>): SeasonProgress {
    val trackable = episodes.filterNot { it.watchState == EpisodeWatchState.Unavailable }
    val watched = trackable.count { it.watchState is EpisodeWatchState.Watched }
    return SeasonProgress(watched, trackable.size, trackable.isNotEmpty() && watched == trackable.size)
}

fun deriveSeriesProgress(seasons: List<CachedSeason>): SeriesProgress {
    val regular = seasons.filter { it.season.seasonNumber > 0 && it.progress.trackableEpisodes > 0 }
    val watched = regular.sumOf { it.progress.watchedEpisodes }
    val total = regular.sumOf { it.progress.trackableEpisodes }
    val complete = regular.count { it.progress.isComplete }
    return SeriesProgress(
        watchedEpisodes = watched,
        trackableEpisodes = total,
        completedSeasons = complete,
        trackableSeasons = regular.size,
        isComplete = total > 0 && watched == total
    )
}
