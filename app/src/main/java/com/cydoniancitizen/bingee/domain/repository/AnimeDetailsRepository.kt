package com.cydoniancitizen.bingee.domain.repository

import com.cydoniancitizen.bingee.core.model.CachedAnimeDetails
import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.result.AppResult
import kotlinx.coroutines.flow.Flow

interface AnimeDetailsRepository {
    fun observeDetails(reference: ExternalMediaRef): Flow<AppResult<CachedAnimeDetails?>>

    suspend fun refreshDetails(reference: ExternalMediaRef, force: Boolean = false): AppResult<Unit>
}
