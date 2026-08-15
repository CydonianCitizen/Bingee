package com.cydoniancitizen.bingee.domain.policy

import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.LibraryEntry
import com.cydoniancitizen.bingee.core.model.LibraryProgress
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.core.model.SeriesProgress
import java.time.Instant
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeriesFollowPolicyTest {

    @Test
    fun tvSeriesInLibraryIsFollowed() {
        val entry = createEntry(
            mediaType = MediaType.SERIES,
            inLibrary = true
        )
        assertTrue(SeriesFollowPolicy.isFollowed(entry))
        assertTrue(SeriesFollowPolicy.isFollowedSeries(MediaType.SERIES, inLibrary = true))
    }

    @Test
    fun movieInLibraryIsNotFollowed() {
        val entry = createEntry(
            mediaType = MediaType.MOVIE,
            inLibrary = true
        )
        assertFalse(SeriesFollowPolicy.isFollowed(entry))
        assertFalse(SeriesFollowPolicy.isFollowedSeries(MediaType.MOVIE, inLibrary = true))
    }

    @Test
    fun tvSeriesNotInLibraryIsNotFollowed() {
        val entry = createEntry(
            mediaType = MediaType.SERIES,
            inLibrary = false
        )
        assertFalse(SeriesFollowPolicy.isFollowed(entry))
        assertFalse(SeriesFollowPolicy.isFollowedSeries(MediaType.SERIES, inLibrary = false))
    }

    @Test
    fun watchLaterSeriesIsFollowed() {
        val entry = createEntry(
            mediaType = MediaType.SERIES,
            inLibrary = true,
            progress = LibraryProgress.Series(
                SeriesProgress(
                    watchedEpisodes = 0,
                    trackableEpisodes = 10,
                    completedSeasons = 0,
                    trackableSeasons = 1,
                    isComplete = false
                )
            )
        )
        assertTrue(SeriesFollowPolicy.isFollowed(entry))
    }

    @Test
    fun inProgressSeriesIsFollowed() {
        val entry = createEntry(
            mediaType = MediaType.SERIES,
            inLibrary = true,
            progress = LibraryProgress.Series(
                SeriesProgress(
                    watchedEpisodes = 5,
                    trackableEpisodes = 10,
                    completedSeasons = 0,
                    trackableSeasons = 1,
                    isComplete = false
                )
            )
        )
        assertTrue(SeriesFollowPolicy.isFollowed(entry))
    }

    @Test
    fun completedSeriesRemainsFollowed() {
        val entry = createEntry(
            mediaType = MediaType.SERIES,
            inLibrary = true,
            progress = LibraryProgress.Series(
                SeriesProgress(
                    watchedEpisodes = 10,
                    trackableEpisodes = 10,
                    completedSeasons = 1,
                    trackableSeasons = 1,
                    isComplete = true
                )
            )
        )
        assertTrue(SeriesFollowPolicy.isFollowed(entry))
    }

    @Test
    fun abandonedSeriesIsNotFollowed() {
        assertFalse(SeriesFollowPolicy.isFollowed(createEntry(MediaType.SERIES, true).copy(isAbandoned = true)))
        assertFalse(SeriesFollowPolicy.isFollowedSeries(MediaType.SERIES, true, isAbandoned = true))
    }

    private fun createEntry(
        mediaType: MediaType,
        inLibrary: Boolean,
        progress: LibraryProgress = LibraryProgress.Unavailable
    ): LibraryEntry = LibraryEntry(
        mediaRef = ExternalMediaRef(MediaSource.TMDB, "123"),
        mediaType = mediaType,
        title = "Test Title",
        addedAt = Instant.EPOCH,
        inLibrary = inLibrary,
        progress = progress
    )
}
