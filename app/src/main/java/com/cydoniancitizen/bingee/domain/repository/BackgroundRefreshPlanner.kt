package com.cydoniancitizen.bingee.domain.repository

import com.cydoniancitizen.bingee.core.model.BackgroundRefreshTarget
import com.cydoniancitizen.bingee.core.result.AppResult

interface BackgroundRefreshPlanner {
    suspend fun plan(limit: Int): AppResult<List<BackgroundRefreshTarget>>
}
