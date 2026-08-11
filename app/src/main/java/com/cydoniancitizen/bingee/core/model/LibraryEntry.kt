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
    val watchedDate: LocalDate? = null,
    val inLibrary: Boolean = true
) {
    init {
        require(title.isNotBlank()) { "Library title must not be blank" }
    }

    val libraryState: LibraryState get() = progress.deriveLibraryState()
}

/** Any watched TV episode counts as watched until granular serial states are introduced. */
internal enum class PersonalLibraryStatus { UNWATCHED, WATCHED }

internal fun LibraryEntry.personalLibraryStatus(): PersonalLibraryStatus = when (val p = progress) {
    is LibraryProgress.Movie ->
        if (p.state is MovieWatchState.Watched) PersonalLibraryStatus.WATCHED else PersonalLibraryStatus.UNWATCHED
    is LibraryProgress.Series ->
        if (p.progress.watchedEpisodes > 0 || p.progress.isComplete) {
            PersonalLibraryStatus.WATCHED
        } else {
            PersonalLibraryStatus.UNWATCHED
        }
    LibraryProgress.Unavailable ->
        if (libraryState == LibraryState.COMPLETED || libraryState == LibraryState.IN_PROGRESS) {
            PersonalLibraryStatus.WATCHED
        } else {
            PersonalLibraryStatus.UNWATCHED
        }
}

fun LibraryEntry.isWatched(): Boolean = personalLibraryStatus() == PersonalLibraryStatus.WATCHED
