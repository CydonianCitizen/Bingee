package com.cydoniancitizen.bingee.domain.repository

import com.cydoniancitizen.bingee.core.model.CachedMediaDetails
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.core.result.AppResult
import kotlinx.coroutines.flow.Flow

interface MediaDetailsRepository {
    fun observeDetails(tmdbId: Long): Flow<AppResult<CachedMediaDetails?>>

    suspend fun refreshDetails(tmdbId: Long, mediaType: MediaType, force: Boolean = false): AppResult<Unit>
}
