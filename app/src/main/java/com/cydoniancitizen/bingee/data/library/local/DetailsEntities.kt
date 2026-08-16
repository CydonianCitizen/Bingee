package com.cydoniancitizen.bingee.data.library.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import com.cydoniancitizen.bingee.core.model.MediaSource
import java.time.Instant

@Entity(
    tableName = "media_details",
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
internal data class MediaDetailsEntity(
    @ColumnInfo(name = "local_media_id") val localMediaId: Long,
    @ColumnInfo(name = "backdrop_url") val backdropUrl: String?,
    @ColumnInfo(name = "production_status") val productionStatus: String,
    @ColumnInfo(name = "original_language") val originalLanguage: String?,
    @ColumnInfo(name = "runtime_minutes") val runtimeMinutes: Int?,
    @ColumnInfo(name = "episode_runtime_minutes") val episodeRuntimeMinutes: Int?,
    @ColumnInfo(name = "number_of_seasons") val numberOfSeasons: Int?,
    @ColumnInfo(name = "number_of_episodes") val numberOfEpisodes: Int?,
    @ColumnInfo(name = "details_fetched_at") val detailsFetchedAt: Instant
)

@Entity(
    tableName = "media_genres",
    primaryKeys = ["local_media_id", "genre_order"],
    foreignKeys = [
        ForeignKey(
            entity = MediaEntity::class,
            parentColumns = ["local_media_id"],
            childColumns = ["local_media_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["source", "genre_id"])]
)
internal data class MediaGenreEntity(
    @ColumnInfo(name = "local_media_id") val localMediaId: Long,
    @ColumnInfo(name = "genre_order") val genreOrder: Int,
    val name: String,
    val source: MediaSource? = null,
    @ColumnInfo(name = "genre_id") val genreId: Long? = null
) {
    init {
        require(genreOrder >= 0) { "Genre order must not be negative" }
        require(name.isNotBlank()) { "Persisted genre must not be blank" }
        require((source == null) == (genreId == null)) { "Genre source and ID must both be present or absent" }
        require(genreId == null || genreId > 0) { "Genre ID must be positive" }
    }
}
