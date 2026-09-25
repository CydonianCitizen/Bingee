package com.cydoniancitizen.bingee.data.notification

import android.database.sqlite.SQLiteException
import com.cydoniancitizen.bingee.core.model.NotificationDelivery
import com.cydoniancitizen.bingee.core.model.NotificationDeliveryIdentity
import com.cydoniancitizen.bingee.core.result.AppError
import com.cydoniancitizen.bingee.core.result.AppResult
import com.cydoniancitizen.bingee.data.library.local.NotificationDeliveryDao
import com.cydoniancitizen.bingee.data.library.local.NotificationDeliveryEntity
import com.cydoniancitizen.bingee.domain.repository.NotificationClaim
import com.cydoniancitizen.bingee.domain.repository.NotificationDeliveryRepository
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

@Singleton
internal class RoomNotificationDeliveryRepository @Inject constructor(private val dao: NotificationDeliveryDao) :
    NotificationDeliveryRepository {
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

    override suspend fun claim(
        identity: NotificationDeliveryIdentity,
        notificationId: Int,
        now: Instant
    ): AppResult<NotificationClaim> = read {
        val token = UUID.randomUUID().toString()
        val expiresAtMs = now.plusSeconds(CLAIM_SECONDS).toEpochMilli()
        val inserted = dao.insert(
            NotificationDeliveryEntity(
                source = identity.source,
                subjectType = identity.subjectType,
                subjectExternalId = identity.subjectExternalId,
                eventType = identity.eventType,
                eventDate = identity.eventDate,
                leadDays = identity.leadDays,
                notificationId = notificationId,
                // Pending rows are excluded from delivered lookups until completeClaim clears the token.
                deliveredAt = Instant.EPOCH,
                claimToken = token,
                claimExpiresAtMs = expiresAtMs
            )
        )
        if (inserted != -1L) {
            NotificationClaim.Acquired(token)
        } else if (
            dao.takeExpiredClaim(
                identity.source, identity.subjectType, identity.subjectExternalId,
                identity.eventType, identity.eventDate, identity.leadDays,
                token, expiresAtMs, now.toEpochMilli()
            ) == 1
        ) {
            NotificationClaim.Acquired(token)
        } else {
            val row = dao.find(
                identity.source,
                identity.subjectType,
                identity.subjectExternalId,
                identity.eventType,
                identity.eventDate,
                identity.leadDays
            )
            if (row != null && row.claimToken == null) {
                NotificationClaim.AlreadyDelivered
            } else {
                NotificationClaim.InFlight
            }
        }
    }

    override suspend fun complete(delivery: NotificationDelivery, token: String): AppResult<Boolean> = read {
        val identity = delivery.identity
        dao.completeClaim(
            identity.source, identity.subjectType, identity.subjectExternalId,
            identity.eventType, identity.eventDate, identity.leadDays,
            token, delivery.notificationId, delivery.deliveredAt
        ) == 1
    }

    override suspend fun release(identity: NotificationDeliveryIdentity, token: String): AppResult<Unit> = read {
        dao.releaseClaim(
            identity.source,
            identity.subjectType,
            identity.subjectExternalId,
            identity.eventType,
            identity.eventDate,
            identity.leadDays,
            token
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

    private companion object {
        // WorkManager's retry backoff starts after this lease; a process crash cannot strand a claim.
        const val CLAIM_SECONDS = 5 * 60L
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
