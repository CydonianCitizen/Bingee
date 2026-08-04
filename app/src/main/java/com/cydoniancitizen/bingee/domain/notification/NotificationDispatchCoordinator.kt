package com.cydoniancitizen.bingee.domain.notification

import com.cydoniancitizen.bingee.core.model.NotificationDispatchSummary

interface NotificationDispatchCoordinator {
    suspend fun dispatch(): NotificationDispatchSummary
}
