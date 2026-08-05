package com.cydoniancitizen.bingee.domain.repository

import com.cydoniancitizen.bingee.core.model.AnimeWatchProgress
import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.result.AppResult
import kotlinx.coroutines.flow.Flow

interface AnimeProgressRepository {
    fun observe(reference: ExternalMediaRef): Flow<AppResult<AnimeWatchProgress?>>
    suspend fun increment(reference: ExternalMediaRef): AppResult<Unit>
    suspend fun decrement(reference: ExternalMediaRef): AppResult<Unit>
    suspend fun setCount(reference: ExternalMediaRef, count: Int): AppResult<Unit>
    suspend fun markComplete(reference: ExternalMediaRef): AppResult<Unit>
    suspend fun markIncomplete(reference: ExternalMediaRef): AppResult<Unit>
    suspend fun reset(reference: ExternalMediaRef): AppResult<Unit>
}
