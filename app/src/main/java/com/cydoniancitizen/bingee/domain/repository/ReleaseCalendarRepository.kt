package com.cydoniancitizen.bingee.domain.repository

import com.cydoniancitizen.bingee.core.model.ReleaseEvent
import com.cydoniancitizen.bingee.core.result.AppResult
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface ReleaseCalendarRepository {
    fun observeEvents(fromDate: LocalDate): Flow<AppResult<List<ReleaseEvent>>>

    fun observeEvents(fromDate: LocalDate, throughDate: LocalDate): Flow<AppResult<List<ReleaseEvent>>> =
        observeEvents(fromDate).map { result ->
            when (result) {
                is AppResult.Success -> AppResult.Success(result.value.filter { !it.eventDate.isAfter(throughDate) })
                is AppResult.Failure -> result
            }
        }

    fun observeLastSuccessfulRefresh(): Flow<AppResult<Instant?>>

    suspend fun getEvents(fromDate: LocalDate, throughDate: LocalDate): AppResult<List<ReleaseEvent>>

    suspend fun backfill(): AppResult<Unit>

    suspend fun markRefreshSuccessful(at: Instant): AppResult<Unit>
}
