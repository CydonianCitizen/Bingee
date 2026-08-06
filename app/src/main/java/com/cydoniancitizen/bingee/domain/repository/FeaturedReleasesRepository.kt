package com.cydoniancitizen.bingee.domain.repository

import com.cydoniancitizen.bingee.core.model.MediaSearchResult
import com.cydoniancitizen.bingee.core.result.AppResult

interface FeaturedReleasesRepository {
    suspend fun getFeaturedReleases(): AppResult<List<MediaSearchResult>>
}
