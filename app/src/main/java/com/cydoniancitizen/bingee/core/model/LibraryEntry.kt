package com.cydoniancitizen.bingee.core.model

import java.time.Instant
import java.time.LocalDate

data class LibraryEntry(
    val mediaRef: ExternalMediaRef,
    val mediaType: MediaType,
    val title: String,
    val originalTitle: String? = null,
    val posterUrl: String? = null,
    val releaseDate: LocalDate? = null,
    val overview: String? = null,
    val addedAt: Instant,
    val progress: LibraryProgress = LibraryProgress.Unavailable,
    val personalRating: PersonalRating? = null,
    val isFavorite: Boolean = false,
    val favoriteAddedAt: Instant? = null,
    val watchedDate: LocalDate? = null,
    val isAbandoned: Boolean = false,
    val inLibrary: Boolean = true
) {
    init {
        require(title.isNotBlank()) { "Library title must not be blank" }
    }

    val libraryState: LibraryState get() = progress.deriveLibraryState()

    val serialState: SeriesTrackingState?
        get() = if (mediaType == MediaType.SERIES) {
            resolveSeriesTrackingState(
                inLibrary = inLibrary,
                progress = (progress as? LibraryProgress.Series)?.progress,
                isAbandoned = isAbandoned
            )
        } else {
            null
        }
}

fun LibraryEntry.isWatched(): Boolean = when (mediaType) {
    MediaType.MOVIE -> (progress as? LibraryProgress.Movie)?.state is MovieWatchState.Watched
    MediaType.SERIES -> serialState == SeriesTrackingState.WATCHED
}
