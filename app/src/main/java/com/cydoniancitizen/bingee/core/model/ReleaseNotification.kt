package com.cydoniancitizen.bingee.core.model

import java.time.Instant
import java.time.LocalDate

enum class ReleaseNotificationLeadTime(val days: Int) {
    SAME_DAY(0),
    ONE_DAY(1),
    THREE_DAYS(3),
    SEVEN_DAYS(7)
}

data class ReleaseNotificationPreferences(
    val enabled: Boolean = false,
    val leadTime: ReleaseNotificationLeadTime = ReleaseNotificationLeadTime.ONE_DAY,
    val movieReleases: Boolean = true,
    val seasonPremieres: Boolean = true,
    val episodeAirings: Boolean = true
) {
    fun includes(type: ReleaseEventType): Boolean = when (type) {
        ReleaseEventType.MOVIE_RELEASE -> movieReleases
        ReleaseEventType.SEASON_PREMIERE -> seasonPremieres
        ReleaseEventType.EPISODE_AIRING -> episodeAirings
    }
}

data class NotificationDeliveryIdentity(
    val source: MediaSource,
    val subjectType: ReleaseSubjectType,
    val subjectExternalId: String,
    val eventType: ReleaseEventType,
    val eventDate: LocalDate,
    val leadDays: Int
) {
    init {
        require(subjectExternalId.isNotBlank())
        require(leadDays in setOf(0, 1, 3, 7))
    }
}

data class NotificationDelivery(
    val identity: NotificationDeliveryIdentity,
    val notificationId: Int,
    val deliveredAt: Instant
)

enum class NotificationCapabilityStatus {
    AVAILABLE,
    PERMISSION_DENIED,
    SYSTEM_BLOCKED,
    CHANNEL_BLOCKED
}

data class NotificationDispatchSummary(
    val candidates: Int = 0,
    val posted: Int = 0,
    val alreadyDelivered: Int = 0,
    val skippedByCategory: Int = 0,
    val failed: Int = 0,
    val capability: NotificationCapabilityStatus? = null,
    val transientFailure: Boolean = false
)
