package com.cydoniancitizen.bingee.domain.repository

import com.cydoniancitizen.bingee.core.model.CachedSeason
import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.result.AppResult
import kotlinx.coroutines.flow.Flow

interface SeriesRepository {
    fun observeSeasons(seriesRef: ExternalMediaRef): Flow<AppResult<List<CachedSeason>>>

    suspend fun refreshSeason(seriesRef: ExternalMediaRef, seasonNumber: Int, force: Boolean = false): AppResult<Unit>
}
