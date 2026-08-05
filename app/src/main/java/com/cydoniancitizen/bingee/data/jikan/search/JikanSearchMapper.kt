package com.cydoniancitizen.bingee.data.jikan.search

import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.MediaSearchPage
import com.cydoniancitizen.bingee.core.model.MediaSearchQuery
import com.cydoniancitizen.bingee.core.model.MediaSearchResult
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import java.time.LocalDate

internal object JikanSearchMapper {
    fun map(response: JikanAnimeSearchResponseDto, requestedPage: Int): MediaSearchPage {
        val results = response.data.orEmpty().mapNotNull(::mapResult)
        val reportedLast = response.pagination?.lastVisiblePage
            ?.coerceIn(requestedPage, MediaSearchQuery.MAX_PAGE)
        val totalPages = when {
            reportedLast != null -> reportedLast
            response.pagination?.hasNextPage == true -> (requestedPage + 1).coerceAtMost(MediaSearchQuery.MAX_PAGE)
            else -> requestedPage
        }
        return MediaSearchPage(results, requestedPage, totalPages, results.size)
    }

    fun mapResult(dto: JikanAnimeSearchResultDto): MediaSearchResult? {
        val id = dto.malId?.takeIf { it > 0 } ?: return null
        val title =
            dto.title.normalized() ?: dto.titleEnglish.normalized() ?: dto.titleJapanese.normalized() ?: return null
        val original = listOf(dto.titleJapanese, dto.titleEnglish)
            .mapNotNull { it.normalized() }
            .firstOrNull { !it.equals(title, ignoreCase = true) }
        return MediaSearchResult(
            externalRef = ExternalMediaRef(MediaSource.JIKAN, id.toString()),
            mediaType = MediaType.ANIME,
            title = title,
            originalTitle = original,
            posterUrl = dto.images?.jpg?.largeImageUrl.normalized() ?: dto.images?.jpg?.imageUrl.normalized(),
            releaseDate = dto.aired?.from.normalized()?.take(10)?.let(::parseDate),
            overview = dto.synopsis.normalized()?.replace(HTML_TAGS, " ")?.replace(WHITESPACE, " ")?.trim()
        )
    }

    private fun String?.normalized(): String? = this?.trim()?.takeIf(String::isNotEmpty)

    private fun parseDate(value: String): LocalDate? = runCatching { LocalDate.parse(value) }.getOrNull()

    private val HTML_TAGS = Regex("<[^>]*>")
    private val WHITESPACE = Regex("\\s+")
}
