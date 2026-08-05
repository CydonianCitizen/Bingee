package com.cydoniancitizen.bingee.data.library.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.cydoniancitizen.bingee.core.model.AnimeWatchProgress
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import kotlinx.coroutines.flow.Flow

@Dao
internal abstract class AnimeDao {
    @Transaction
    @Query(
        """
        SELECT media_entries.* FROM media_entries
        INNER JOIN external_refs USING(local_media_id)
        WHERE external_refs.source = 'JIKAN' AND external_refs.external_id = :externalId
        LIMIT 1
        """
    )
    abstract fun observeAnime(externalId: String): Flow<CachedAnimeRelation?>

    @Transaction
    @Query(
        """
        SELECT media_entries.* FROM media_entries
        INNER JOIN external_refs USING(local_media_id)
        WHERE external_refs.source = 'JIKAN' AND external_refs.external_id = :externalId
        LIMIT 1
        """
    )
    abstract suspend fun getAnime(externalId: String): CachedAnimeRelation?

    @Query(
        """
        SELECT media_entries.* FROM media_entries
        INNER JOIN external_refs USING(local_media_id)
        WHERE external_refs.source = :source AND external_refs.external_id = :externalId
        LIMIT 1
        """
    )
    protected abstract suspend fun getMedia(source: MediaSource, externalId: String): MediaEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertMedia(media: MediaEntity): Long

    @Update(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun updateMedia(media: MediaEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertExternalRef(ref: ExternalRefEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun replaceDetails(details: AnimeDetailsEntity)

    @Query("DELETE FROM anime_relations WHERE local_media_id = :localMediaId")
    protected abstract suspend fun deleteRelations(localMediaId: Long)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertRelations(relations: List<AnimeRelationEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun replaceProgress(progress: AnimeProgressEntity)

    @Query("DELETE FROM anime_progress WHERE local_media_id = :localMediaId")
    protected abstract suspend fun deleteProgress(localMediaId: Long)

    @Transaction
    open suspend fun storeAnime(
        media: MediaEntity,
        externalId: String,
        details: AnimeDetailsEntity,
        relations: List<AnimeRelationEntity>
    ): Long {
        require(media.mediaType == MediaType.ANIME)
        require(externalId.toLongOrNull()?.let { it > 0 } == true)
        val existing = getMedia(MediaSource.JIKAN, externalId)
        val id = if (existing == null) {
            insertMedia(media).also { insertExternalRef(ExternalRefEntity(it, MediaSource.JIKAN, externalId)) }
        } else {
            check(existing.mediaType == MediaType.ANIME)
            updateMedia(media.copy(localMediaId = existing.localMediaId, createdAt = existing.createdAt))
            existing.localMediaId
        }
        replaceDetails(details.copy(localMediaId = id))
        deleteRelations(id)
        if (relations.isNotEmpty()) insertRelations(relations.map { it.copy(localMediaId = id) })
        return id
    }

    @Transaction
    open suspend fun setProgress(source: MediaSource, externalId: String, progress: AnimeWatchProgress?) {
        val media = getMedia(source, externalId) ?: error("Anime identity not found")
        require(source == MediaSource.JIKAN && media.mediaType == MediaType.ANIME)
        if (progress == null) {
            deleteProgress(media.localMediaId)
        } else {
            replaceProgress(
                AnimeProgressEntity(
                    media.localMediaId,
                    progress.watchedEpisodes,
                    progress.completedAt,
                    progress.completionOrigin,
                    progress.updatedAt
                )
            )
        }
    }
}
