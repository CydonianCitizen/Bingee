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

internal data class StoredSeasonEpisodes(val season: SeasonEntity, val episodes: List<EpisodeEntity>)

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

    @Query("SELECT * FROM episodes WHERE local_season_id = :localSeasonId")
    protected abstract suspend fun getEpisodesForSeason(localSeasonId: Long): List<EpisodeEntity>

    @Query(
        """
        SELECT * FROM episodes
        WHERE source = :source AND external_id IN (:externalIds)
        """
    )
    protected abstract suspend fun getEpisodesByExternalIds(
        source: MediaSource,
        externalIds: List<String>
    ): List<EpisodeEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertEpisodes(episodes: List<EpisodeEntity>): List<Long>

    @Update(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun updateEpisodes(episodes: List<EpisodeEntity>)

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
    ): StoredSeasonEpisodes {
        val media = checkNotNull(getMedia(source, seriesExternalId)) { "Series metadata is missing" }
        check(media.mediaType == MediaType.SERIES) { "Episode metadata requires a TV series" }
        val storedSeason = upsertSeason(media.localMediaId, season)
        episodes.forEach { candidate ->
            check(candidate.source == storedSeason.source) { "Episode provider differs from season provider" }
        }

        val storedEpisodes = getEpisodesForSeason(storedSeason.localSeasonId)
        val referencedEpisodes = episodes
            .map { it.externalId }
            .distinct()
            .takeIf { it.isNotEmpty() }
            ?.let { getEpisodesByExternalIds(storedSeason.source, it) }
            .orEmpty()
        val statesById = mutableMapOf<Long, EpisodeState>()
        (storedEpisodes + referencedEpisodes).forEach { episode ->
            statesById.getOrPut(episode.localEpisodeId) { EpisodeState(episode) }
        }
        val byRef = statesById.values.associateBy { it.episode.externalId }.toMutableMap()
        val byNumber = storedEpisodes
            .map { statesById.getValue(it.localEpisodeId) }
            .associateBy { it.episode.episodeNumber }
            .toMutableMap()
        val candidateStates = linkedSetOf<EpisodeState>()
        val newStates = linkedSetOf<EpisodeState>()
        val updatesById = linkedMapOf<Long, EpisodeEntity>()

        episodes.forEach { candidate ->
            val byRefState = byRef[candidate.externalId]
            val byNumberState = byNumber[candidate.episodeNumber]
            check(byRefState == null || byRefState.episode.localSeasonId == storedSeason.localSeasonId) {
                "Episode provider identity belongs to another season"
            }
            check(byRefState == null || byNumberState == null || sameEpisode(byRefState, byNumberState)) {
                "Episode identity conflicts with its season number"
            }
            val state = byRefState ?: byNumberState ?: EpisodeState(
                candidate.copy(localSeasonId = storedSeason.localSeasonId),
                localEpisodeId = null
            )
            candidateStates += state
            byRef.entries.removeIf { it.value === state }
            byNumber.entries.removeIf { it.value === state }
            state.episode = candidate.copy(
                localEpisodeId = state.localEpisodeId ?: 0,
                localSeasonId = storedSeason.localSeasonId
            )
            if (state.localEpisodeId == null) {
                newStates += state
            } else {
                updatesById[checkNotNull(state.localEpisodeId)] = state.episode
            }
            byRef[candidate.externalId] = state
            byNumber[candidate.episodeNumber] = state
        }

        if (updatesById.isNotEmpty()) updateEpisodes(updatesById.values.toList())
        if (newStates.isNotEmpty()) {
            val ids = insertEpisodes(newStates.map { it.episode })
            newStates.forEachIndexed { index, state ->
                state.localEpisodeId = ids[index]
                state.episode = state.episode.copy(localEpisodeId = ids[index])
            }
        }
        updateEpisodesFetchedAt(storedSeason.localSeasonId, fetchedAt)
        return StoredSeasonEpisodes(
            season = storedSeason.copy(episodesFetchedAt = fetchedAt),
            episodes = candidateStates.map { it.episode }
        )
    }

    private class EpisodeState(var episode: EpisodeEntity, var localEpisodeId: Long? = episode.localEpisodeId)

    private fun sameEpisode(first: EpisodeState, second: EpisodeState): Boolean = first === second || (
        first.localEpisodeId != null &&
            first.localEpisodeId == second.localEpisodeId
        )

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
