package com.cydoniancitizen.bingee.data.details

import com.cydoniancitizen.bingee.core.model.CachedMediaDetails
import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.Genre
import com.cydoniancitizen.bingee.core.model.MediaDetails
import com.cydoniancitizen.bingee.core.model.ProductionStatus
import com.cydoniancitizen.bingee.data.library.local.CachedDetailsRelation
import com.cydoniancitizen.bingee.data.library.local.MediaDetailsEntity
import com.cydoniancitizen.bingee.data.library.local.MediaEntity
import com.cydoniancitizen.bingee.data.library.local.MediaGenreEntity
import java.time.Duration
import java.time.Instant

internal data class DetailsCacheWrite(
    val media: MediaEntity,
    val details: MediaDetailsEntity,
    val genres: List<MediaGenreEntity>
)

internal fun MediaDetails.toCacheWrite(fetchedAt: Instant): DetailsCacheWrite = DetailsCacheWrite(
    media = MediaEntity(
        mediaType = mediaType,
        title = title.trim(),
        originalTitle = originalTitle.normalizedOptional(),
        overview = overview.normalizedOptional(),
        posterUrl = posterUrl.normalizedOptional(),
        releaseDate = releaseDate,
        createdAt = fetchedAt,
        metadataUpdatedAt = fetchedAt
    ),
    details = MediaDetailsEntity(
        localMediaId = 0,
        backdropUrl = backdropUrl.normalizedOptional(),
        productionStatus = productionStatus.name,
        originalLanguage = originalLanguage.normalizedOptional(),
        runtimeMinutes = runtime.minutesOrNull(),
        episodeRuntimeMinutes = episodeRuntime.minutesOrNull(),
        numberOfSeasons = numberOfSeasons,
        numberOfEpisodes = numberOfEpisodes,
        detailsFetchedAt = fetchedAt
    ),
    genres = genres.mapIndexed { index, genre ->
        MediaGenreEntity(
            localMediaId = 0,
            genreOrder = index,
            name = genre.name.trim(),
            source = genre.source,
            genreId = genre.genreId
        )
    }
)

internal fun CachedDetailsRelation.toDomain(
    reference: ExternalMediaRef,
    freshnessPolicy: CacheFreshnessPolicy
): CachedMediaDetails? {
    val cached = details ?: return null
    require(externalRefs.any { it.source == reference.source && it.externalId == reference.externalId }) {
        "Cached details do not own requested external reference"
    }
    val status = ProductionStatus.entries.firstOrNull { it.name == cached.productionStatus }
        ?: ProductionStatus.UNKNOWN
    val domain = MediaDetails(
        externalRef = reference,
        mediaType = media.mediaType,
        title = media.title,
        originalTitle = media.originalTitle,
        overview = media.overview,
        posterUrl = media.posterUrl,
        backdropUrl = cached.backdropUrl,
        releaseDate = media.releaseDate,
        productionStatus = status,
        originalLanguage = cached.originalLanguage,
        runtime = cached.runtimeMinutes.positiveDuration(),
        episodeRuntime = cached.episodeRuntimeMinutes.positiveDuration(),
        numberOfSeasons = cached.numberOfSeasons.nonNegative(),
        numberOfEpisodes = cached.numberOfEpisodes.nonNegative(),
        genres = cachedGenres()
    )
    return CachedMediaDetails(
        details = domain,
        fetchedAt = cached.detailsFetchedAt,
        freshness = freshnessPolicy.classify(cached.detailsFetchedAt)
    )
}

private fun CachedDetailsRelation.cachedGenres(): List<Genre> = genres
    .sortedBy(MediaGenreEntity::genreOrder)
    .mapNotNull { row ->
        row.name.normalizedOptional()?.let { name ->
            Genre(name = name, source = row.source, genreId = row.genreId)
        }
    }

private fun String?.normalizedOptional(): String? = this?.trim()?.takeIf(String::isNotEmpty)

private fun Duration?.minutesOrNull(): Int? = this?.toMinutes()?.takeIf { it > 0 && it <= Int.MAX_VALUE }?.toInt()

private fun Int?.positiveDuration(): Duration? = this?.takeIf { it > 0 }?.let { Duration.ofMinutes(it.toLong()) }

private fun Int?.nonNegative(): Int? = this?.takeIf { it >= 0 }
