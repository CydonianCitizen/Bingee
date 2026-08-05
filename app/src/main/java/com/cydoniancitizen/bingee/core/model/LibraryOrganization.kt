package com.cydoniancitizen.bingee.core.model

import java.util.Locale

fun normalizeLibrarySearch(query: String): String = query.trim().lowercase(Locale.ROOT)

fun LibraryEntry.matchesLibrarySearch(query: String): Boolean {
    val normalized = normalizeLibrarySearch(query)
    if (normalized.isEmpty()) return true
    return title.lowercase(Locale.ROOT).contains(normalized) ||
        originalTitle?.lowercase(Locale.ROOT)?.contains(normalized) == true
}

fun organizeLibraryEntries(entries: List<LibraryEntry>, query: LibraryQuery): List<LibraryEntry> {
    val locallyMatched = entries.filter {
        (query.mediaFilter.mediaType == null || it.mediaType == query.mediaFilter.mediaType) &&
            it.matchesLibrarySearch(query.searchQuery)
    }
    return applyLibraryStateAndSort(locallyMatched, query)
}

fun applyLibraryStateAndSort(entries: List<LibraryEntry>, query: LibraryQuery): List<LibraryEntry> = entries
    .filter { it.matchesStateFilter(query.stateFilter) }
    .sortedWith(libraryComparator(query.sort))

private fun LibraryEntry.matchesStateFilter(filter: LibraryStateFilter): Boolean = when (filter) {
    LibraryStateFilter.ALL -> true
    LibraryStateFilter.NOT_STARTED -> libraryState == LibraryState.NOT_STARTED
    LibraryStateFilter.IN_PROGRESS -> libraryState == LibraryState.IN_PROGRESS
    LibraryStateFilter.COMPLETED -> libraryState == LibraryState.COMPLETED
    LibraryStateFilter.PROGRESS_UNAVAILABLE -> libraryState == LibraryState.PROGRESS_UNAVAILABLE
}

private fun libraryComparator(sort: LibrarySort): Comparator<LibraryEntry> {
    val identity = compareBy<LibraryEntry>({ it.mediaRef.source.name }, { it.mediaRef.externalId })
    val title = compareBy<LibraryEntry>(
        { it.title.lowercase(Locale.ROOT) },
        { it.originalTitle?.lowercase(Locale.ROOT).orEmpty() }
    ).then(identity)
    return when (sort) {
        LibrarySort.RECENTLY_ADDED -> compareByDescending<LibraryEntry> { it.addedAt }.then(title)
        LibrarySort.TITLE -> title
        LibrarySort.PROGRESS -> compareBy<LibraryEntry> { it.libraryState.progressOrder }
            .thenByDescending { it.progress.completionRatio }
            .then(title)
        LibrarySort.PERSONAL_RATING -> compareBy<LibraryEntry> { it.personalRating == null }
            .thenByDescending { it.personalRating?.value ?: 0 }
            .then(title)
    }
}

private val LibraryState.progressOrder: Int get() = when (this) {
    LibraryState.IN_PROGRESS -> 0
    LibraryState.NOT_STARTED -> 1
    LibraryState.COMPLETED -> 2
    LibraryState.PROGRESS_UNAVAILABLE -> 3
}

private val LibraryProgress.completionRatio: Float get() = when (this) {
    LibraryProgress.Unavailable -> -1f
    is LibraryProgress.Movie -> if (state is MovieWatchState.Watched) 1f else 0f
    is LibraryProgress.Series -> progress.fraction
    is LibraryProgress.Anime -> when {
        completed -> 1f
        totalEpisodes == null || totalEpisodes <= 0 -> watchedEpisodes.toFloat()
        else -> watchedEpisodes.toFloat() / totalEpisodes
    }
}
