package com.cydoniancitizen.bingee.domain.notification

import com.cydoniancitizen.bingee.core.model.NotificationCapabilityStatus

interface ReleaseNotificationCapability {
    fun ensureChannel()

    fun status(): NotificationCapabilityStatus

    fun openSystemSettings()
}
