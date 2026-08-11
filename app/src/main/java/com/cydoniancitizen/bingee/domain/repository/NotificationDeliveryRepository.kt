package com.cydoniancitizen.bingee.domain.repository

import com.cydoniancitizen.bingee.core.model.NotificationDelivery
import com.cydoniancitizen.bingee.core.model.NotificationDeliveryIdentity
import com.cydoniancitizen.bingee.core.result.AppResult
import java.time.LocalDate

interface NotificationDeliveryRepository {
    suspend fun contains(identity: NotificationDeliveryIdentity): AppResult<Boolean>

    suspend fun findDelivered(
        identities: Set<NotificationDeliveryIdentity>
    ): AppResult<Set<NotificationDeliveryIdentity>>

    suspend fun record(delivery: NotificationDelivery): AppResult<Unit>

    suspend fun prune(eventDateBefore: LocalDate): AppResult<Int>
}
