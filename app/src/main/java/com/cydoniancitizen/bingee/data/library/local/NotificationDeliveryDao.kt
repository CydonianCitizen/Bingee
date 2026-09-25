package com.cydoniancitizen.bingee.data.library.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.ReleaseEventType
import com.cydoniancitizen.bingee.core.model.ReleaseSubjectType
import java.time.LocalDate

@Dao
internal interface NotificationDeliveryDao {
    @Query(
        "SELECT * FROM notification_deliveries " +
            "WHERE event_date BETWEEN :fromDate AND :throughDate AND lead_days = :leadDays " +
            "AND claim_token IS NULL"
    )
    suspend fun findBetween(
        fromDate: LocalDate,
        throughDate: LocalDate,
        leadDays: Int
    ): List<NotificationDeliveryEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(delivery: NotificationDeliveryEntity): Long

    @Query(
        "SELECT * FROM notification_deliveries WHERE source = :source AND subject_type = :subjectType " +
            "AND subject_external_id = :subjectExternalId AND event_type = :eventType " +
            "AND event_date = :eventDate AND lead_days = :leadDays"
    )
    suspend fun find(
        source: MediaSource,
        subjectType: ReleaseSubjectType,
        subjectExternalId: String,
        eventType: ReleaseEventType,
        eventDate: LocalDate,
        leadDays: Int
    ): NotificationDeliveryEntity?

    @Query(
        "UPDATE notification_deliveries SET claim_token = :token, claim_expires_at_ms = :expiresAtMs " +
            "WHERE source = :source AND subject_type = :subjectType " +
            "AND subject_external_id = :subjectExternalId AND event_type = :eventType " +
            "AND event_date = :eventDate AND lead_days = :leadDays " +
            "AND claim_token IS NOT NULL AND claim_expires_at_ms <= :nowMs"
    )
    suspend fun takeExpiredClaim(
        source: MediaSource,
        subjectType: ReleaseSubjectType,
        subjectExternalId: String,
        eventType: ReleaseEventType,
        eventDate: LocalDate,
        leadDays: Int,
        token: String,
        expiresAtMs: Long,
        nowMs: Long
    ): Int

    @Query(
        "UPDATE notification_deliveries SET delivered_at = :deliveredAt, notification_id = :notificationId, " +
            "claim_token = NULL, claim_expires_at_ms = NULL " +
            "WHERE source = :source AND subject_type = :subjectType " +
            "AND subject_external_id = :subjectExternalId AND event_type = :eventType " +
            "AND event_date = :eventDate AND lead_days = :leadDays AND claim_token = :token"
    )
    suspend fun completeClaim(
        source: MediaSource,
        subjectType: ReleaseSubjectType,
        subjectExternalId: String,
        eventType: ReleaseEventType,
        eventDate: LocalDate,
        leadDays: Int,
        token: String,
        notificationId: Int,
        deliveredAt: java.time.Instant
    ): Int

    @Query(
        "DELETE FROM notification_deliveries WHERE source = :source AND subject_type = :subjectType " +
            "AND subject_external_id = :subjectExternalId AND event_type = :eventType " +
            "AND event_date = :eventDate AND lead_days = :leadDays AND claim_token = :token"
    )
    suspend fun releaseClaim(
        source: MediaSource,
        subjectType: ReleaseSubjectType,
        subjectExternalId: String,
        eventType: ReleaseEventType,
        eventDate: LocalDate,
        leadDays: Int,
        token: String
    ): Int

    @Query("DELETE FROM notification_deliveries WHERE event_date < :eventDateBefore")
    suspend fun prune(eventDateBefore: LocalDate): Int

    @Query("SELECT COUNT(*) FROM notification_deliveries")
    suspend fun count(): Int
}
