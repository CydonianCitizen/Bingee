package com.cydoniancitizen.bingee.domain.repository

import com.cydoniancitizen.bingee.core.model.ReleaseEvent
import com.cydoniancitizen.bingee.core.result.AppResult
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

interface ReleaseCalendarRepository {
    fun observeEvents(fromDate: LocalDate): Flow<AppResult<List<ReleaseEvent>>>

    fun observeLastSuccessfulRefresh(): Flow<AppResult<Instant?>>

    suspend fun getEvents(fromDate: LocalDate, throughDate: LocalDate, limit: Int): AppResult<List<ReleaseEvent>>

    suspend fun backfill(): AppResult<Unit>

    suspend fun markRefreshSuccessful(at: Instant): AppResult<Unit>
}
