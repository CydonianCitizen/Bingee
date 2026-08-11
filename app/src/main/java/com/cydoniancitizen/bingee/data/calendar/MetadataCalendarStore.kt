package com.cydoniancitizen.bingee.data.calendar

import androidx.room.withTransaction
import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.MediaDetails
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.core.model.Season
import com.cydoniancitizen.bingee.data.details.toCacheWrite
import com.cydoniancitizen.bingee.data.library.local.BingeeDatabase
import com.cydoniancitizen.bingee.data.library.local.DetailsDao
import com.cydoniancitizen.bingee.data.library.local.ReleaseEventDao
import com.cydoniancitizen.bingee.data.library.local.SeasonSummaryStore
import com.cydoniancitizen.bingee.data.library.local.SeriesDao
import com.cydoniancitizen.bingee.data.series.toEntity
import com.cydoniancitizen.bingee.data.tmdb.series.TmdbSeasonPayload
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

internal interface MetadataCalendarStore {
    suspend fun storeDetails(
        reference: ExternalMediaRef,
        details: MediaDetails,
        seasons: List<Season>,
        fetchedAt: Instant
    )

    suspend fun storeSeason(seriesRef: ExternalMediaRef, payload: TmdbSeasonPayload, fetchedAt: Instant)
}

@Singleton
internal class RoomMetadataCalendarStore @Inject constructor(
    private val database: BingeeDatabase,
    private val detailsDao: DetailsDao,
    private val seasonSummaryStore: SeasonSummaryStore,
    private val seriesDao: SeriesDao,
    private val releaseEventDao: ReleaseEventDao,
    private val projector: ReleaseEventProjector
) : MetadataCalendarStore {
    override suspend fun storeDetails(
        reference: ExternalMediaRef,
        details: MediaDetails,
        seasons: List<Season>,
        fetchedAt: Instant
    ) {
        val write = details.toCacheWrite(fetchedAt)
        database.withTransaction {
            detailsDao.storeDetails(
                candidate = write.media,
                source = reference.source,
                externalId = reference.externalId,
                details = write.details,
                genres = write.genres
            )
            if (details.mediaType == MediaType.MOVIE) {
                releaseEventDao.reconcileMovie(reference, projector.movie(details, fetchedAt))
            } else {
                seasonSummaryStore.upsertSeasonSummaries(
                    source = reference.source,
                    seriesExternalId = reference.externalId,
                    summaries = seasons.map { it.toEntity(fetchedAt) }
                )
                seasons.forEach { season ->
                    releaseEventDao.reconcileSeason(season.externalRef, projector.season(season, fetchedAt))
                }
            }
        }
    }

    override suspend fun storeSeason(seriesRef: ExternalMediaRef, payload: TmdbSeasonPayload, fetchedAt: Instant) {
        database.withTransaction {
            val stored = seriesDao.storeSeasonEpisodes(
                source = seriesRef.source,
                seriesExternalId = seriesRef.externalId,
                season = payload.season.toEntity(fetchedAt),
                episodes = payload.episodes.map { it.toEntity(fetchedAt) },
                fetchedAt = fetchedAt
            )
            releaseEventDao.reconcileSeason(
                stored.season,
                projector.season(payload.season, fetchedAt)
            )
            val persistedEpisodes = stored.episodes.associateBy { it.externalId }
            payload.episodes.forEach { episode ->
                releaseEventDao.reconcileEpisode(
                    episode = checkNotNull(persistedEpisodes[episode.externalRef.externalId]),
                    localMediaId = stored.season.localMediaId,
                    event = projector.episode(episode, fetchedAt)
                )
            }
        }
    }
}
