package com.cydoniancitizen.bingee.data.jikan.details

import com.cydoniancitizen.bingee.core.model.AnimeDetails
import com.cydoniancitizen.bingee.core.model.AnimeFormat
import com.cydoniancitizen.bingee.core.model.AnimeRelation
import com.cydoniancitizen.bingee.core.model.AnimeStatus
import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.data.jikan.jikanDate
import com.cydoniancitizen.bingee.data.jikan.normalizedJikanText
import java.util.Locale

internal object JikanDetailsMapper {
    fun map(response: JikanAnimeFullResponseDto): AnimeDetails {
        val dto = requireNotNull(response.data) { "Missing anime detail payload" }
        val id = dto.malId?.takeIf { it > 0 } ?: error("Missing anime identity")
        val title = dto.title.normalizedJikanText()
            ?: dto.titleEnglish.normalizedJikanText()
            ?: dto.titleJapanese.normalizedJikanText()
            ?: error("Missing anime title")
        return AnimeDetails(
            externalRef = ExternalMediaRef(MediaSource.JIKAN, id.toString()),
            title = title,
            englishTitle = dto.titleEnglish.normalizedJikanText(),
            japaneseTitle = dto.titleJapanese.normalizedJikanText(),
            synopsis = dto.synopsis.normalizedJikanText(stripHtml = true),
            posterUrl = dto.images?.jpg?.largeImageUrl.normalizedJikanText()
                ?: dto.images?.jpg?.imageUrl.normalizedJikanText(),
            format = mapFormat(dto.type),
            status = mapStatus(dto.status),
            episodeCount = dto.episodes?.takeIf { it >= 0 },
            duration = dto.duration.normalizedJikanText(),
            startDate = dto.aired?.from.jikanDate(),
            endDate = dto.aired?.to.jikanDate(),
            season = dto.season.normalizedJikanText(),
            year = dto.year?.takeIf { it in 1900..3000 },
            providerScore = dto.score?.takeIf { it in 0.0..10.0 && it != 0.0 },
            relations = dto.relations.orEmpty().flatMap(::mapRelation)
        )
    }

    internal fun mapFormat(value: String?): AnimeFormat = com.cydoniancitizen.bingee.domain.model.AnimeFormatClassifier.parseFormat(value)

    internal fun mapStatus(value: String?): AnimeStatus = when (value.key()) {
        "currently airing" -> AnimeStatus.AIRING
        "finished airing" -> AnimeStatus.FINISHED
        "not yet aired" -> AnimeStatus.UPCOMING
        else -> AnimeStatus.UNKNOWN
    }

    private fun mapRelation(dto: JikanRelationDto): List<AnimeRelation> {
        val relation = dto.relation.normalizedJikanText() ?: return emptyList()
        return dto.entry.orEmpty().mapNotNull { entry ->
            if (entry.type.key() != "anime") return@mapNotNull null
            val id = entry.malId?.takeIf { it > 0 } ?: return@mapNotNull null
            val name = entry.name.normalizedJikanText() ?: return@mapNotNull null
            AnimeRelation(relation, ExternalMediaRef(MediaSource.JIKAN, id.toString()), name)
        }
    }

    private val IMDB_TITLE_REGEX = Regex("""\b(tt\d+)\b""")

    internal fun extractImdbId(external: List<JikanExternalLinkDto>?): String? {
        if (external.isNullOrEmpty()) return null
        for (link in external) {
            val nameMatches = link.name?.contains("imdb", ignoreCase = true) == true
            val url = link.url ?: continue
            val match = IMDB_TITLE_REGEX.find(url)
            if (match != null) {
                if (nameMatches || url.contains("/title/")) {
                    return match.groupValues[1]
                }
            }
        }
        return null
    }

    private fun String?.key(): String = normalizedJikanText()?.lowercase(Locale.ROOT).orEmpty()
}
