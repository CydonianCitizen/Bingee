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
        """
        SELECT EXISTS(
            SELECT 1 FROM notification_deliveries
            WHERE source = :source
              AND subject_type = :subjectType
              AND subject_external_id = :subjectExternalId
              AND event_type = :eventType
              AND event_date = :eventDate
              AND lead_days = :leadDays
        )
        """
    )
    suspend fun contains(
        source: MediaSource,
        subjectType: ReleaseSubjectType,
        subjectExternalId: String,
        eventType: ReleaseEventType,
        eventDate: LocalDate,
        leadDays: Int
    ): Boolean

    @Query(
        "SELECT * FROM notification_deliveries " +
            "WHERE event_date BETWEEN :fromDate AND :throughDate AND lead_days = :leadDays"
    )
    suspend fun findBetween(
        fromDate: LocalDate,
        throughDate: LocalDate,
        leadDays: Int
    ): List<NotificationDeliveryEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(delivery: NotificationDeliveryEntity): Long

    @Query("DELETE FROM notification_deliveries WHERE event_date < :eventDateBefore")
    suspend fun prune(eventDateBefore: LocalDate): Int

    @Query("SELECT COUNT(*) FROM notification_deliveries")
    suspend fun count(): Int
}
