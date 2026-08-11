package com.cydoniancitizen.bingee.domain.policy

import com.cydoniancitizen.bingee.core.model.ContinueWatchingItem
import com.cydoniancitizen.bingee.core.model.EpisodePosition
import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.core.model.SeriesProgress
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContinueWatchingPolicyTest {
    @Test
    fun zeroWatchedEpisodesAreExcluded() {
        assertTrue(ContinueWatchingPolicy.select(listOf(item(watched = 0))).isEmpty())
    }

    @Test
    fun partiallyWatchedSeriesIsIncludedWithProgressAndNextEpisode() {
        val selected = ContinueWatchingPolicy.select(listOf(item(watched = 3, total = 8))).single()

        assertEquals(3, selected.progress.watchedEpisodes)
        assertEquals(8, selected.progress.trackableEpisodes)
        assertEquals(EpisodePosition(2, 5), selected.nextEpisode)
    }

    @Test
    fun completedSeriesIsExcluded() {
        assertTrue(ContinueWatchingPolicy.select(listOf(item(watched = 8, total = 8))).isEmpty())
    }

    @Test
    fun movieIsExcluded() {
        assertTrue(
            ContinueWatchingPolicy.select(listOf(item(mediaType = MediaType.MOVIE, watched = 3))).isEmpty()
        )
    }

    @Test
    fun tmdbAnimatedSeriesUsesSameSeriesPolicy() {
        val selected = ContinueWatchingPolicy.select(listOf(item(title = "Animated Series", watched = 1)))

        assertEquals("Animated Series", selected.single().title)
    }

    @Test
    fun orderingUsesLatestProgressThenStableTitleAndIdentity() {
        val latest = item(id = "latest", title = "Zed", updatedAt = Instant.parse("2026-08-03T12:00:00Z"))
        val tiedB = item(id = "b", title = "Beta", updatedAt = Instant.EPOCH)
        val tiedA = item(id = "a", title = "Alpha", updatedAt = Instant.EPOCH)

        assertEquals(
            listOf("latest", "a", "b"),
            ContinueWatchingPolicy.select(listOf(tiedB, latest, tiedA)).map { it.mediaRef.externalId }
        )
    }

    @Test
    fun unfollowedSeriesIsExcludedWithoutChangingFollowPolicy() {
        assertTrue(ContinueWatchingPolicy.select(listOf(item(inLibrary = false, watched = 1))).isEmpty())
    }

    private fun item(
        id: String = "series",
        title: String = "Series",
        mediaType: MediaType = MediaType.SERIES,
        watched: Int = 3,
        total: Int = 8,
        updatedAt: Instant? = Instant.EPOCH,
        inLibrary: Boolean = true
    ) = ContinueWatchingItem(
        mediaRef = ExternalMediaRef(MediaSource.TMDB, id),
        mediaType = mediaType,
        title = title,
        posterUrl = null,
        progress = SeriesProgress(watched, total, 0, 1, watched == total),
        nextEpisode = EpisodePosition(2, 5),
        updatedAt = updatedAt,
        inLibrary = inLibrary
    )
}
