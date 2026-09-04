package com.cydoniancitizen.bingee.domain.repository

import com.cydoniancitizen.bingee.core.model.MediaSearchResult
import com.cydoniancitizen.bingee.core.result.AppResult

/**
 * Discovery content for Home, kept as two lists rather than one merged one. The two rows scroll
 * independently on screen, and a merged list could only ever be split back apart by media type.
 *
 * Either list can be empty on its own: the two requests are independent, and one failing is not a
 * reason to drop the other's results.
 */
data class FeaturedReleases(
    val movies: List<MediaSearchResult> = emptyList(),
    val series: List<MediaSearchResult> = emptyList()
)

interface FeaturedReleasesRepository {
    suspend fun getFeaturedReleases(): AppResult<FeaturedReleases>
}
