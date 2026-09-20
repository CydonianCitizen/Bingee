package com.cydoniancitizen.bingee.data.library.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import java.time.Instant

internal data class BackgroundRefreshCandidateRow(
    @ColumnInfo(name = "local_media_id") val localMediaId: Long,
    val source: MediaSource,
    @ColumnInfo(name = "external_id") val externalId: String,
    @ColumnInfo(name = "media_type") val mediaType: MediaType
)

/** When background refresh last picked a title, whatever the outcome. Success stays in `details_fetched_at`. */
@Entity(
    tableName = "background_refresh_attempts",
    foreignKeys = [
        ForeignKey(
            entity = MediaEntity::class,
            parentColumns = ["local_media_id"],
            childColumns = ["local_media_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
internal data class BackgroundRefreshAttemptEntity(
    @PrimaryKey
    @ColumnInfo(name = "local_media_id")
    val localMediaId: Long,
    @ColumnInfo(name = "attempted_at")
    val attemptedAt: Instant
)
