package com.cydoniancitizen.bingee.data.library.local

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Relation
import com.cydoniancitizen.bingee.core.model.AnimeCompletionOrigin
import com.cydoniancitizen.bingee.core.model.AnimeFormat
import com.cydoniancitizen.bingee.core.model.AnimeStatus
import java.time.Instant
import java.time.LocalDate

@Entity(
    tableName = "anime_details",
    primaryKeys = ["local_media_id"],
    foreignKeys = [
        ForeignKey(
            entity = MediaEntity::class,
            parentColumns = ["local_media_id"],
            childColumns = ["local_media_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
internal data class AnimeDetailsEntity(
    @ColumnInfo(name = "local_media_id") val localMediaId: Long,
    val format: AnimeFormat,
    @ColumnInfo(name = "provider_status") val providerStatus: AnimeStatus,
    @ColumnInfo(name = "english_title") val englishTitle: String?,
    @ColumnInfo(name = "japanese_title") val japaneseTitle: String?,
    val synopsis: String?,
    @ColumnInfo(name = "episode_count") val episodeCount: Int?,
    val duration: String?,
    @ColumnInfo(name = "start_date") val startDate: LocalDate?,
    @ColumnInfo(name = "end_date") val endDate: LocalDate?,
    val season: String?,
    val year: Int?,
    @ColumnInfo(name = "provider_score") val providerScore: Double?,
    @ColumnInfo(name = "image_url") val imageUrl: String?,
    @ColumnInfo(name = "details_updated_at") val detailsUpdatedAt: Instant
)

@Entity(
    tableName = "anime_progress",
    primaryKeys = ["local_media_id"],
    foreignKeys = [
        ForeignKey(
            entity = MediaEntity::class,
            parentColumns = ["local_media_id"],
            childColumns = ["local_media_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
internal data class AnimeProgressEntity(
    @ColumnInfo(name = "local_media_id") val localMediaId: Long,
    @ColumnInfo(name = "watched_episode_count") val watchedEpisodeCount: Int,
    @ColumnInfo(name = "completed_at") val completedAt: Instant?,
    @ColumnInfo(name = "completion_origin") val completionOrigin: AnimeCompletionOrigin?,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant
)

@Entity(
    tableName = "anime_relations",
    primaryKeys = ["local_media_id", "relation_type", "related_jikan_id"],
    foreignKeys = [
        ForeignKey(
            entity = MediaEntity::class,
            parentColumns = ["local_media_id"],
            childColumns = ["local_media_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["local_media_id"]), Index(value = ["related_jikan_id"])]
)
internal data class AnimeRelationEntity(
    @ColumnInfo(name = "local_media_id") val localMediaId: Long,
    @ColumnInfo(name = "relation_type") val relationType: String,
    @ColumnInfo(name = "related_jikan_id") val relatedJikanId: String,
    @ColumnInfo(name = "related_title") val relatedTitle: String,
    @ColumnInfo(name = "related_format") val relatedFormat: AnimeFormat
)

internal data class CachedAnimeRelation(
    @Embedded val media: MediaEntity,
    @Relation(parentColumn = "local_media_id", entityColumn = "local_media_id")
    val details: AnimeDetailsEntity?,
    @Relation(parentColumn = "local_media_id", entityColumn = "local_media_id")
    val externalRefs: List<ExternalRefEntity>,
    @Relation(parentColumn = "local_media_id", entityColumn = "local_media_id")
    val relations: List<AnimeRelationEntity>,
    @Relation(parentColumn = "local_media_id", entityColumn = "local_media_id")
    val progress: AnimeProgressEntity?
)
