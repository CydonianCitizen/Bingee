package com.cydoniancitizen.bingee.data.library.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

@Dao
internal abstract class LibraryDao {
    internal data class LibraryProgressRow(
        @androidx.room.ColumnInfo(name = "local_media_id") val localMediaId: Long,
        @androidx.room.ColumnInfo(name = "media_type") val mediaType: MediaType,
        @androidx.room.ColumnInfo(name = "movie_watched_at") val movieWatchedAt: Instant?,
        @androidx.room.ColumnInfo(name = "anime_watched_episodes") val animeWatchedEpisodes: Int,
        @androidx.room.ColumnInfo(name = "anime_episode_total") val animeEpisodeTotal: Int?,
        @androidx.room.ColumnInfo(name = "anime_completed_at") val animeCompletedAt: Instant?,
        @androidx.room.ColumnInfo(name = "watched_episodes") val watchedEpisodes: Int,
        @androidx.room.ColumnInfo(name = "trackable_episodes") val trackableEpisodes: Int,
        @androidx.room.ColumnInfo(name = "completed_seasons") val completedSeasons: Int,
        @androidx.room.ColumnInfo(name = "trackable_seasons") val trackableSeasons: Int
    )

    @Transaction
    @Query(
        """
        SELECT media_entries.*, library_entries.added_at AS membership_added_at
        FROM media_entries
        INNER JOIN library_entries USING(local_media_id)
        LEFT JOIN anime_details USING(local_media_id)
        WHERE (:mediaType IS NULL OR media_entries.media_type = :mediaType)
          AND (
              LOWER(media_entries.title) LIKE :searchPattern ESCAPE '\'
              OR LOWER(COALESCE(media_entries.original_title, '')) LIKE :searchPattern ESCAPE '\'
              OR LOWER(COALESCE(anime_details.english_title, '')) LIKE :searchPattern ESCAPE '\'
              OR LOWER(COALESCE(anime_details.japanese_title, '')) LIKE :searchPattern ESCAPE '\'
          )
        ORDER BY library_entries.added_at DESC,
                 LOWER(media_entries.title) ASC,
                 media_entries.local_media_id ASC
        """
    )
    abstract fun observeLibraryItems(
        mediaType: MediaType? = null,
        searchPattern: String = "%"
    ): Flow<List<LibraryItemWithRefs>>

    @Query("SELECT COUNT(*) FROM library_entries")
    abstract fun observeLibraryEntryCount(): Flow<Int>

    @Transaction
    @Query(
        """
        SELECT media_entries.*, library_entries.added_at AS membership_added_at
        FROM media_entries
        INNER JOIN library_entries USING(local_media_id)
        INNER JOIN external_refs USING(local_media_id)
        WHERE external_refs.source = :source AND external_refs.external_id = :externalId
        LIMIT 1
        """
    )
    abstract fun observeLibraryItem(source: MediaSource, externalId: String): Flow<LibraryItemWithRefs?>

    @Query(
        """
        SELECT external_refs.*
        FROM external_refs
        INNER JOIN library_entries USING(local_media_id)
        ORDER BY external_refs.source, external_refs.external_id
        """
    )
    abstract fun observeMembershipRefs(): Flow<List<ExternalRefEntity>>

    @Query(
        """
        SELECT external_refs.source, external_refs.external_id, media_entries.media_type
        FROM library_entries
        INNER JOIN media_entries USING(local_media_id)
        INNER JOIN external_refs USING(local_media_id)
        LEFT JOIN media_details USING(local_media_id)
        LEFT JOIN anime_details USING(local_media_id)
        ORDER BY CASE WHEN COALESCE(media_details.details_fetched_at, anime_details.details_updated_at) IS NULL THEN 0 ELSE 1 END ASC,
                 COALESCE(media_details.details_fetched_at, anime_details.details_updated_at) ASC,
                 media_entries.metadata_updated_at ASC,
                 external_refs.source ASC,
                 external_refs.external_id ASC,
                 media_entries.media_type ASC
        LIMIT :limit
        """
    )
    abstract suspend fun getBackgroundRefreshCandidates(limit: Int): List<BackgroundRefreshCandidateRow>

    @Query(
        """
        SELECT media_entries.local_media_id,
               media_entries.media_type,
               movie_watch_progress.watched_at AS movie_watched_at,
               COALESCE(anime_progress.watched_episode_count, 0) AS anime_watched_episodes,
               anime_details.episode_count AS anime_episode_total,
               anime_progress.completed_at AS anime_completed_at,
               (
                   SELECT COUNT(*)
                   FROM episodes
                   INNER JOIN seasons USING(local_season_id)
                   INNER JOIN episode_watch_progress USING(local_episode_id)
                   WHERE seasons.local_media_id = media_entries.local_media_id
                     AND seasons.season_number > 0
                     AND (episodes.air_date IS NULL OR episodes.air_date <= :today)
               ) AS watched_episodes,
               (
                   SELECT COUNT(*)
                   FROM episodes
                   INNER JOIN seasons USING(local_season_id)
                   WHERE seasons.local_media_id = media_entries.local_media_id
                     AND seasons.season_number > 0
                     AND (episodes.air_date IS NULL OR episodes.air_date <= :today)
               ) AS trackable_episodes,
               (
                   SELECT COUNT(*)
                   FROM seasons
                   WHERE seasons.local_media_id = media_entries.local_media_id
                     AND seasons.season_number > 0
                     AND EXISTS (
                         SELECT 1 FROM episodes
                         WHERE episodes.local_season_id = seasons.local_season_id
                           AND (episodes.air_date IS NULL OR episodes.air_date <= :today)
                     )
                     AND NOT EXISTS (
                         SELECT 1 FROM episodes
                         LEFT JOIN episode_watch_progress USING(local_episode_id)
                         WHERE episodes.local_season_id = seasons.local_season_id
                           AND (episodes.air_date IS NULL OR episodes.air_date <= :today)
                           AND episode_watch_progress.local_episode_id IS NULL
                     )
               ) AS completed_seasons,
               (
                   SELECT COUNT(*)
                   FROM seasons
                   WHERE seasons.local_media_id = media_entries.local_media_id
                     AND seasons.season_number > 0
                     AND EXISTS (
                         SELECT 1 FROM episodes
                         WHERE episodes.local_season_id = seasons.local_season_id
                           AND (episodes.air_date IS NULL OR episodes.air_date <= :today)
                     )
               ) AS trackable_seasons
        FROM media_entries
        INNER JOIN library_entries USING(local_media_id)
        LEFT JOIN movie_watch_progress USING(local_media_id)
        LEFT JOIN anime_progress USING(local_media_id)
        LEFT JOIN anime_details USING(local_media_id)
        """
    )
    abstract fun observeLibraryProgress(today: LocalDate): Flow<List<LibraryProgressRow>>

    @Query(
        """
        SELECT media_entries.*
        FROM media_entries
        INNER JOIN external_refs USING(local_media_id)
        WHERE external_refs.source = :source AND external_refs.external_id = :externalId
        LIMIT 1
        """
    )
    abstract suspend fun getMediaByExternalRef(source: MediaSource, externalId: String): MediaEntity?

    @Query(
        """
        SELECT EXISTS(
            SELECT 1
            FROM external_refs
            INNER JOIN library_entries USING(local_media_id)
            WHERE external_refs.source = :source AND external_refs.external_id = :externalId
        )
        """
    )
    abstract suspend fun isInLibrary(source: MediaSource, externalId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertMedia(media: MediaEntity): Long

    @Update(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun updateMedia(media: MediaEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertExternalRef(externalRef: ExternalRefEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertMembership(membership: LibraryMembershipEntity)

    @Transaction
    @Query(
        """
        SELECT media_entries.*, library_entries.added_at AS membership_added_at
        FROM media_entries
        INNER JOIN library_entries USING(local_media_id)
        WHERE media_entries.local_media_id = :localMediaId
        LIMIT 1
        """
    )
    protected abstract suspend fun getLibraryItem(localMediaId: Long): LibraryItemWithRefs?

    @Transaction
    open suspend fun addExistingToLibrary(
        source: MediaSource,
        externalId: String,
        addedAt: Instant
    ): LibraryItemWithRefs? {
        val existing = getMediaByExternalRef(source, externalId) ?: return null
        insertMembership(LibraryMembershipEntity(existing.localMediaId, addedAt))
        return getLibraryItem(existing.localMediaId)
    }

    @Transaction
    open suspend fun addToLibrary(
        candidate: MediaEntity,
        source: MediaSource,
        externalId: String,
        addedAt: Instant
    ): LibraryItemWithRefs {
        val existing = getMediaByExternalRef(source, externalId)
        val localMediaId =
            if (existing == null) {
                val insertedId = insertMedia(candidate)
                insertExternalRef(
                    ExternalRefEntity(
                        localMediaId = insertedId,
                        source = source,
                        externalId = externalId
                    )
                )
                insertedId
            } else {
                check(existing.mediaType == candidate.mediaType) {
                    "External identity is already assigned to another media type"
                }
                updateMedia(
                    candidate.copy(
                        localMediaId = existing.localMediaId,
                        createdAt = existing.createdAt
                    )
                )
                existing.localMediaId
            }

        insertMembership(LibraryMembershipEntity(localMediaId, addedAt))
        return checkNotNull(getLibraryItem(localMediaId)) {
            "Library transaction completed without a readable membership"
        }
    }

    @Query(
        """
        DELETE FROM library_entries
        WHERE local_media_id = (
            SELECT local_media_id
            FROM external_refs
            WHERE source = :source AND external_id = :externalId
        )
        """
    )
    abstract suspend fun removeMembership(source: MediaSource, externalId: String): Int
}
