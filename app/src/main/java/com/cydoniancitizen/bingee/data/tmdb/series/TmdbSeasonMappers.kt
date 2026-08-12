package com.cydoniancitizen.bingee.data.tmdb.series

import com.cydoniancitizen.bingee.core.model.Episode
import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.Season
import com.cydoniancitizen.bingee.data.tmdb.TmdbImageUrlResolver
import com.cydoniancitizen.bingee.data.tmdb.details.TmdbSeasonSummaryDto
import java.time.Duration
import java.time.LocalDate
import java.time.format.DateTimeParseException

internal data class TmdbSeasonPayload(val season: Season, val episodes: List<Episode>)

internal object TmdbSeasonSummaryMapper {
    fun mapAll(seriesRef: ExternalMediaRef, rows: List<TmdbSeasonSummaryDto>?): List<Season> {
        val seenIds = mutableSetOf<ExternalMediaRef>()
        val seenNumbers = mutableSetOf<Int>()
        return rows.orEmpty()
            .mapNotNull { map(seriesRef, it) }
            .sortedBy(Season::seasonNumber)
            .filter { seenIds.add(it.externalRef) && seenNumbers.add(it.seasonNumber) }
    }

    fun map(seriesRef: ExternalMediaRef, dto: TmdbSeasonSummaryDto): Season? {
        val id = dto.id?.takeIf { it > 0 } ?: return null
        val number = dto.seasonNumber?.takeIf { it >= 0 } ?: return null
        return Season(
            seriesRef = seriesRef,
            externalRef = ExternalMediaRef(MediaSource.TMDB, id.toString()),
            seasonNumber = number,
            name = dto.name.normalized(),
            overview = dto.overview.normalized(),
            posterUrl = TmdbImageUrlResolver.detailPoster(dto.posterPath),
            airDate = parseDate(dto.airDate),
            episodeCount = dto.episodeCount?.coerceAtLeast(0) ?: 0
        )
    }
}

internal object TmdbSeasonDetailsMapper {
    fun map(seriesRef: ExternalMediaRef, requestedSeasonNumber: Int, dto: TmdbSeasonDetailsDto): TmdbSeasonPayload? {
        if (requestedSeasonNumber < 0) return null
        val seasonId = dto.id?.takeIf { it > 0 } ?: return null
        val responseNumber = dto.seasonNumber?.takeIf { it >= 0 } ?: requestedSeasonNumber
        if (responseNumber != requestedSeasonNumber) return null
        val seasonRef = ExternalMediaRef(MediaSource.TMDB, seasonId.toString())
        val episodes = mapEpisodes(seriesRef, seasonRef, requestedSeasonNumber, dto.episodes)
        return TmdbSeasonPayload(
            season = Season(
                seriesRef = seriesRef,
                externalRef = seasonRef,
                seasonNumber = requestedSeasonNumber,
                name = dto.name.normalized(),
                overview = dto.overview.normalized(),
                posterUrl = TmdbImageUrlResolver.detailPoster(dto.posterPath),
                airDate = parseDate(dto.airDate),
                episodeCount = dto.episodes.orEmpty().size.coerceAtLeast(episodes.size)
            ),
            episodes = episodes
        )
    }

    private fun mapEpisodes(
        seriesRef: ExternalMediaRef,
        seasonRef: ExternalMediaRef,
        seasonNumber: Int,
        rows: List<TmdbEpisodeDto>?
    ): List<Episode> {
        val seenIds = mutableSetOf<ExternalMediaRef>()
        val seenNumbers = mutableSetOf<Int>()
        return rows.orEmpty()
            .mapNotNull { mapEpisode(seriesRef, seasonRef, seasonNumber, it) }
            .sortedBy(Episode::episodeNumber)
            .filter { seenIds.add(it.externalRef) && seenNumbers.add(it.episodeNumber) }
    }

    private fun mapEpisode(
        seriesRef: ExternalMediaRef,
        seasonRef: ExternalMediaRef,
        seasonNumber: Int,
        dto: TmdbEpisodeDto
    ): Episode? {
        val id = dto.id?.takeIf { it > 0 } ?: return null
        if (dto.seasonNumber != null && dto.seasonNumber != seasonNumber) return null
        val number = dto.episodeNumber?.takeIf { it > 0 } ?: return null
        val title = dto.name.normalized() ?: return null
        return Episode(
            seriesRef = seriesRef,
            seasonRef = seasonRef,
            externalRef = ExternalMediaRef(MediaSource.TMDB, id.toString()),
            seasonNumber = seasonNumber,
            episodeNumber = number,
            title = title,
            overview = dto.overview.normalized(),
            airDate = parseDate(dto.airDate),
            runtime = dto.runtime?.takeIf { it > 0 }?.let { Duration.ofMinutes(it.toLong()) },
            stillUrl = TmdbImageUrlResolver.episodeStill(dto.stillPath)
        )
    }
}

private fun String?.normalized(): String? = this?.trim()?.takeIf(String::isNotEmpty)

private fun parseDate(value: String?): LocalDate? = try {
    value.normalized()?.let(LocalDate::parse)
} catch (_: DateTimeParseException) {
    null
}
