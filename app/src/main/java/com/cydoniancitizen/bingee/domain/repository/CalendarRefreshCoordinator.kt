package com.cydoniancitizen.bingee.domain.repository

import com.cydoniancitizen.bingee.core.model.BackgroundRefreshTarget
import com.cydoniancitizen.bingee.core.model.CalendarRefreshSummary

interface CalendarRefreshCoordinator {
    suspend fun refresh(): CalendarRefreshSummary

    suspend fun refresh(targets: List<BackgroundRefreshTarget>): CalendarRefreshSummary
}
