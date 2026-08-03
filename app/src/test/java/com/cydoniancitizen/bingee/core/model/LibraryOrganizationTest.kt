package com.cydoniancitizen.bingee.core.model

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryOrganizationTest {
    @Test
    fun derivesMovieAndSeriesStatesWithoutPersistedFlags() {
        assertEquals(LibraryState.NOT_STARTED, movie("1").libraryState)
        assertEquals(
            LibraryState.COMPLETED,
            movie("2", LibraryProgress.Movie(MovieWatchState.Watched(Instant.EPOCH))).libraryState
        )
        assertEquals(LibraryState.PROGRESS_UNAVAILABLE, series("3", LibraryProgress.Unavailable).libraryState)
        assertEquals(LibraryState.NOT_STARTED, series("4", progress(0, 2)).libraryState)
        assertEquals(LibraryState.IN_PROGRESS, series("5", progress(1, 2)).libraryState)
        assertEquals(LibraryState.COMPLETED, series("6", progress(2, 2)).libraryState)
    }

    @Test
    fun localSearchTrimsCaseFoldsMatchesOriginalAndTreatsWildcardsAsText() {
        val entries = listOf(
            movie("1", title = "Amélie", originalTitle = "Le Fabuleux Destin"),
            movie("2", title = "100% Real_Story"),
            movie("3", title = "Director's Cut", overview = "needle only in overview")
        )
        assertEquals(listOf("1"), organizeLibraryEntries(entries, LibraryQuery(searchQuery = "  AMÉLIE ")).ids())
        assertEquals(listOf("1"), organizeLibraryEntries(entries, LibraryQuery(searchQuery = "fabuleux destin")).ids())
        assertEquals(listOf("2"), organizeLibraryEntries(entries, LibraryQuery(searchQuery = "% real_")).ids())
        assertEquals(listOf("3"), organizeLibraryEntries(entries, LibraryQuery(searchQuery = "director's")).ids())
        assertEquals(emptyList<String>(), organizeLibraryEntries(entries, LibraryQuery(searchQuery = "needle")).ids())
        assertEquals(3, organizeLibraryEntries(entries, LibraryQuery(searchQuery = "   ")).size)
    }

    @Test
    fun combinedFiltersAndAllSortModesAreDeterministic() {
        val sameTime = Instant.parse("2026-08-03T10:00:00Z")
        val entries = listOf(
            movie("b", title = "Beta", addedAt = sameTime, rating = 10),
            movie("a", title = "Alpha", addedAt = sameTime, rating = 10),
            series("c", progress(1, 2), title = "Current", rating = 7),
            series("d", LibraryProgress.Unavailable, title = "Unknown")
        )
        assertEquals(
            listOf("a", "b", "c", "d"),
            organizeLibraryEntries(entries, LibraryQuery(sort = LibrarySort.TITLE)).ids()
        )
        assertEquals(
            listOf("a", "b", "c", "d"),
            organizeLibraryEntries(entries, LibraryQuery(sort = LibrarySort.PERSONAL_RATING)).ids()
        )
        assertEquals(
            listOf("c", "a", "b", "d"),
            organizeLibraryEntries(entries, LibraryQuery(sort = LibrarySort.PROGRESS)).ids()
        )
        assertEquals(
            listOf("a", "b", "c", "d"),
            organizeLibraryEntries(entries, LibraryQuery(sort = LibrarySort.RECENTLY_ADDED)).ids()
        )
        assertEquals(
            listOf("c"),
            organizeLibraryEntries(
                entries,
                LibraryQuery(
                    searchQuery = "cur",
                    mediaFilter = LibraryMediaFilter.TV_SERIES,
                    stateFilter = LibraryStateFilter.IN_PROGRESS
                )
            ).ids()
        )
    }

    private fun movie(
        id: String,
        progress: LibraryProgress = LibraryProgress.Movie(MovieWatchState.Unwatched),
        title: String = "Movie $id",
        originalTitle: String? = null,
        overview: String? = null,
        addedAt: Instant = Instant.parse("2026-08-03T10:00:00Z"),
        rating: Int? = null
    ) = entry(id, MediaType.MOVIE, progress, title, originalTitle, overview, addedAt, rating)

    private fun series(id: String, progress: LibraryProgress, title: String = "Series $id", rating: Int? = null) =
        entry(id, MediaType.SERIES, progress, title, null, null, Instant.parse("2026-08-02T10:00:00Z"), rating)

    private fun entry(
        id: String,
        type: MediaType,
        progress: LibraryProgress,
        title: String,
        originalTitle: String?,
        overview: String?,
        addedAt: Instant,
        rating: Int?
    ) = LibraryEntry(
        mediaRef = ExternalMediaRef(MediaSource.TMDB, id),
        mediaType = type,
        title = title,
        originalTitle = originalTitle,
        overview = overview,
        addedAt = addedAt,
        progress = progress,
        personalRating = rating?.let(::PersonalRating)
    )

    private fun progress(watched: Int, total: Int) = LibraryProgress.Series(
        SeriesProgress(watched, total, if (watched == total) 1 else 0, 1, watched == total)
    )

    private fun List<LibraryEntry>.ids() = map { it.mediaRef.externalId }
}
