package com.cydoniancitizen.bingee.data.library.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.core.model.ReleaseEventType
import com.cydoniancitizen.bingee.core.model.ReleaseSubjectType
import java.time.Instant
import java.time.LocalDate

@Entity(
    tableName = "release_events",
    foreignKeys = [
        ForeignKey(
            entity = MediaEntity::class,
            parentColumns = ["local_media_id"],
            childColumns = ["local_media_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = SeasonEntity::class,
            parentColumns = ["local_season_id"],
            childColumns = ["local_season_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = EpisodeEntity::class,
            parentColumns = ["local_episode_id"],
            childColumns = ["local_episode_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["local_media_id"]),
        Index(value = ["local_season_id"]),
        Index(value = ["local_episode_id"]),
        Index(value = ["event_date"]),
        Index(value = ["event_type"]),
        Index(
            value = ["source", "subject_type", "subject_external_id", "event_type"],
            unique = true
        )
    ]
)
internal data class ReleaseEventEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "local_event_id")
    val localEventId: Long = 0,
    @ColumnInfo(name = "local_media_id")
    val localMediaId: Long,
    @ColumnInfo(name = "local_season_id")
    val localSeasonId: Long?,
    @ColumnInfo(name = "local_episode_id")
    val localEpisodeId: Long?,
    val source: MediaSource,
    @ColumnInfo(name = "subject_type")
    val subjectType: ReleaseSubjectType,
    @ColumnInfo(name = "subject_external_id")
    val subjectExternalId: String,
    @ColumnInfo(name = "event_type")
    val eventType: ReleaseEventType,
    @ColumnInfo(name = "event_date")
    val eventDate: LocalDate,
    @ColumnInfo(name = "projected_at")
    val projectedAt: Instant,
    @ColumnInfo(name = "source_metadata_updated_at")
    val sourceMetadataUpdatedAt: Instant
) {
    init {
        require(localEventId >= 0)
        require(localMediaId > 0)
        require(subjectExternalId.isNotBlank())
    }
}

@Entity(tableName = "calendar_refresh_state")
internal data class CalendarRefreshStateEntity(
    @PrimaryKey
    @ColumnInfo(name = "singleton_key")
    val singletonKey: Int = SINGLETON_KEY,
    @ColumnInfo(name = "last_successful_refresh_at")
    val lastSuccessfulRefreshAt: Instant
) {
    init {
        require(singletonKey == SINGLETON_KEY)
    }

    companion object {
        const val SINGLETON_KEY = 1
    }
}

internal data class ReleaseEventRow(
    val source: MediaSource,
    @ColumnInfo(name = "parent_external_id") val parentExternalId: String,
    @ColumnInfo(name = "subject_type") val subjectType: ReleaseSubjectType,
    @ColumnInfo(name = "subject_external_id") val subjectExternalId: String,
    @ColumnInfo(name = "event_type") val eventType: ReleaseEventType,
    @ColumnInfo(name = "event_date") val eventDate: LocalDate,
    @ColumnInfo(name = "media_type") val mediaType: MediaType,
    @ColumnInfo(name = "media_title") val mediaTitle: String,
    @ColumnInfo(name = "poster_url") val posterUrl: String?,
    @ColumnInfo(name = "season_number") val seasonNumber: Int?,
    @ColumnInfo(name = "episode_number") val episodeNumber: Int?,
    @ColumnInfo(name = "subject_title") val subjectTitle: String?
)

internal data class ReleaseSubjectLocalIds(
    @ColumnInfo(name = "local_media_id") val localMediaId: Long,
    @ColumnInfo(name = "local_season_id") val localSeasonId: Long?,
    @ColumnInfo(name = "local_episode_id") val localEpisodeId: Long?
)
