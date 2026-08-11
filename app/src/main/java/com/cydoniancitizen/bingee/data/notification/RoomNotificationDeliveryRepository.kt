package com.cydoniancitizen.bingee.data.notification

import android.database.sqlite.SQLiteException
import com.cydoniancitizen.bingee.core.model.NotificationDelivery
import com.cydoniancitizen.bingee.core.model.NotificationDeliveryIdentity
import com.cydoniancitizen.bingee.core.result.AppError
import com.cydoniancitizen.bingee.core.result.AppResult
import com.cydoniancitizen.bingee.data.library.local.NotificationDeliveryDao
import com.cydoniancitizen.bingee.data.library.local.NotificationDeliveryEntity
import com.cydoniancitizen.bingee.domain.repository.NotificationDeliveryRepository
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

@Singleton
internal class RoomNotificationDeliveryRepository @Inject constructor(private val dao: NotificationDeliveryDao) :
    NotificationDeliveryRepository {
    override suspend fun contains(identity: NotificationDeliveryIdentity): AppResult<Boolean> = read {
        dao.contains(
            identity.source,
            identity.subjectType,
            identity.subjectExternalId,
            identity.eventType,
            identity.eventDate,
            identity.leadDays
        )
    }

    override suspend fun findDelivered(
        identities: Set<NotificationDeliveryIdentity>
    ): AppResult<Set<NotificationDeliveryIdentity>> {
        if (identities.isEmpty()) return AppResult.Success(emptySet())
        return read {
            identities.groupBy(NotificationDeliveryIdentity::leadDays)
                .flatMapTo(mutableSetOf()) { (leadDays, candidates) ->
                    val rows = dao.findBetween(
                        fromDate = candidates.minOf(NotificationDeliveryIdentity::eventDate),
                        throughDate = candidates.maxOf(NotificationDeliveryIdentity::eventDate),
                        leadDays = leadDays
                    )
                    rows.map { it.toIdentity() }.filter(candidates::contains)
                }
        }
    }

    override suspend fun record(delivery: NotificationDelivery): AppResult<Unit> = read {
        val identity = delivery.identity
        dao.insert(
            NotificationDeliveryEntity(
                source = identity.source,
                subjectType = identity.subjectType,
                subjectExternalId = identity.subjectExternalId,
                eventType = identity.eventType,
                eventDate = identity.eventDate,
                leadDays = identity.leadDays,
                notificationId = delivery.notificationId,
                deliveredAt = delivery.deliveredAt
            )
        )
        Unit
    }

    override suspend fun prune(eventDateBefore: LocalDate): AppResult<Int> = read {
        dao.prune(eventDateBefore)
    }

    private suspend fun <T> read(block: suspend () -> T): AppResult<T> = try {
        AppResult.Success(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: SQLiteException) {
        AppResult.Failure(AppError.LocalStorageFailure)
    } catch (_: Exception) {
        AppResult.Failure(AppError.Unknown)
    }
}

private fun NotificationDeliveryEntity.toIdentity() = NotificationDeliveryIdentity(
    source = source,
    subjectType = subjectType,
    subjectExternalId = subjectExternalId,
    eventType = eventType,
    eventDate = eventDate,
    leadDays = leadDays
)
