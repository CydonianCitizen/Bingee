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
import kotlinx.coroutines.flow.Flow

@Dao
internal abstract class SeriesDao : SeasonSummaryStore {
    @Transaction
    @Query(
        """
        SELECT seasons.*
        FROM seasons
        INNER JOIN media_entries USING(local_media_id)
        INNER JOIN external_refs USING(local_media_id)
        WHERE external_refs.source = :source AND external_refs.external_id = :seriesExternalId
        ORDER BY seasons.season_number
        """
    )
    abstract fun observeSeriesSeasons(
        source: MediaSource,
        seriesExternalId: String
    ): Flow<List<SeasonWithEpisodesRelation>>

    @Transaction
    @Query(
        """
        SELECT seasons.* FROM seasons
        INNER JOIN external_refs USING(local_media_id)
        WHERE external_refs.source = :source
          AND external_refs.external_id = :seriesExternalId
          AND seasons.source = :source
          AND seasons.external_id = :seasonExternalId
        LIMIT 1
        """
    )
    abstract fun observeSeason(
        source: MediaSource,
        seriesExternalId: String,
        seasonExternalId: String
    ): Flow<SeasonWithEpisodesRelation?>

    @Query(
        """
        SELECT * FROM seasons
        WHERE source = :source AND external_id = :seasonExternalId
        LIMIT 1
        """
    )
    abstract suspend fun getSeason(source: MediaSource, seasonExternalId: String): SeasonEntity?

    @Query(
        """
        SELECT seasons.*
        FROM seasons
        INNER JOIN external_refs USING(local_media_id)
        WHERE external_refs.source = :source
          AND external_refs.external_id = :seriesExternalId
          AND seasons.season_number = :seasonNumber
        LIMIT 1
        """
    )
    abstract suspend fun getSeasonForSeries(
        source: MediaSource,
        seriesExternalId: String,
        seasonNumber: Int
    ): SeasonEntity?

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
        SELECT * FROM seasons
        WHERE local_media_id = :localMediaId AND season_number = :seasonNumber
        LIMIT 1
        """
    )
    protected abstract suspend fun getSeasonByNumber(localMediaId: Long, seasonNumber: Int): SeasonEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertSeason(season: SeasonEntity): Long

    @Update(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun updateSeason(season: SeasonEntity)

    @Query(
        """
        SELECT * FROM episodes
        WHERE source = :source AND external_id = :externalId
        LIMIT 1
        """
    )
    protected abstract suspend fun getEpisode(source: MediaSource, externalId: String): EpisodeEntity?

    suspend fun getEpisodeForImport(source: MediaSource, externalId: String): EpisodeEntity? =
        getEpisode(source, externalId)

    @Query(
        """
        SELECT * FROM episodes
        WHERE local_season_id = :localSeasonId AND episode_number = :episodeNumber
        LIMIT 1
        """
    )
    protected abstract suspend fun getEpisodeByNumber(localSeasonId: Long, episodeNumber: Int): EpisodeEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertEpisode(episode: EpisodeEntity): Long

    @Update(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun updateEpisode(episode: EpisodeEntity)

    @Query(
        """
        UPDATE seasons
        SET episodes_fetched_at = :fetchedAt
        WHERE local_season_id = :localSeasonId
        """
    )
    protected abstract suspend fun updateEpisodesFetchedAt(localSeasonId: Long, fetchedAt: Instant)

    @Transaction
    open override suspend fun upsertSeasonSummaries(
        source: MediaSource,
        seriesExternalId: String,
        summaries: List<SeasonEntity>
    ) {
        val media = checkNotNull(getMedia(source, seriesExternalId)) { "Series metadata must exist before seasons" }
        check(media.mediaType == MediaType.SERIES) { "Season metadata requires a TV series" }
        summaries.forEach { upsertSeason(media.localMediaId, it) }
    }

    @Transaction
    open suspend fun storeSeasonEpisodes(
        source: MediaSource,
        seriesExternalId: String,
        season: SeasonEntity,
        episodes: List<EpisodeEntity>,
        fetchedAt: Instant
    ) {
        val media = checkNotNull(getMedia(source, seriesExternalId)) { "Series metadata is missing" }
        check(media.mediaType == MediaType.SERIES) { "Episode metadata requires a TV series" }
        val storedSeason = upsertSeason(media.localMediaId, season)
        episodes.forEach { candidate ->
            check(candidate.source == storedSeason.source) { "Episode provider differs from season provider" }
            val byRef = getEpisode(candidate.source, candidate.externalId)
            val byNumber = getEpisodeByNumber(storedSeason.localSeasonId, candidate.episodeNumber)
            check(byRef == null || byRef.localSeasonId == storedSeason.localSeasonId) {
                "Episode provider identity belongs to another season"
            }
            check(byRef == null || byNumber == null || byRef.localEpisodeId == byNumber.localEpisodeId) {
                "Episode identity conflicts with its season number"
            }
            val existing = byRef ?: byNumber
            if (existing == null) {
                insertEpisode(candidate.copy(localEpisodeId = 0, localSeasonId = storedSeason.localSeasonId))
            } else {
                updateEpisode(
                    candidate.copy(
                        localEpisodeId = existing.localEpisodeId,
                        localSeasonId = storedSeason.localSeasonId
                    )
                )
            }
        }
        updateEpisodesFetchedAt(storedSeason.localSeasonId, fetchedAt)
    }

    private suspend fun upsertSeason(localMediaId: Long, candidate: SeasonEntity): SeasonEntity {
        check(candidate.externalId.isNotBlank())
        val byRef = getSeason(candidate.source, candidate.externalId)
        val byNumber = getSeasonByNumber(localMediaId, candidate.seasonNumber)
        check(byRef == null || byRef.localMediaId == localMediaId) {
            "Season provider identity belongs to another series"
        }
        check(byRef == null || byNumber == null || byRef.localSeasonId == byNumber.localSeasonId) {
            "Season identity conflicts with its series number"
        }
        val existing = byRef ?: byNumber
        return if (existing == null) {
            val id = insertSeason(candidate.copy(localSeasonId = 0, localMediaId = localMediaId))
            candidate.copy(localSeasonId = id, localMediaId = localMediaId)
        } else {
            val updated = candidate.copy(
                localSeasonId = existing.localSeasonId,
                localMediaId = localMediaId,
                episodesFetchedAt = candidate.episodesFetchedAt ?: existing.episodesFetchedAt
            )
            updateSeason(updated)
            updated
        }
    }
}

internal interface SeasonSummaryStore {
    suspend fun upsertSeasonSummaries(source: MediaSource, seriesExternalId: String, summaries: List<SeasonEntity>)
}
