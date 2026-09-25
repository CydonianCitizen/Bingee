package com.cydoniancitizen.bingee.data.library.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.ReleaseEventType
import com.cydoniancitizen.bingee.core.model.ReleaseSubjectType
import java.time.Instant
import java.time.LocalDate

@Entity(
    tableName = "notification_deliveries",
    primaryKeys = [
        "source",
        "subject_type",
        "subject_external_id",
        "event_type",
        "event_date",
        "lead_days"
    ],
    indices = [Index(value = ["event_date"])]
)
internal data class NotificationDeliveryEntity(
    val source: MediaSource,
    @ColumnInfo(name = "subject_type") val subjectType: ReleaseSubjectType,
    @ColumnInfo(name = "subject_external_id") val subjectExternalId: String,
    @ColumnInfo(name = "event_type") val eventType: ReleaseEventType,
    @ColumnInfo(name = "event_date") val eventDate: LocalDate,
    @ColumnInfo(name = "lead_days") val leadDays: Int,
    @ColumnInfo(name = "notification_id") val notificationId: Int,
    @ColumnInfo(name = "delivered_at") val deliveredAt: Instant,
    @ColumnInfo(name = "claim_token") val claimToken: String? = null,
    @ColumnInfo(name = "claim_expires_at_ms") val claimExpiresAtMs: Long? = null
) {
    init {
        require(subjectExternalId.isNotBlank())
        require(leadDays in setOf(0, 1, 3, 7))
        require((claimToken == null) == (claimExpiresAtMs == null))
    }
}
