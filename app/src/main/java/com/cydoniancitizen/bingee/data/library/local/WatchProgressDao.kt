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
import kotlinx.coroutines.flow.Flow

internal enum class ProgressWriteOutcome {
    SUCCESS,
    NOT_FOUND,
    NOT_TRACKABLE,
    MEDIA_TYPE_MISMATCH
}

internal data class MovieProgressRow(
    @ColumnInfo(name = "media_type") val mediaType: MediaType,
    @ColumnInfo(name = "watched_at") val watchedAt: Instant?
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
        return ProgressWriteOutcome.SUCCESS
    }

    @Transaction
    open suspend fun markEpisodeUnwatched(source: MediaSource, externalId: String): ProgressWriteOutcome {
        val episode = getEpisode(source, externalId) ?: return ProgressWriteOutcome.NOT_FOUND
        deleteEpisodeProgress(episode.localEpisodeId)
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
        return ProgressWriteOutcome.SUCCESS
    }

    @Transaction
    open suspend fun markSeasonUnwatched(source: MediaSource, externalId: String): ProgressWriteOutcome {
        val season = getSeason(source, externalId) ?: return ProgressWriteOutcome.NOT_FOUND
        deleteSeasonProgress(season.localSeasonId)
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
        watchedDate: LocalDate? = null
    ): ProgressWriteOutcome {
        val media = getMedia(source, externalId) ?: return ProgressWriteOutcome.NOT_FOUND
        if (media.mediaType != MediaType.SERIES) return ProgressWriteOutcome.MEDIA_TYPE_MISMATCH
        val existing = getSeriesProgressByMediaId(media.localMediaId)
        val finalDate = watchedDate ?: existing?.watchedDate ?: LocalDate.now()
        insertSeriesProgress(SeriesWatchProgressEntity(media.localMediaId, finalDate, completedAt))
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
            insertSeriesProgress(
                SeriesWatchProgressEntity(
                    localMediaId = media.localMediaId,
                    watchedDate = watchedDate,
                    completedAt = existing?.completedAt ?: now
                )
            )
        }
        return ProgressWriteOutcome.SUCCESS
    }
}
