package com.cydoniancitizen.bingee.domain.policy

import com.cydoniancitizen.bingee.core.model.LibraryEntry
import com.cydoniancitizen.bingee.core.model.MediaType

/**
 * Centralized policy for determining whether a media title is considered "followed" for notification updates.
 *
 * A serial title (TV Series, including Anime series returned by TMDB as TV Series) is considered followed
 * when it belongs to the user's personal tracking activity (Watch Later, In-Progress, Watched).
 *
 * In future milestones, when an explicit `Abandoned` state is introduced, this policy will cleanly exclude
 * abandoned series without needing to modify Notification Center logic.
 */
object SeriesFollowPolicy {
    fun isFollowed(entry: LibraryEntry): Boolean {
        if (entry.mediaType != MediaType.SERIES) return false
        if (!entry.inLibrary) return false
        return true
    }

    fun isFollowedSeries(mediaType: MediaType, inLibrary: Boolean): Boolean {
        if (mediaType != MediaType.SERIES) return false
        if (!inLibrary) return false
        return true
    }
}
