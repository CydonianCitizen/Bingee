package com.cydoniancitizen.bingee.data.library.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.cydoniancitizen.bingee.core.model.MediaSource
import java.time.Instant
import java.time.LocalDate

@Entity(
    tableName = "seasons",
    foreignKeys = [
        ForeignKey(
            entity = MediaEntity::class,
            parentColumns = ["local_media_id"],
            childColumns = ["local_media_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["local_media_id"]),
        Index(value = ["source", "external_id"], unique = true),
        Index(value = ["local_media_id", "season_number"], unique = true)
    ]
)
internal data class SeasonEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "local_season_id")
    val localSeasonId: Long = 0,
    @ColumnInfo(name = "local_media_id")
    val localMediaId: Long,
    val source: MediaSource,
    @ColumnInfo(name = "external_id")
    val externalId: String,
    @ColumnInfo(name = "season_number")
    val seasonNumber: Int,
    val name: String?,
    val overview: String?,
    @ColumnInfo(name = "poster_url")
    val posterUrl: String?,
    @ColumnInfo(name = "air_date")
    val airDate: LocalDate?,
    @ColumnInfo(name = "episode_count")
    val episodeCount: Int,
    @ColumnInfo(name = "metadata_updated_at")
    val metadataUpdatedAt: Instant,
    @ColumnInfo(name = "episodes_fetched_at")
    val episodesFetchedAt: Instant?
) {
    init {
        require(localSeasonId >= 0) { "Local season ID must not be negative" }
        require(localMediaId >= 0) { "Local media ID must not be negative" }
        require(externalId.isNotBlank()) { "Persisted season external ID must not be blank" }
        require(seasonNumber >= 0) { "Season number must not be negative" }
        require(episodeCount >= 0) { "Episode count must not be negative" }
    }
}

@Entity(
    tableName = "episodes",
    foreignKeys = [
        ForeignKey(
            entity = SeasonEntity::class,
            parentColumns = ["local_season_id"],
            childColumns = ["local_season_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["local_season_id"]),
        Index(value = ["source", "external_id"], unique = true),
        Index(value = ["local_season_id", "episode_number"], unique = true)
    ]
)
internal data class EpisodeEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "local_episode_id")
    val localEpisodeId: Long = 0,
    @ColumnInfo(name = "local_season_id")
    val localSeasonId: Long,
    val source: MediaSource,
    @ColumnInfo(name = "external_id")
    val externalId: String,
    @ColumnInfo(name = "episode_number")
    val episodeNumber: Int,
    val title: String,
    val overview: String?,
    @ColumnInfo(name = "air_date")
    val airDate: LocalDate?,
    @ColumnInfo(name = "runtime_minutes")
    val runtimeMinutes: Int?,
    @ColumnInfo(name = "still_url")
    val stillUrl: String?,
    @ColumnInfo(name = "metadata_updated_at")
    val metadataUpdatedAt: Instant
) {
    init {
        require(localEpisodeId >= 0) { "Local episode ID must not be negative" }
        require(localSeasonId >= 0) { "Local season ID must not be negative" }
        require(externalId.isNotBlank()) { "Persisted episode external ID must not be blank" }
        require(episodeNumber > 0) { "Episode number must be positive" }
        require(title.isNotBlank()) { "Persisted episode title must not be blank" }
        require(runtimeMinutes == null || runtimeMinutes > 0) { "Episode runtime must be positive" }
    }
}

@Entity(
    tableName = "episode_watch_progress",
    foreignKeys = [
        ForeignKey(
            entity = EpisodeEntity::class,
            parentColumns = ["local_episode_id"],
            childColumns = ["local_episode_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
internal data class EpisodeWatchProgressEntity(
    @PrimaryKey
    @ColumnInfo(name = "local_episode_id")
    val localEpisodeId: Long,
    @ColumnInfo(name = "watched_at")
    val watchedAt: Instant
)

@Entity(
    tableName = "movie_watch_progress",
    foreignKeys = [
        ForeignKey(
            entity = MediaEntity::class,
            parentColumns = ["local_media_id"],
            childColumns = ["local_media_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
internal data class MovieWatchProgressEntity(
    @PrimaryKey
    @ColumnInfo(name = "local_media_id")
    val localMediaId: Long,
    @ColumnInfo(name = "watched_at")
    val watchedAt: Instant
)
