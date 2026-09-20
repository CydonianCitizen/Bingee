package com.cydoniancitizen.bingee.domain.repository

import com.cydoniancitizen.bingee.core.model.BackgroundRefreshTarget
import com.cydoniancitizen.bingee.core.model.CalendarRefreshSummary

interface CalendarRefreshCoordinator {
    suspend fun refresh(): CalendarRefreshSummary

    /** Bounded background batch; manual refresh above includes all relevant seasons. */
    suspend fun refresh(targets: List<BackgroundRefreshTarget>): CalendarRefreshSummary
}
