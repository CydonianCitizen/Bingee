package com.cydoniancitizen.bingee.data.library.local

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow

internal enum class ProgressWriteOutcome {
    SUCCESS,
    NOT_FOUND,
    NOT_IN_LIBRARY,
    NOT_TRACKABLE,
    INCOMPLETE,
    MEDIA_TYPE_MISMATCH
}

internal data class MovieProgressRow(
    @ColumnInfo(name = "media_type") val mediaType: MediaType,
    @ColumnInfo(name = "watched_at") val watchedAt: Instant?
)

internal data class SeriesCompletionRow(
    @ColumnInfo(name = "watched_episodes") val watchedEpisodes: Int,
    @ColumnInfo(name = "trackable_episodes") val trackableEpisodes: Int,
    @ColumnInfo(name = "has_sufficient_coverage") val hasSufficientCoverage: Boolean
)

@Dao
internal abstract class WatchProgressDao {
    @Query(
        """
        SELECT media_entries.media_type, movie_watch_progress.watched_at
        FROM media_entries
        INNER JOIN external_refs USING(local_media_id)
        LEFT JOIN movie_watch_progress USING(local_media_id)
        WHERE external_refs.source = :source AND external_refs.external_id = :externalId
        LIMIT 1
        """
    )
    abstract fun observeMovieProgress(source: MediaSource, externalId: String): Flow<MovieProgressRow?>

    @Query(
        """
        SELECT episodes.*
        FROM episodes
        WHERE source = :source AND external_id = :externalId
        LIMIT 1
        """
    )
    protected abstract suspend fun getEpisode(source: MediaSource, externalId: String): EpisodeEntity?

    @Query(
        """
        SELECT * FROM seasons
        WHERE source = :source AND external_id = :externalId
        LIMIT 1
        """
    )
    protected abstract suspend fun getSeason(source: MediaSource, externalId: String): SeasonEntity?

    @Query(
        """
        SELECT media_entries.*
        FROM media_entries
        INNER JOIN external_refs USING(local_media_id)
        WHERE external_refs.source = :source AND external_refs.external_id = :externalId
        LIMIT 1
        """
    )
    protected abstract suspend fun getMedia(source: MediaSource, externalId: String): MediaEntity?

    @Query(
        """
        SELECT local_episode_id FROM episodes
        WHERE local_season_id = :localSeasonId
          AND (air_date IS NULL OR air_date <= :today)
        """
    )
    protected abstract suspend fun getTrackableEpisodeIds(localSeasonId: Long, today: LocalDate): List<Long>

    @Query("SELECT * FROM movie_watch_progress WHERE local_media_id = :localMediaId")
    protected abstract suspend fun getMovieProgressByMediaId(localMediaId: Long): MovieWatchProgressEntity?

    @Query("SELECT * FROM series_watch_progress WHERE local_media_id = :localMediaId")
    protected abstract suspend fun getSeriesProgressByMediaId(localMediaId: Long): SeriesWatchProgressEntity?

    @Query(
        """
        SELECT
            (
                SELECT COUNT(*)
                FROM episodes
                INNER JOIN seasons USING(local_season_id)
                WHERE seasons.local_media_id = :localMediaId
                  AND seasons.season_number > 0
                  AND (episodes.air_date IS NULL OR episodes.air_date <= :today)
            ) AS trackable_episodes,
            (
                SELECT COUNT(*)
                FROM episodes
                INNER JOIN seasons USING(local_season_id)
                INNER JOIN episode_watch_progress USING(local_episode_id)
                WHERE seasons.local_media_id = :localMediaId
                  AND seasons.season_number > 0
                  AND (episodes.air_date IS NULL OR episodes.air_date <= :today)
            ) AS watched_episodes,
            CASE WHEN NOT EXISTS (
                SELECT 1
                FROM seasons
                WHERE seasons.local_media_id = :localMediaId
                  AND seasons.season_number > 0
                  AND (
                      seasons.episodes_fetched_at IS NULL
                      OR seasons.episode_count != (
                          SELECT COUNT(*) FROM episodes
                          WHERE episodes.local_season_id = seasons.local_season_id
                      )
                  )
            ) THEN 1 ELSE 0 END AS has_sufficient_coverage
        """
    )
    protected abstract suspend fun getSeriesCompletion(localMediaId: Long, today: LocalDate): SeriesCompletionRow

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertEpisodeProgress(progress: EpisodeWatchProgressEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertEpisodeProgress(progress: List<EpisodeWatchProgressEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertMovieProgress(progress: MovieWatchProgressEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertSeriesProgress(progress: SeriesWatchProgressEntity): Long

    @Query("DELETE FROM episode_watch_progress WHERE local_episode_id = :localEpisodeId")
    protected abstract suspend fun deleteEpisodeProgress(localEpisodeId: Long)

    @Query(
        """
        DELETE FROM episode_watch_progress
        WHERE local_episode_id IN (
            SELECT local_episode_id FROM episodes WHERE local_season_id = :localSeasonId
        )
        """
    )
    protected abstract suspend fun deleteSeasonProgress(localSeasonId: Long)

    @Query("DELETE FROM movie_watch_progress WHERE local_media_id = :localMediaId")
    protected abstract suspend fun deleteMovieProgress(localMediaId: Long)

    @Query("DELETE FROM series_watch_progress WHERE local_media_id = :localMediaId")
    protected abstract suspend fun deleteSeriesProgress(localMediaId: Long)

    @Transaction
    open suspend fun markEpisodeWatched(
        source: MediaSource,
        externalId: String,
        today: LocalDate,
        watchedAt: Instant
    ): ProgressWriteOutcome {
        val episode = getEpisode(source, externalId) ?: return ProgressWriteOutcome.NOT_FOUND
        if (episode.airDate?.isAfter(today) == true) return ProgressWriteOutcome.NOT_TRACKABLE
        insertEpisodeProgress(EpisodeWatchProgressEntity(episode.localEpisodeId, watchedAt))
        reconcileSeriesCompletion(episode.localSeasonId, today, watchedAt)
        return ProgressWriteOutcome.SUCCESS
    }

    @Transaction
    open suspend fun markEpisodeUnwatched(source: MediaSource, externalId: String): ProgressWriteOutcome {
        val episode = getEpisode(source, externalId) ?: return ProgressWriteOutcome.NOT_FOUND
        deleteEpisodeProgress(episode.localEpisodeId)
        getSeasonByLocalId(episode.localSeasonId)
            ?.takeIf { it.seasonNumber > 0 }
            ?.let { deleteSeriesProgress(it.localMediaId) }
        return ProgressWriteOutcome.SUCCESS
    }

    @Transaction
    open suspend fun markSeasonWatched(
        source: MediaSource,
        externalId: String,
        today: LocalDate,
        watchedAt: Instant
    ): ProgressWriteOutcome {
        val season = getSeason(source, externalId) ?: return ProgressWriteOutcome.NOT_FOUND
        val progress = getTrackableEpisodeIds(season.localSeasonId, today)
            .map { EpisodeWatchProgressEntity(it, watchedAt) }
        if (progress.isNotEmpty()) insertEpisodeProgress(progress)
        reconcileSeriesCompletion(season.localSeasonId, today, watchedAt)
        return ProgressWriteOutcome.SUCCESS
    }

    @Transaction
    open suspend fun markSeasonUnwatched(source: MediaSource, externalId: String): ProgressWriteOutcome {
        val season = getSeason(source, externalId) ?: return ProgressWriteOutcome.NOT_FOUND
        deleteSeasonProgress(season.localSeasonId)
        if (season.seasonNumber > 0) deleteSeriesProgress(season.localMediaId)
        return ProgressWriteOutcome.SUCCESS
    }

    @Transaction
    open suspend fun markMovieWatched(
        source: MediaSource,
        externalId: String,
        watchedAt: Instant,
        watchedDate: LocalDate? = null
    ): ProgressWriteOutcome {
        val media = getMedia(source, externalId) ?: return ProgressWriteOutcome.NOT_FOUND
        if (media.mediaType != MediaType.MOVIE) return ProgressWriteOutcome.MEDIA_TYPE_MISMATCH
        val existing = getMovieProgressByMediaId(media.localMediaId)
        val finalDate = watchedDate ?: existing?.watchedDate
        insertMovieProgress(MovieWatchProgressEntity(media.localMediaId, watchedAt, finalDate))
        return ProgressWriteOutcome.SUCCESS
    }

    @Transaction
    open suspend fun markMovieUnwatched(source: MediaSource, externalId: String): ProgressWriteOutcome {
        val media = getMedia(source, externalId) ?: return ProgressWriteOutcome.NOT_FOUND
        if (media.mediaType != MediaType.MOVIE) return ProgressWriteOutcome.MEDIA_TYPE_MISMATCH
        deleteMovieProgress(media.localMediaId)
        return ProgressWriteOutcome.SUCCESS
    }

    @Transaction
    open suspend fun markSeriesWatched(
        source: MediaSource,
        externalId: String,
        completedAt: Instant,
        watchedDate: LocalDate? = null,
        today: LocalDate = completedAt.atZone(ZoneOffset.UTC).toLocalDate()
    ): ProgressWriteOutcome {
        val media = getMedia(source, externalId) ?: return ProgressWriteOutcome.NOT_FOUND
        if (media.mediaType != MediaType.SERIES) return ProgressWriteOutcome.MEDIA_TYPE_MISMATCH
        val completion = getSeriesCompletion(media.localMediaId, today)
        if (completion.trackableEpisodes == 0 ||
            completion.watchedEpisodes != completion.trackableEpisodes ||
            !completion.hasSufficientCoverage
        ) {
            return ProgressWriteOutcome.INCOMPLETE
        }
        val existing = getSeriesProgressByMediaId(media.localMediaId)
        insertSeriesProgress(
            SeriesWatchProgressEntity(
                media.localMediaId,
                watchedDate ?: existing?.watchedDate,
                existing?.completedAt ?: completedAt
            )
        )
        return ProgressWriteOutcome.SUCCESS
    }

    @Transaction
    open suspend fun markSeriesUnwatched(source: MediaSource, externalId: String): ProgressWriteOutcome {
        val media = getMedia(source, externalId) ?: return ProgressWriteOutcome.NOT_FOUND
        if (media.mediaType != MediaType.SERIES) return ProgressWriteOutcome.MEDIA_TYPE_MISMATCH
        deleteSeriesProgress(media.localMediaId)
        return ProgressWriteOutcome.SUCCESS
    }

    @Transaction
    open suspend fun setMediaWatchedDate(
        source: MediaSource,
        externalId: String,
        watchedDate: LocalDate?,
        now: Instant
    ): ProgressWriteOutcome {
        val media = getMedia(source, externalId) ?: return ProgressWriteOutcome.NOT_FOUND
        if (media.mediaType == MediaType.MOVIE) {
            val existing = getMovieProgressByMediaId(media.localMediaId)
            if (existing != null) {
                insertMovieProgress(existing.copy(watchedDate = watchedDate))
            } else {
                insertMovieProgress(MovieWatchProgressEntity(media.localMediaId, now, watchedDate))
            }
        } else {
            val existing = getSeriesProgressByMediaId(media.localMediaId)
            if (existing != null) insertSeriesProgress(existing.copy(watchedDate = watchedDate))
        }
        return ProgressWriteOutcome.SUCCESS
    }

    private suspend fun reconcileSeriesCompletion(localSeasonId: Long, today: LocalDate, completedAt: Instant) {
        val season = getSeasonByLocalId(localSeasonId) ?: return
        if (season.seasonNumber == 0) return
        val completion = getSeriesCompletion(season.localMediaId, today)
        if (completion.trackableEpisodes > 0 &&
            completion.watchedEpisodes == completion.trackableEpisodes &&
            completion.hasSufficientCoverage
        ) {
            val existing = getSeriesProgressByMediaId(season.localMediaId)
            insertSeriesProgress(
                SeriesWatchProgressEntity(
                    season.localMediaId,
                    existing?.watchedDate,
                    existing?.completedAt ?: completedAt
                )
            )
        } else {
            deleteSeriesProgress(season.localMediaId)
        }
    }

    @Query("SELECT * FROM seasons WHERE local_season_id = :localSeasonId LIMIT 1")
    protected abstract suspend fun getSeasonByLocalId(localSeasonId: Long): SeasonEntity?
}
