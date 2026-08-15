package com.cydoniancitizen.bingee.domain.policy

import com.cydoniancitizen.bingee.core.model.LibraryEntry
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.core.model.SeriesTrackingState

/**
 * Centralized policy for determining whether a media title is considered "followed" for notification updates.
 *
 * A serial title (TV Series, including Anime series returned by TMDB as TV Series) is considered followed
 * when canonical serial state is Watch Later, Watching, or Watched.
 */
object SeriesFollowPolicy {
    fun isFollowed(entry: LibraryEntry): Boolean = entry.mediaType == MediaType.SERIES &&
        entry.serialState != SeriesTrackingState.ABANDONED &&
        entry.serialState != null

    fun isFollowedSeries(mediaType: MediaType, inLibrary: Boolean, isAbandoned: Boolean = false): Boolean {
        if (mediaType != MediaType.SERIES || !inLibrary) return false
        return !isAbandoned
    }
}
