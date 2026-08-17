package com.cydoniancitizen.bingee.data.library.local

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
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
    internal data class PersonalViewingRow(
        @Embedded val media: MediaEntity,
        val source: MediaSource,
        @ColumnInfo(name = "external_id") val externalId: String,
        @ColumnInfo(name = "membership_added_at") val membershipAddedAt: Instant?,
        @ColumnInfo(name = "in_library") val inLibrary: Boolean,
        @ColumnInfo(name = "is_abandoned") val isAbandoned: Boolean,
        @ColumnInfo(name = "rating_value") val ratingValue: Int?,
        @ColumnInfo(name = "movie_watched_at") val movieWatchedAt: Instant?,
        @ColumnInfo(name = "movie_watched_date") val movieWatchedDate: LocalDate?,
        @ColumnInfo(name = "watched_regular_episodes") val watchedRegularEpisodes: Int,
        @ColumnInfo(name = "series_completed_at") val seriesCompletedAt: Instant?,
        @ColumnInfo(name = "series_watched_date") val seriesWatchedDate: LocalDate?
    )

    internal data class ContinueWatchingRow(
        @ColumnInfo(name = "media_type") val mediaType: MediaType,
        val title: String,
        @ColumnInfo(name = "poster_url") val posterUrl: String?,
        val source: MediaSource,
        @ColumnInfo(name = "external_id") val externalId: String,
        @ColumnInfo(name = "watched_episodes") val watchedEpisodes: Int,
        @ColumnInfo(name = "trackable_episodes") val trackableEpisodes: Int,
        @ColumnInfo(name = "completed_seasons") val completedSeasons: Int,
        @ColumnInfo(name = "trackable_seasons") val trackableSeasons: Int,
        @ColumnInfo(name = "has_sufficient_coverage") val hasSufficientCoverage: Boolean,
        @ColumnInfo(name = "is_abandoned") val isAbandoned: Boolean,
        @ColumnInfo(name = "last_progress_at") val lastProgressAt: Instant?,
        @ColumnInfo(name = "next_season_number") val nextSeasonNumber: Int?,
        @ColumnInfo(name = "next_episode_number") val nextEpisodeNumber: Int?
    )

    internal data class LibraryProgressRow(
        @ColumnInfo(name = "local_media_id") val localMediaId: Long,
        @ColumnInfo(name = "media_type") val mediaType: MediaType,
        @ColumnInfo(name = "movie_watched_at") val movieWatchedAt: Instant?,
        @ColumnInfo(name = "movie_watched_date") val movieWatchedDate: LocalDate?,
        @ColumnInfo(name = "series_watched_date") val seriesWatchedDate: LocalDate?,
        @ColumnInfo(name = "watched_episodes") val watchedEpisodes: Int,
        @ColumnInfo(name = "trackable_episodes") val trackableEpisodes: Int,
        @ColumnInfo(name = "completed_seasons") val completedSeasons: Int,
        @ColumnInfo(name = "trackable_seasons") val trackableSeasons: Int,
        @ColumnInfo(name = "has_sufficient_coverage") val hasSufficientCoverage: Boolean,
        @ColumnInfo(name = "is_abandoned") val isAbandoned: Boolean
    )

    @Transaction
    @Query(
        """
        SELECT media_entries.*,
               library_entries.added_at AS membership_added_at,
               CASE WHEN library_entries.local_media_id IS NOT NULL THEN 1 ELSE 0 END AS in_library
        FROM media_entries
        LEFT JOIN library_entries USING(local_media_id)
        WHERE (library_entries.local_media_id IS NOT NULL OR media_entries.is_favorite = 1)
          AND (:mediaType IS NULL OR media_entries.media_type = :mediaType)
          AND (
              LOWER(media_entries.title) LIKE :searchPattern ESCAPE '\'
              OR LOWER(COALESCE(media_entries.original_title, '')) LIKE :searchPattern ESCAPE '\'
          )
        ORDER BY COALESCE(library_entries.added_at, media_entries.created_at) DESC,
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
        SELECT media_entries.*,
               library_entries.added_at AS membership_added_at,
               CASE WHEN library_entries.local_media_id IS NOT NULL THEN 1 ELSE 0 END AS in_library
        FROM media_entries
        INNER JOIN external_refs USING(local_media_id)
        LEFT JOIN library_entries USING(local_media_id)
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
        WITH regular_episode_activity AS (
            SELECT seasons.local_media_id, COUNT(*) AS watched_regular_episodes
            FROM seasons
            INNER JOIN episodes USING(local_season_id)
            INNER JOIN episode_watch_progress USING(local_episode_id)
            WHERE seasons.season_number > 0
              AND (episodes.air_date IS NULL OR episodes.air_date <= :today)
            GROUP BY seasons.local_media_id
        )
        SELECT media_entries.*,
               selected_ref.source,
               selected_ref.external_id,
               library_entries.added_at AS membership_added_at,
               CASE WHEN library_entries.local_media_id IS NOT NULL THEN 1 ELSE 0 END AS in_library,
               CASE WHEN series_state_overrides.is_abandoned = 1 THEN 1 ELSE 0 END AS is_abandoned,
               media_ratings.rating_value,
               movie_watch_progress.watched_at AS movie_watched_at,
               movie_watch_progress.watched_date AS movie_watched_date,
               COALESCE(regular_episode_activity.watched_regular_episodes, 0) AS watched_regular_episodes,
               series_watch_progress.completed_at AS series_completed_at,
               series_watch_progress.watched_date AS series_watched_date
        FROM media_entries
        INNER JOIN external_refs AS selected_ref
          ON selected_ref.local_media_id = media_entries.local_media_id
         AND NOT EXISTS (
             SELECT 1
             FROM external_refs AS earlier_ref
             WHERE earlier_ref.local_media_id = selected_ref.local_media_id
               AND (
                   earlier_ref.source < selected_ref.source
                   OR (
                       earlier_ref.source = selected_ref.source
                       AND earlier_ref.external_id < selected_ref.external_id
                   )
               )
         )
        LEFT JOIN library_entries USING(local_media_id)
        LEFT JOIN media_ratings USING(local_media_id)
        LEFT JOIN series_state_overrides USING(local_media_id)
        LEFT JOIN movie_watch_progress USING(local_media_id)
        LEFT JOIN series_watch_progress USING(local_media_id)
        LEFT JOIN regular_episode_activity USING(local_media_id)
        WHERE movie_watch_progress.local_media_id IS NOT NULL
           OR series_watch_progress.local_media_id IS NOT NULL
           OR regular_episode_activity.watched_regular_episodes > 0
        ORDER BY media_entries.local_media_id
        """
    )
    abstract fun observePersonalViewing(today: LocalDate): Flow<List<PersonalViewingRow>>

    @Query(
        """
        SELECT media_entries.media_type,
               media_entries.title,
               media_entries.poster_url,
               external_refs.source,
               external_refs.external_id,
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
                     AND seasons.episodes_fetched_at IS NOT NULL
                     AND seasons.episode_count = (
                         SELECT COUNT(*)
                         FROM episodes
                         WHERE episodes.local_season_id = seasons.local_season_id
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
               ) AS trackable_seasons,
               CASE WHEN NOT EXISTS (
                   SELECT 1
                   FROM seasons
                   WHERE seasons.local_media_id = media_entries.local_media_id
                     AND seasons.season_number > 0
                     AND (
                         seasons.episodes_fetched_at IS NULL
                         OR seasons.episode_count != (
                             SELECT COUNT(*)
                             FROM episodes
                             WHERE episodes.local_season_id = seasons.local_season_id
                         )
                     )
                   ) THEN 1 ELSE 0 END AS has_sufficient_coverage,
               CASE WHEN EXISTS (
                   SELECT 1 FROM series_state_overrides
                   WHERE series_state_overrides.local_media_id = media_entries.local_media_id
                     AND series_state_overrides.is_abandoned = 1
               ) THEN 1 ELSE 0 END AS is_abandoned,
               (
                   SELECT MAX(episode_watch_progress.watched_at)
                   FROM episodes
                   INNER JOIN seasons USING(local_season_id)
                   INNER JOIN episode_watch_progress USING(local_episode_id)
                   WHERE seasons.local_media_id = media_entries.local_media_id
                     AND seasons.season_number > 0
                     AND (episodes.air_date IS NULL OR episodes.air_date <= :today)
               ) AS last_progress_at,
               (
                   SELECT seasons.season_number
                   FROM episodes
                   INNER JOIN seasons USING(local_season_id)
                   LEFT JOIN episode_watch_progress USING(local_episode_id)
                   WHERE seasons.local_media_id = media_entries.local_media_id
                     AND seasons.season_number > 0
                     AND (episodes.air_date IS NULL OR episodes.air_date <= :today)
                     AND episode_watch_progress.local_episode_id IS NULL
                   ORDER BY seasons.season_number, episodes.episode_number
                   LIMIT 1
               ) AS next_season_number,
               (
                   SELECT episodes.episode_number
                   FROM episodes
                   INNER JOIN seasons USING(local_season_id)
                   LEFT JOIN episode_watch_progress USING(local_episode_id)
                   WHERE seasons.local_media_id = media_entries.local_media_id
                     AND seasons.season_number > 0
                     AND (episodes.air_date IS NULL OR episodes.air_date <= :today)
                     AND episode_watch_progress.local_episode_id IS NULL
                   ORDER BY seasons.season_number, episodes.episode_number
                   LIMIT 1
               ) AS next_episode_number
        FROM media_entries
        INNER JOIN library_entries USING(local_media_id)
        INNER JOIN external_refs USING(local_media_id)
        WHERE external_refs.source = :source
        """
    )
    abstract fun observeContinueWatchingRows(source: MediaSource, today: LocalDate): Flow<List<ContinueWatchingRow>>

    @Query(
        """
        SELECT external_refs.source, external_refs.external_id, media_entries.media_type
        FROM library_entries
        INNER JOIN media_entries USING(local_media_id)
        INNER JOIN external_refs USING(local_media_id)
        LEFT JOIN media_details USING(local_media_id)
        ORDER BY CASE WHEN media_details.details_fetched_at IS NULL THEN 0 ELSE 1 END ASC,
                 media_details.details_fetched_at ASC,
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
        WITH personal_state AS (
            SELECT local_media_id FROM library_entries
            UNION
            SELECT local_media_id FROM movie_watch_progress
            UNION
            SELECT local_media_id FROM series_watch_progress
            UNION
            SELECT local_media_id FROM series_state_overrides
            UNION
            SELECT local_media_id FROM media_entries WHERE is_favorite = 1
            UNION
            SELECT seasons.local_media_id
            FROM seasons
            INNER JOIN episodes USING(local_season_id)
            INNER JOIN episode_watch_progress USING(local_episode_id)
        ),
        season_progress AS (
            SELECT seasons.local_media_id,
                   seasons.local_season_id,
                   seasons.episode_count,
                   seasons.episodes_fetched_at,
                   (
                       SELECT COUNT(*)
                       FROM episodes AS cached_episodes
                       WHERE cached_episodes.local_season_id = seasons.local_season_id
                   ) AS cached_episode_count,
                   COUNT(*) AS trackable_episodes,
                   SUM(
                       CASE WHEN episode_watch_progress.local_episode_id IS NOT NULL
                            THEN 1 ELSE 0 END
                   ) AS watched_episodes
            FROM seasons
            INNER JOIN personal_state USING(local_media_id)
            INNER JOIN episodes USING(local_season_id)
            LEFT JOIN episode_watch_progress USING(local_episode_id)
            WHERE seasons.season_number > 0
              AND (episodes.air_date IS NULL OR episodes.air_date <= :today)
            GROUP BY seasons.local_media_id, seasons.local_season_id
        ),
        series_progress AS (
            SELECT season_progress.local_media_id,
                   SUM(season_progress.watched_episodes) AS watched_episodes,
                   SUM(season_progress.trackable_episodes) AS trackable_episodes,
                   SUM(
                       CASE WHEN season_progress.watched_episodes = season_progress.trackable_episodes
                                     AND season_progress.episodes_fetched_at IS NOT NULL
                                     AND season_progress.episode_count = season_progress.cached_episode_count
                            THEN 1 ELSE 0 END
                   ) AS completed_seasons,
                   COUNT(*) AS trackable_seasons
            FROM season_progress
            GROUP BY season_progress.local_media_id
        )
        SELECT media_entries.local_media_id,
               media_entries.media_type,
               movie_watch_progress.watched_at AS movie_watched_at,
               movie_watch_progress.watched_date AS movie_watched_date,
               series_watch_progress.watched_date AS series_watched_date,
               COALESCE(series_progress.watched_episodes, 0) AS watched_episodes,
               COALESCE(series_progress.trackable_episodes, 0) AS trackable_episodes,
               COALESCE(series_progress.completed_seasons, 0) AS completed_seasons,
               COALESCE(series_progress.trackable_seasons, 0) AS trackable_seasons,
               CASE WHEN NOT EXISTS (
                   SELECT 1
                   FROM seasons
                   WHERE seasons.local_media_id = media_entries.local_media_id
                     AND seasons.season_number > 0
                     AND (
                         seasons.episodes_fetched_at IS NULL
                         OR seasons.episode_count != (
                             SELECT COUNT(*)
                             FROM episodes
                             WHERE episodes.local_season_id = seasons.local_season_id
                         )
                     )
               ) THEN 1 ELSE 0 END AS has_sufficient_coverage,
               CASE WHEN series_state_overrides.is_abandoned = 1 THEN 1 ELSE 0 END AS is_abandoned
        FROM media_entries
        INNER JOIN personal_state USING(local_media_id)
        LEFT JOIN movie_watch_progress USING(local_media_id)
        LEFT JOIN series_watch_progress USING(local_media_id)
        LEFT JOIN series_state_overrides USING(local_media_id)
        LEFT JOIN series_progress USING(local_media_id)
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

    @Transaction
    open suspend fun setSeriesAbandoned(
        source: MediaSource,
        externalId: String,
        isAbandoned: Boolean
    ): ProgressWriteOutcome {
        val media = getMediaByExternalRef(source, externalId) ?: return ProgressWriteOutcome.NOT_FOUND
        if (media.mediaType != MediaType.SERIES) return ProgressWriteOutcome.MEDIA_TYPE_MISMATCH
        if (isAbandoned && !isInLibraryByMediaId(media.localMediaId)) return ProgressWriteOutcome.NOT_IN_LIBRARY
        if (isAbandoned) {
            insertSeriesStateOverride(SeriesStateOverrideEntity(media.localMediaId))
        } else {
            deleteSeriesStateOverride(media.localMediaId)
        }
        return ProgressWriteOutcome.SUCCESS
    }

    @Query("SELECT EXISTS(SELECT 1 FROM library_entries WHERE local_media_id = :localMediaId)")
    protected abstract suspend fun isInLibraryByMediaId(localMediaId: Long): Boolean

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

    @Query(
        """
        UPDATE media_entries
        SET is_favorite = :isFavorite
        WHERE local_media_id = (
            SELECT local_media_id FROM external_refs
            WHERE source = :source AND external_id = :externalId
        )
        """
    )
    abstract suspend fun updateFavoriteState(source: MediaSource, externalId: String, isFavorite: Boolean): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertMedia(media: MediaEntity): Long

    @Update(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun updateMedia(media: MediaEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertExternalRef(externalRef: ExternalRefEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertMembership(membership: LibraryMembershipEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertSeriesStateOverride(override: SeriesStateOverrideEntity)

    @Query("DELETE FROM series_state_overrides WHERE local_media_id = :localMediaId")
    protected abstract suspend fun deleteSeriesStateOverride(localMediaId: Long)

    @Transaction
    @Query(
        """
        SELECT media_entries.*,
               library_entries.added_at AS membership_added_at,
               CASE WHEN library_entries.local_media_id IS NOT NULL THEN 1 ELSE 0 END AS in_library
        FROM media_entries
        LEFT JOIN library_entries USING(local_media_id)
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
                        createdAt = existing.createdAt,
                        isFavorite = existing.isFavorite
                    )
                )
                existing.localMediaId
            }

        insertMembership(LibraryMembershipEntity(localMediaId, addedAt))
        return checkNotNull(getLibraryItem(localMediaId)) {
            "Library transaction completed without a readable membership"
        }
    }

    @Transaction
    open suspend fun ensureMediaAndSetFavorite(
        candidate: MediaEntity,
        source: MediaSource,
        externalId: String,
        isFavorite: Boolean
    ) {
        val existing = getMediaByExternalRef(source, externalId)
        if (existing == null) {
            val insertedId = insertMedia(candidate.copy(isFavorite = isFavorite))
            insertExternalRef(
                ExternalRefEntity(
                    localMediaId = insertedId,
                    source = source,
                    externalId = externalId
                )
            )
        } else {
            updateMedia(existing.copy(isFavorite = isFavorite))
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
    protected abstract suspend fun deleteMembership(source: MediaSource, externalId: String): Int

    @Transaction
    open suspend fun removeMembership(source: MediaSource, externalId: String): Int {
        val removed = deleteMembership(source, externalId)
        if (removed > 0) {
            getMediaByExternalRef(source, externalId)?.let { deleteSeriesStateOverride(it.localMediaId) }
        }
        return removed
    }
}
