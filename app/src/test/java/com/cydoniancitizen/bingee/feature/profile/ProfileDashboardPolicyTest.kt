package com.cydoniancitizen.bingee.feature.profile

import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.LibraryEntry
import com.cydoniancitizen.bingee.core.model.LibraryProgress
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.core.model.SeriesProgress
import com.cydoniancitizen.bingee.domain.model.GenreStatistic
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileDashboardPolicyTest {
    @Test
    fun podiumPresentationPlacesSecondFirstThird() {
        val ranked = listOf(
            GenreStatistic(MediaSource.TMDB, 18, "Drama", 10),
            GenreStatistic(MediaSource.TMDB, 35, "Comedy", 8),
            GenreStatistic(MediaSource.TMDB, 53, "Thriller", 6)
        )

        assertEquals(listOf(35L, 18L, 53L), podiumPresentationOrder(ranked).map { it.genreId })
    }

    @Test
    fun watchingExcludesNotStartedCaughtUpAbandonedMoviesAndSpecialsOnly() {
        val entries = listOf(
            series("watching", 6, 10),
            series("not-started", 0, 10),
            series("caught-up", 10, 10, complete = true),
            series("abandoned", 2, 10, abandoned = true),
            series("specials-only", 0, 0),
            movie("movie")
        )

        assertEquals(listOf("watching"), selectWatchingPreview(entries).map { it.mediaRef.externalId })
    }

    @Test
    fun newlyAvailableEpisodeMakesCaughtUpSeriesEligibleAgain() {
        assertTrue(selectWatchingPreview(listOf(series("series", 10, 11))).single().progress.fraction > 0.9f)
        assertTrue(selectWatchingPreview(listOf(series("series", 10, 10, complete = true))).isEmpty())
    }

    @Test
    fun watchingOrdersByLastViewingAndCapsAtSeven() {
        val entries = (0..8).map { index ->
            series(
                id = "series-$index",
                watched = 1,
                total = 2,
                lastWatchedAt = Instant.ofEpochSecond(index.toLong())
            )
        }

        val selected = selectWatchingPreview(entries)

        assertEquals(7, selected.size)
        assertEquals("series-8", selected.first().mediaRef.externalId)
        assertEquals("series-2", selected.last().mediaRef.externalId)
    }

    @Test
    fun collectionCountsKeepFavoritesIndependentFromLibrary() {
        val favoriteRemoved = movie("favorite-removed", inLibrary = false, favorite = true)
        val abandoned = series("abandoned", 2, 3, abandoned = true)
        val counts = countCollections(listOf(favoriteRemoved, abandoned))

        assertEquals(1, counts.favorites)
        assertEquals(1, counts.abandoned)
        assertEquals(0, counts.watchLater)
        assertEquals(0, counts.watched)
    }

    @Test
    fun favoritesUseFavoriteTimestampAndDoNotUseLibraryAddedAt() {
        val olderLibraryEntry = movie(
            "older",
            addedAt = Instant.ofEpochSecond(100),
            favoriteAddedAt = Instant.ofEpochSecond(1),
            favorite = true
        )
        val newerFavorite = movie(
            "newer",
            addedAt = Instant.EPOCH,
            favoriteAddedAt = Instant.ofEpochSecond(2),
            favorite = true
        )

        assertEquals(
            listOf("newer", "older"),
            selectFavoritePreview(listOf(olderLibraryEntry, newerFavorite)).map { it.mediaRef.externalId }
        )
    }

    private fun series(
        id: String,
        watched: Int,
        total: Int,
        complete: Boolean = watched == total && total > 0,
        abandoned: Boolean = false,
        lastWatchedAt: Instant? = null
    ) = LibraryEntry(
        mediaRef = ExternalMediaRef(MediaSource.TMDB, id),
        mediaType = MediaType.SERIES,
        title = id,
        addedAt = Instant.EPOCH,
        progress = LibraryProgress.Series(
            SeriesProgress(watched, total, 0, 1, complete, lastWatchedAt = lastWatchedAt)
        ),
        isAbandoned = abandoned
    )

    private fun movie(
        id: String,
        inLibrary: Boolean = true,
        favorite: Boolean = false,
        addedAt: Instant = Instant.EPOCH,
        favoriteAddedAt: Instant? = null
    ) = LibraryEntry(
        mediaRef = ExternalMediaRef(MediaSource.TMDB, id),
        mediaType = MediaType.MOVIE,
        title = id,
        addedAt = addedAt,
        isFavorite = favorite,
        favoriteAddedAt = favoriteAddedAt,
        inLibrary = inLibrary
    )
}
