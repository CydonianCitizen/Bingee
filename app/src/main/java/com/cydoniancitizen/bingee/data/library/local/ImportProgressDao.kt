package com.cydoniancitizen.bingee.data.library.local

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import java.time.Instant

internal enum class ImportProgressWriteOutcome { ADDED, PRESERVED, CONFLICT, NOT_FOUND }

internal data class ImportMovieState(
    @ColumnInfo(name = "local_media_id")
    val localMediaId: Long,
    @ColumnInfo(name = "media_type")
    val mediaType: MediaType,
    @ColumnInfo(name = "watched_at")
    val watchedAt: Instant?
)

internal data class ImportEpisodeState(
    @ColumnInfo(name = "local_episode_id")
    val localEpisodeId: Long,
    @ColumnInfo(name = "watched_at")
    val watchedAt: Instant?
)

@Dao
internal abstract class ImportProgressDao {
    @Query(
        """
        SELECT media_entries.local_media_id, media_entries.media_type,
               movie_watch_progress.watched_at
        FROM media_entries
        INNER JOIN external_refs USING(local_media_id)
        LEFT JOIN movie_watch_progress USING(local_media_id)
        WHERE external_refs.source = :source AND external_refs.external_id = :externalId
        LIMIT 1
        """
    )
    abstract suspend fun getMovieState(source: MediaSource, externalId: String): ImportMovieState?

    @Query(
        """
        SELECT episodes.local_episode_id, episode_watch_progress.watched_at
        FROM episodes
        LEFT JOIN episode_watch_progress USING(local_episode_id)
        WHERE episodes.source = :source AND episodes.external_id = :externalId
        LIMIT 1
        """
    )
    abstract suspend fun getEpisodeState(source: MediaSource, externalId: String): ImportEpisodeState?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertMovieProgress(progress: MovieWatchProgressEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertEpisodeProgress(progress: EpisodeWatchProgressEntity): Long

    suspend fun addMovieProgress(
        source: MediaSource,
        externalId: String,
        watchedAt: Instant
    ): ImportProgressWriteOutcome {
        val state = getMovieState(source, externalId)
            ?: return ImportProgressWriteOutcome.NOT_FOUND
        if (state.mediaType != MediaType.MOVIE) return ImportProgressWriteOutcome.NOT_FOUND
        if (state.watchedAt != null) {
            return if (state.watchedAt == watchedAt) {
                ImportProgressWriteOutcome.PRESERVED
            } else {
                ImportProgressWriteOutcome.CONFLICT
            }
        }
        insertMovieProgress(MovieWatchProgressEntity(state.localMediaId, watchedAt))
        return ImportProgressWriteOutcome.ADDED
    }

    suspend fun addEpisodeProgress(
        source: MediaSource,
        externalId: String,
        watchedAt: Instant
    ): ImportProgressWriteOutcome {
        val state = getEpisodeState(source, externalId)
            ?: return ImportProgressWriteOutcome.NOT_FOUND
        if (state.watchedAt != null) {
            return if (state.watchedAt == watchedAt) {
                ImportProgressWriteOutcome.PRESERVED
            } else {
                ImportProgressWriteOutcome.CONFLICT
            }
        }
        insertEpisodeProgress(EpisodeWatchProgressEntity(state.localEpisodeId, watchedAt))
        return ImportProgressWriteOutcome.ADDED
    }
}
