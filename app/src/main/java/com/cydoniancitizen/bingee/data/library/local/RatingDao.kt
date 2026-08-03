package com.cydoniancitizen.bingee.data.library.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.PersonalRating
import java.time.Instant
import kotlinx.coroutines.flow.Flow

internal enum class RatingWriteOutcome {
    SUCCESS,
    UNCHANGED,
    NOT_FOUND
}

@Dao
internal abstract class RatingDao {
    @Query(
        """
        SELECT media_ratings.*
        FROM media_ratings
        INNER JOIN external_refs USING(local_media_id)
        WHERE external_refs.source = :source AND external_refs.external_id = :externalId
        LIMIT 1
        """
    )
    abstract fun observeRating(source: MediaSource, externalId: String): Flow<MediaRatingEntity?>

    @Query(
        """
        SELECT media_ratings.*
        FROM media_ratings
        INNER JOIN library_entries USING(local_media_id)
        ORDER BY media_ratings.local_media_id
        """
    )
    abstract fun observeActiveLibraryRatings(): Flow<List<MediaRatingEntity>>

    @Query(
        """
        SELECT local_media_id FROM external_refs
        WHERE source = :source AND external_id = :externalId
        LIMIT 1
        """
    )
    protected abstract suspend fun findLocalMediaId(source: MediaSource, externalId: String): Long?

    @Query("SELECT * FROM media_ratings WHERE local_media_id = :localMediaId LIMIT 1")
    protected abstract suspend fun findRating(localMediaId: Long): MediaRatingEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertRating(rating: MediaRatingEntity)

    @Update(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun updateRating(rating: MediaRatingEntity)

    @Query("DELETE FROM media_ratings WHERE local_media_id = :localMediaId")
    protected abstract suspend fun deleteRating(localMediaId: Long): Int

    @Transaction
    open suspend fun setRating(
        source: MediaSource,
        externalId: String,
        ratingValue: Int,
        now: Instant
    ): RatingWriteOutcome {
        require(ratingValue in PersonalRating.MIN_VALUE..PersonalRating.MAX_VALUE) {
            "Rating must be between 1 and 10"
        }
        val localMediaId = findLocalMediaId(source, externalId) ?: return RatingWriteOutcome.NOT_FOUND
        val existing = findRating(localMediaId)
        if (existing?.ratingValue == ratingValue) return RatingWriteOutcome.UNCHANGED
        if (existing == null) {
            insertRating(MediaRatingEntity(localMediaId, ratingValue, now, now))
        } else {
            updateRating(existing.copy(ratingValue = ratingValue, updatedAt = now))
        }
        return RatingWriteOutcome.SUCCESS
    }

    @Transaction
    open suspend fun removeRating(source: MediaSource, externalId: String): RatingWriteOutcome {
        val localMediaId = findLocalMediaId(source, externalId) ?: return RatingWriteOutcome.NOT_FOUND
        return if (deleteRating(localMediaId) == 0) {
            RatingWriteOutcome.UNCHANGED
        } else {
            RatingWriteOutcome.SUCCESS
        }
    }
}
