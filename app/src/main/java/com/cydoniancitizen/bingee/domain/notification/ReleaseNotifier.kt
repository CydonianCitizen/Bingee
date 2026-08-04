package com.cydoniancitizen.bingee.domain.notification

import com.cydoniancitizen.bingee.core.model.ReleaseEvent
import com.cydoniancitizen.bingee.core.result.AppResult

data class ReleaseNotificationContent(val title: String, val body: String)

interface ReleaseNotificationContentMapper {
    fun map(event: ReleaseEvent, daysUntilEvent: Int): ReleaseNotificationContent
}

interface ReleaseNotifier {
    fun post(event: ReleaseEvent, notificationId: Int, content: ReleaseNotificationContent): AppResult<Unit>
}
