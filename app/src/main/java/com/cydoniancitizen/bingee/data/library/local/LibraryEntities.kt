package com.cydoniancitizen.bingee.data.library.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import java.time.Instant
import java.time.LocalDate

@Entity(
    tableName = "media_entries",
    indices = [Index(value = ["media_type"])]
)
internal data class MediaEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "local_media_id")
    val localMediaId: Long = 0,
    @ColumnInfo(name = "media_type")
    val mediaType: MediaType,
    val title: String,
    @ColumnInfo(name = "original_title")
    val originalTitle: String?,
    val overview: String?,
    @ColumnInfo(name = "poster_url")
    val posterUrl: String?,
    @ColumnInfo(name = "release_date")
    val releaseDate: LocalDate?,
    @ColumnInfo(name = "created_at")
    val createdAt: Instant,
    @ColumnInfo(name = "metadata_updated_at")
    val metadataUpdatedAt: Instant,
    @ColumnInfo(name = "is_favorite")
    val isFavorite: Boolean = false,
    @ColumnInfo(name = "favorite_added_at")
    val favoriteAddedAt: Instant? = null
) {
    init {
        require(localMediaId >= 0) { "Local media ID must not be negative" }
        require(title.isNotBlank()) { "Persisted media title must not be blank" }
    }
}

@Entity(
    tableName = "external_refs",
    primaryKeys = ["source", "external_id"],
    foreignKeys = [
        ForeignKey(
            entity = MediaEntity::class,
            parentColumns = ["local_media_id"],
            childColumns = ["local_media_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["local_media_id"])]
)
internal data class ExternalRefEntity(
    @ColumnInfo(name = "local_media_id")
    val localMediaId: Long,
    val source: MediaSource,
    @ColumnInfo(name = "external_id")
    val externalId: String
) {
    init {
        require(localMediaId > 0) { "External reference requires a persisted local media ID" }
        require(externalId.isNotBlank()) { "Persisted external ID must not be blank" }
    }
}

@Entity(
    tableName = "library_entries",
    foreignKeys = [
        ForeignKey(
            entity = MediaEntity::class,
            parentColumns = ["local_media_id"],
            childColumns = ["local_media_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
internal data class LibraryMembershipEntity(
    @PrimaryKey
    @ColumnInfo(name = "local_media_id")
    val localMediaId: Long,
    @ColumnInfo(name = "added_at")
    val addedAt: Instant
) {
    init {
        require(localMediaId > 0) { "Library membership requires a persisted local media ID" }
    }
}
