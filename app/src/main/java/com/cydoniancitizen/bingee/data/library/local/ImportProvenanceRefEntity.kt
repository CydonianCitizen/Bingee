package com.cydoniancitizen.bingee.data.library.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "import_provenance_refs",
    primaryKeys = ["namespace", "external_id", "target_type"],
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
        Index(value = ["local_episode_id"])
    ]
)
internal data class ImportProvenanceRefEntity(
    val namespace: String,
    @ColumnInfo(name = "external_id") val externalId: String,
    @ColumnInfo(name = "target_type") val targetType: String,
    @ColumnInfo(name = "local_media_id") val localMediaId: Long? = null,
    @ColumnInfo(name = "local_season_id") val localSeasonId: Long? = null,
    @ColumnInfo(name = "local_episode_id") val localEpisodeId: Long? = null
) {
    init {
        require(namespace.isNotBlank() && externalId.isNotBlank() && targetType.isNotBlank())
        require(listOf(localMediaId, localSeasonId, localEpisodeId).count { it != null } == 1)
    }
}
