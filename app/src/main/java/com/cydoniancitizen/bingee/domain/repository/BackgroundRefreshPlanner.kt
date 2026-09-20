package com.cydoniancitizen.bingee.domain.repository

import com.cydoniancitizen.bingee.core.model.BackgroundRefreshTarget
import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.result.AppResult

interface BackgroundRefreshPlanner {
    suspend fun plan(limit: Int): AppResult<List<BackgroundRefreshTarget>>

    /** Persist the attempt cursor before networking, including attempts that subsequently fail. */
    suspend fun claimSeasons(
        mediaRef: ExternalMediaRef,
        seasonNumbers: List<Int>,
        prioritySeasonNumber: Int? = null
    ): AppResult<List<Int>>

    companion object {
        const val TITLE_LIMIT = 20
        const val SEASON_LIMIT = 2
    }
}
