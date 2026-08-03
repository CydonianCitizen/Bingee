package com.cydoniancitizen.bingee.domain.repository

import com.cydoniancitizen.bingee.core.model.CachedMediaDetails
import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.core.result.AppResult
import kotlinx.coroutines.flow.Flow

interface MediaDetailsRepository {
    fun observeDetails(reference: ExternalMediaRef): Flow<AppResult<CachedMediaDetails?>>

    suspend fun refreshDetails(
        reference: ExternalMediaRef,
        mediaType: MediaType,
        force: Boolean = false
    ): AppResult<Unit>
}
