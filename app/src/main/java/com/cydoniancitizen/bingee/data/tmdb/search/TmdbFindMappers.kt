package com.cydoniancitizen.bingee.data.tmdb.search

import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.MediaSearchResult
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.data.tmdb.TmdbImageUrlResolver
import java.time.LocalDate
import java.time.format.DateTimeParseException

internal data class TmdbExternalIdMatches(
    val movies: List<MediaSearchResult>,
    val series: List<MediaSearchResult>,
    val episodes: List<TmdbExternalEpisode>
)

internal data class TmdbExternalEpisode(
    val externalRef: ExternalMediaRef,
    val seriesRef: ExternalMediaRef?,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
    val title: String,
    val airDate: LocalDate?
)

internal object TmdbFindMapper {
    fun map(dto: TmdbFindResponseDto): TmdbExternalIdMatches = TmdbExternalIdMatches(
        movies = dto.movieResults.orEmpty().mapNotNull { movie ->
            val id = movie.id?.takeIf { it > 0 } ?: return@mapNotNull null
            val title = movie.title?.trim()?.takeIf(String::isNotEmpty) ?: return@mapNotNull null
            MediaSearchResult(
                externalRef = ExternalMediaRef(MediaSource.TMDB, id.toString()),
                mediaType = MediaType.MOVIE,
                title = title,
                originalTitle = movie.originalTitle?.trim()?.takeIf { it.isNotEmpty() },
                posterUrl = TmdbImageUrlResolver.listPoster(movie.posterPath),
                releaseDate = parseDate(movie.releaseDate),
                overview = movie.overview?.trim()?.takeIf { it.isNotEmpty() }
            )
        },
        series = dto.tvResults.orEmpty().mapNotNull { tv ->
            val id = tv.id?.takeIf { it > 0 } ?: return@mapNotNull null
            val title = tv.name?.trim()?.takeIf(String::isNotEmpty) ?: return@mapNotNull null
            MediaSearchResult(
                externalRef = ExternalMediaRef(MediaSource.TMDB, id.toString()),
                mediaType = MediaType.SERIES,
                title = title,
                originalTitle = tv.originalName?.trim()?.takeIf { it.isNotEmpty() },
                posterUrl = TmdbImageUrlResolver.listPoster(tv.posterPath),
                releaseDate = parseDate(tv.firstAirDate),
                overview = tv.overview?.trim()?.takeIf { it.isNotEmpty() }
            )
        },
        episodes = dto.episodeResults.orEmpty().mapNotNull { episode ->
            val id = episode.id?.takeIf { it > 0 } ?: return@mapNotNull null
            val title = episode.name?.trim()?.takeIf(String::isNotEmpty) ?: return@mapNotNull null
            TmdbExternalEpisode(
                externalRef = ExternalMediaRef(MediaSource.TMDB, id.toString()),
                seriesRef = episode.showId?.takeIf { it > 0 }?.let {
                    ExternalMediaRef(MediaSource.TMDB, it.toString())
                },
                seasonNumber = episode.seasonNumber,
                episodeNumber = episode.episodeNumber,
                title = title,
                airDate = parseDate(episode.airDate)
            )
        }
    )

    private fun parseDate(value: String?): LocalDate? = try {
        value?.trim()?.takeIf { it.isNotEmpty() }?.let(LocalDate::parse)
    } catch (_: DateTimeParseException) {
        null
    }
}
