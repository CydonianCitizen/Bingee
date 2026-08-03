package com.cydoniancitizen.bingee.data.series

import com.cydoniancitizen.bingee.core.model.CachedSeason
import com.cydoniancitizen.bingee.core.model.Episode
import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.Season
import com.cydoniancitizen.bingee.core.model.TrackedEpisode
import com.cydoniancitizen.bingee.core.model.deriveEpisodeWatchState
import com.cydoniancitizen.bingee.core.model.deriveSeasonProgress
import com.cydoniancitizen.bingee.data.library.local.EpisodeEntity
import com.cydoniancitizen.bingee.data.library.local.SeasonEntity
import com.cydoniancitizen.bingee.data.library.local.SeasonWithEpisodesRelation
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

internal fun Season.toEntity(updatedAt: Instant): SeasonEntity = SeasonEntity(
    localMediaId = 0,
    source = externalRef.source,
    externalId = externalRef.externalId,
    seasonNumber = seasonNumber,
    name = name.normalized(),
    overview = overview.normalized(),
    posterUrl = posterUrl.normalized(),
    airDate = airDate,
    episodeCount = episodeCount,
    metadataUpdatedAt = updatedAt,
    episodesFetchedAt = null
)

internal fun Episode.toEntity(updatedAt: Instant): EpisodeEntity = EpisodeEntity(
    localSeasonId = 0,
    source = externalRef.source,
    externalId = externalRef.externalId,
    episodeNumber = episodeNumber,
    title = title.trim(),
    overview = overview.normalized(),
    airDate = airDate,
    runtimeMinutes = runtime?.toMinutes()?.takeIf { it > 0 && it <= Int.MAX_VALUE }?.toInt(),
    stillUrl = stillUrl.normalized(),
    metadataUpdatedAt = updatedAt
)

internal fun SeasonWithEpisodesRelation.toDomain(
    seriesRef: ExternalMediaRef,
    today: LocalDate,
    freshnessPolicy: SeasonCacheFreshnessPolicy
): CachedSeason {
    require(season.source == seriesRef.source) { "Cached season provider differs from series provider" }
    val seasonRef = ExternalMediaRef(season.source, season.externalId)
    val domainSeason = Season(
        seriesRef = seriesRef,
        externalRef = seasonRef,
        seasonNumber = season.seasonNumber,
        name = season.name,
        overview = season.overview,
        posterUrl = season.posterUrl,
        airDate = season.airDate,
        episodeCount = season.episodeCount
    )
    val trackedEpisodes = episodes
        .sortedBy { it.episode.episodeNumber }
        .map { row ->
            val entity = row.episode
            val episode = Episode(
                seriesRef = seriesRef,
                seasonRef = seasonRef,
                externalRef = ExternalMediaRef(entity.source, entity.externalId),
                seasonNumber = season.seasonNumber,
                episodeNumber = entity.episodeNumber,
                title = entity.title,
                overview = entity.overview,
                airDate = entity.airDate,
                runtime = entity.runtimeMinutes?.let { Duration.ofMinutes(it.toLong()) },
                stillUrl = entity.stillUrl
            )
            TrackedEpisode(
                episode = episode,
                watchState = deriveEpisodeWatchState(episode, row.progress?.watchedAt, today)
            )
        }
    return CachedSeason(
        season = domainSeason,
        metadataUpdatedAt = season.metadataUpdatedAt,
        episodesFetchedAt = season.episodesFetchedAt,
        episodes = trackedEpisodes,
        progress = deriveSeasonProgress(trackedEpisodes),
        episodeCacheFreshness = season.episodesFetchedAt?.let(freshnessPolicy::classify)
    )
}

private fun String?.normalized(): String? = this?.trim()?.takeIf(String::isNotEmpty)
