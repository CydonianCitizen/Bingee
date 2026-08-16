package com.cydoniancitizen.bingee.data.tmdb.details

import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.Genre
import com.cydoniancitizen.bingee.core.model.MediaDetails
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.core.model.ProductionStatus
import com.cydoniancitizen.bingee.data.tmdb.TmdbImageUrlResolver
import java.time.Duration
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.Locale

internal object TmdbMovieDetailsMapper {
    fun map(dto: TmdbMovieDetailsDto): MediaDetails? {
        val id = dto.id?.takeIf { it > 0 } ?: return null
        val title = preferredTitle(dto.title, dto.originalTitle) ?: return null
        return MediaDetails(
            externalRef = ExternalMediaRef(MediaSource.TMDB, id.toString()),
            mediaType = MediaType.MOVIE,
            title = title,
            originalTitle = distinctOriginal(title, dto.originalTitle),
            overview = dto.overview.normalizedOptional(),
            posterUrl = TmdbImageUrlResolver.detailPoster(dto.posterPath),
            backdropUrl = TmdbImageUrlResolver.detailBackdrop(dto.backdropPath),
            releaseDate = parseDate(dto.releaseDate),
            productionStatus = mapStatus(dto.status),
            originalLanguage = dto.originalLanguage.normalizedOptional(),
            runtime = dto.runtime.positiveDuration(),
            genres = mapGenres(dto.genres)
        )
    }
}

internal object TmdbTvDetailsMapper {
    fun map(dto: TmdbTvDetailsDto): MediaDetails? {
        val id = dto.id?.takeIf { it > 0 } ?: return null
        val title = preferredTitle(dto.name, dto.originalName) ?: return null
        return MediaDetails(
            externalRef = ExternalMediaRef(MediaSource.TMDB, id.toString()),
            mediaType = MediaType.SERIES,
            title = title,
            originalTitle = distinctOriginal(title, dto.originalName),
            overview = dto.overview.normalizedOptional(),
            posterUrl = TmdbImageUrlResolver.detailPoster(dto.posterPath),
            backdropUrl = TmdbImageUrlResolver.detailBackdrop(dto.backdropPath),
            releaseDate = parseDate(dto.firstAirDate),
            productionStatus = mapStatus(dto.status),
            originalLanguage = dto.originalLanguage.normalizedOptional(),
            episodeRuntime = dto.episodeRunTime.orEmpty().firstOrNull { it > 0 }.positiveDuration(),
            numberOfSeasons = dto.numberOfSeasons.nonNegative(),
            numberOfEpisodes = dto.numberOfEpisodes.nonNegative(),
            genres = mapGenres(dto.genres)
        )
    }
}

private fun preferredTitle(localized: String?, original: String?): String? =
    localized.normalizedOptional() ?: original.normalizedOptional()

private fun distinctOriginal(title: String, original: String?): String? =
    original.normalizedOptional()?.takeUnless { it.equals(title, ignoreCase = true) }

private fun mapGenres(genres: List<TmdbGenreDto>?): List<Genre> = genres.orEmpty()
    .mapNotNull { genre ->
        val id = genre.id?.takeIf { it > 0 } ?: return@mapNotNull null
        val name = genre.name.normalizedOptional() ?: return@mapNotNull null
        Genre(name = name, source = MediaSource.TMDB, genreId = id)
    }
    .distinctBy { it.source to it.genreId }

private fun mapStatus(value: String?): ProductionStatus = when (value?.trim()?.lowercase(Locale.ROOT)) {
    "rumored" -> ProductionStatus.RUMORED
    "planned" -> ProductionStatus.PLANNED
    "in production" -> ProductionStatus.IN_PRODUCTION
    "post production" -> ProductionStatus.POST_PRODUCTION
    "released" -> ProductionStatus.RELEASED
    "returning series" -> ProductionStatus.RETURNING_SERIES
    "ended" -> ProductionStatus.ENDED
    "canceled", "cancelled" -> ProductionStatus.CANCELED
    "pilot" -> ProductionStatus.PILOT
    else -> ProductionStatus.UNKNOWN
}

private fun String?.normalizedOptional(): String? = this?.trim()?.takeIf(String::isNotEmpty)

private fun parseDate(value: String?): LocalDate? = try {
    value.normalizedOptional()?.let(LocalDate::parse)
} catch (_: DateTimeParseException) {
    null
}

private fun Int?.positiveDuration(): Duration? = this?.takeIf { it > 0 }?.let { Duration.ofMinutes(it.toLong()) }

private fun Int?.nonNegative(): Int? = this?.takeIf { it >= 0 }
