package com.cydoniancitizen.bingee.domain.repository

import com.cydoniancitizen.bingee.core.model.NotificationDelivery
import com.cydoniancitizen.bingee.core.model.NotificationDeliveryIdentity
import com.cydoniancitizen.bingee.core.result.AppResult
import java.time.Instant
import java.time.LocalDate

sealed interface NotificationClaim {
    data class Acquired(val token: String) : NotificationClaim
    data object AlreadyDelivered : NotificationClaim
    data object InFlight : NotificationClaim
}

interface NotificationDeliveryRepository {
    suspend fun findDelivered(
        identities: Set<NotificationDeliveryIdentity>
    ): AppResult<Set<NotificationDeliveryIdentity>>

    suspend fun claim(
        identity: NotificationDeliveryIdentity,
        notificationId: Int,
        now: Instant
    ): AppResult<NotificationClaim>

    suspend fun complete(delivery: NotificationDelivery, token: String): AppResult<Boolean>

    suspend fun release(identity: NotificationDeliveryIdentity, token: String): AppResult<Unit>

    suspend fun prune(eventDateBefore: LocalDate): AppResult<Int>
}
