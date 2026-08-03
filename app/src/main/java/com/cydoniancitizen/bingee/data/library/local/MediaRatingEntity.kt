package com.cydoniancitizen.bingee.data.library.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import com.cydoniancitizen.bingee.core.model.PersonalRating
import java.time.Instant

@Entity(
    tableName = "media_ratings",
    foreignKeys = [
        ForeignKey(
            entity = MediaEntity::class,
            parentColumns = ["local_media_id"],
            childColumns = ["local_media_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
internal data class MediaRatingEntity(
    @PrimaryKey
    @ColumnInfo(name = "local_media_id")
    val localMediaId: Long,
    @ColumnInfo(name = "rating_value")
    val ratingValue: Int,
    @ColumnInfo(name = "rated_at")
    val ratedAt: Instant,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Instant
) {
    init {
        require(localMediaId > 0) { "Rating requires a persisted local media ID" }
        require(ratingValue in PersonalRating.MIN_VALUE..PersonalRating.MAX_VALUE) {
            "Persisted rating must be between 1 and 10"
        }
        require(!updatedAt.isBefore(ratedAt)) { "Rating update cannot predate first rating" }
    }
}
