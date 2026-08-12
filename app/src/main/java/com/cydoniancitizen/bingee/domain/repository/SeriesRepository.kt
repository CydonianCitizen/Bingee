package com.cydoniancitizen.bingee.domain.repository

import com.cydoniancitizen.bingee.core.model.CachedSeason
import com.cydoniancitizen.bingee.core.result.AppResult
import kotlinx.coroutines.flow.Flow

interface SeriesRepository {
    fun observeSeasons(tmdbId: Long): Flow<AppResult<List<CachedSeason>>>

    suspend fun refreshSeason(tmdbId: Long, seasonNumber: Int, force: Boolean = false): AppResult<Unit>
}
