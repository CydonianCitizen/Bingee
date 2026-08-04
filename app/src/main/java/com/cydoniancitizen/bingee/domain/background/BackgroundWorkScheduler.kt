package com.cydoniancitizen.bingee.domain.background

interface BackgroundWorkScheduler {
    fun ensureCalendarRefresh()

    fun reconcileNotificationWork(enabled: Boolean)

    fun enqueueImmediateNotificationEvaluation()
}
