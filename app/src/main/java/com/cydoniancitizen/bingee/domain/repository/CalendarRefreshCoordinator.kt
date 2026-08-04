package com.cydoniancitizen.bingee.domain.repository

import com.cydoniancitizen.bingee.core.model.CalendarRefreshSummary

interface CalendarRefreshCoordinator {
    suspend fun refresh(): CalendarRefreshSummary
}
