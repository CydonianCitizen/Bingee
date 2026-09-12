package com.cydoniancitizen.bingee.data.library.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import kotlinx.coroutines.flow.Flow

@Dao
internal abstract class DetailsDao {
    @Transaction
    @Query(
        """
        SELECT media_entries.*
        FROM media_entries
        INNER JOIN external_refs USING(local_media_id)
        WHERE external_refs.source = :source
          AND external_refs.media_type = :mediaType
          AND external_refs.external_id = :externalId
        LIMIT 1
        """
    )
    abstract fun observeCachedDetails(
        source: MediaSource,
        mediaType: MediaType,
        externalId: String
    ): Flow<CachedDetailsRelation?>

    @Transaction
    @Query(
        """
        SELECT media_entries.*
        FROM media_entries
        INNER JOIN external_refs USING(local_media_id)
        WHERE external_refs.source = :source
          AND external_refs.media_type = :mediaType
          AND external_refs.external_id = :externalId
        LIMIT 1
        """
    )
    abstract suspend fun getCachedDetails(
        source: MediaSource,
        mediaType: MediaType,
        externalId: String
    ): CachedDetailsRelation?

    @Query(
        """
        SELECT media_entries.*
        FROM media_entries
        INNER JOIN external_refs USING(local_media_id)
        WHERE external_refs.source = :source
          AND external_refs.media_type = :mediaType
          AND external_refs.external_id = :externalId
        LIMIT 1
        """
    )
    protected abstract suspend fun getMedia(source: MediaSource, mediaType: MediaType, externalId: String): MediaEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertMedia(media: MediaEntity): Long

    @Update(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun updateMedia(media: MediaEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertExternalRef(externalRef: ExternalRefEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun replaceDetails(details: MediaDetailsEntity)

    @Query("DELETE FROM media_genres WHERE local_media_id = :localMediaId")
    protected abstract suspend fun deleteGenres(localMediaId: Long)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertGenres(genres: List<MediaGenreEntity>)

    @Transaction
    open suspend fun storeDetails(
        candidate: MediaEntity,
        source: MediaSource,
        externalId: String,
        details: MediaDetailsEntity,
        genres: List<MediaGenreEntity>
    ) {
        val existing = getMedia(source, candidate.mediaType, externalId)
        // Details own the runtime; copying it onto the portable media row lets backups carry it.
        val runtimeMinutes = details.runtimeMinutes ?: existing?.runtimeMinutes
        val localMediaId = if (existing == null) {
            val insertedId = insertMedia(candidate.copy(runtimeMinutes = runtimeMinutes))
            insertExternalRef(ExternalRefEntity(insertedId, source, candidate.mediaType, externalId))
            insertedId
        } else {
            check(existing.mediaType == candidate.mediaType) {
                "External identity is already assigned to another media type"
            }
            updateMedia(
                candidate.copy(
                    localMediaId = existing.localMediaId,
                    createdAt = existing.createdAt,
                    isFavorite = existing.isFavorite,
                    favoriteAddedAt = existing.favoriteAddedAt,
                    runtimeMinutes = runtimeMinutes
                )
            )
            existing.localMediaId
        }
        replaceDetails(details.copy(localMediaId = localMediaId))
        deleteGenres(localMediaId)
        if (genres.isNotEmpty()) {
            insertGenres(genres.map { it.copy(localMediaId = localMediaId) })
        }
    }
}
