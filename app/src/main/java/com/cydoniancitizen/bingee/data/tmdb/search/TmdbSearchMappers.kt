package com.cydoniancitizen.bingee.data.tmdb.search

import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.MediaSearchPage
import com.cydoniancitizen.bingee.core.model.MediaSearchQuery
import com.cydoniancitizen.bingee.core.model.MediaSearchResult
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import java.time.LocalDate
import java.time.format.DateTimeParseException

internal object TmdbPosterUrlResolver {
    const val LIST_SIZE = "w342"
    private const val BASE_URL = "https://image.tmdb.org/t/p/"
    private val SUPPORTED_PATH = Regex("/[A-Za-z0-9._-]+\\.(?:jpg|jpeg|png|webp)", RegexOption.IGNORE_CASE)

    fun resolve(path: String?): String? {
        val normalized = path?.trim()?.takeIf(SUPPORTED_PATH::matches) ?: return null
        return "$BASE_URL$LIST_SIZE$normalized"
    }
}

internal object TmdbMovieSearchMapper {
    fun map(response: TmdbMovieSearchResponseDto, requestedPage: Int): MediaSearchPage = page(
        requestedPage = requestedPage,
        providerPage = response.page,
        providerTotalPages = response.totalPages,
        providerTotalResults = response.totalResults,
        results = response.results.orEmpty().mapNotNull(::mapResult)
    )

    fun mapResult(dto: TmdbMovieSearchResultDto): MediaSearchResult? {
        val id = dto.id?.takeIf { it > 0 } ?: return null
        val title = preferredTitle(dto.title, dto.originalTitle) ?: return null
        return MediaSearchResult(
            externalRef = ExternalMediaRef(MediaSource.TMDB, id.toString()),
            mediaType = MediaType.MOVIE,
            title = title,
            originalTitle = distinctOriginal(title, dto.originalTitle),
            posterUrl = TmdbPosterUrlResolver.resolve(dto.posterPath),
            releaseDate = parseDate(dto.releaseDate),
            overview = dto.overview.normalizedOptional()
        )
    }
}

internal object TmdbTvSearchMapper {
    fun map(response: TmdbTvSearchResponseDto, requestedPage: Int): MediaSearchPage = page(
        requestedPage = requestedPage,
        providerPage = response.page,
        providerTotalPages = response.totalPages,
        providerTotalResults = response.totalResults,
        results = response.results.orEmpty().mapNotNull(::mapResult)
    )

    fun mapResult(dto: TmdbTvSearchResultDto): MediaSearchResult? {
        val id = dto.id?.takeIf { it > 0 } ?: return null
        val title = preferredTitle(dto.name, dto.originalName) ?: return null
        return MediaSearchResult(
            externalRef = ExternalMediaRef(MediaSource.TMDB, id.toString()),
            mediaType = MediaType.SERIES,
            title = title,
            originalTitle = distinctOriginal(title, dto.originalName),
            posterUrl = TmdbPosterUrlResolver.resolve(dto.posterPath),
            releaseDate = parseDate(dto.firstAirDate),
            overview = dto.overview.normalizedOptional()
        )
    }
}

private fun page(
    requestedPage: Int,
    providerPage: Int?,
    providerTotalPages: Int?,
    providerTotalResults: Int?,
    results: List<MediaSearchResult>
): MediaSearchPage {
    val currentPage = providerPage?.takeIf { it in 1..MediaSearchQuery.MAX_PAGE } ?: requestedPage
    val totalPages = providerTotalPages
        ?.coerceIn(currentPage, MediaSearchQuery.MAX_PAGE)
        ?: currentPage
    return MediaSearchPage(
        results = results,
        page = currentPage,
        totalPages = totalPages,
        totalResults = providerTotalResults?.coerceAtLeast(0) ?: results.size
    )
}

private fun preferredTitle(localized: String?, original: String?): String? =
    localized.normalizedOptional() ?: original.normalizedOptional()

private fun distinctOriginal(title: String, original: String?): String? =
    original.normalizedOptional()?.takeUnless { it.equals(title, ignoreCase = true) }

private fun String?.normalizedOptional(): String? = this?.trim()?.takeIf(String::isNotEmpty)

private fun parseDate(value: String?): LocalDate? {
    val normalized = value.normalizedOptional() ?: return null
    return try {
        LocalDate.parse(normalized)
    } catch (_: DateTimeParseException) {
        null
    }
}
