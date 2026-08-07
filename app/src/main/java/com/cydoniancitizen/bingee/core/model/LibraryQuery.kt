package com.cydoniancitizen.bingee.core.model

enum class LibraryMediaFilter(val mediaType: MediaType?) {
    ALL(null),
    MOVIES(MediaType.MOVIE),
    TV_SERIES(MediaType.SERIES)
}

enum class LibraryStateFilter {
    ALL,
    NOT_STARTED,
    IN_PROGRESS,
    COMPLETED,
    PROGRESS_UNAVAILABLE
}

enum class LibrarySort {
    RECENTLY_ADDED,
    TITLE,
    PROGRESS,
    PERSONAL_RATING
}

data class LibraryQuery(
    val searchQuery: String = "",
    val mediaFilter: LibraryMediaFilter = LibraryMediaFilter.ALL,
    val stateFilter: LibraryStateFilter = LibraryStateFilter.ALL,
    val sort: LibrarySort = LibrarySort.RECENTLY_ADDED
)

enum class LibraryState {
    NOT_STARTED,
    IN_PROGRESS,
    COMPLETED,
    PROGRESS_UNAVAILABLE
}

fun LibraryProgress.deriveLibraryState(): LibraryState = when (this) {
    LibraryProgress.Unavailable -> LibraryState.PROGRESS_UNAVAILABLE
    is LibraryProgress.Movie ->
        if (state is MovieWatchState.Watched) LibraryState.COMPLETED else LibraryState.NOT_STARTED
    is LibraryProgress.Series -> when {
        progress.trackableEpisodes == 0 -> LibraryState.PROGRESS_UNAVAILABLE
        progress.watchedEpisodes == 0 -> LibraryState.NOT_STARTED
        progress.isComplete -> LibraryState.COMPLETED
        else -> LibraryState.IN_PROGRESS
    }
}
