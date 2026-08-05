package com.cydoniancitizen.bingee.data.jikan.details

import com.cydoniancitizen.bingee.core.model.AnimeDetails
import com.cydoniancitizen.bingee.core.model.AnimeRelation
import com.cydoniancitizen.bingee.core.model.AnimeWatchProgress
import com.cydoniancitizen.bingee.core.model.CacheFreshness
import com.cydoniancitizen.bingee.core.model.CachedAnimeDetails
import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.data.details.CacheFreshnessPolicy
import com.cydoniancitizen.bingee.data.library.local.AnimeDetailsEntity
import com.cydoniancitizen.bingee.data.library.local.AnimeProgressEntity
import com.cydoniancitizen.bingee.data.library.local.AnimeRelationEntity
import com.cydoniancitizen.bingee.data.library.local.CachedAnimeRelation
import com.cydoniancitizen.bingee.data.library.local.MediaEntity
import java.time.Instant

internal fun CachedAnimeRelation.toCachedDomain(
    reference: ExternalMediaRef,
    freshnessPolicy: CacheFreshnessPolicy
): CachedAnimeDetails? {
    val row = details ?: return null
    check(media.mediaType == MediaType.ANIME)
    check(externalRefs.any { it.source == MediaSource.JIKAN && it.externalId == reference.externalId })
    return CachedAnimeDetails(
        details = AnimeDetails(
            externalRef = reference,
            title = media.title,
            englishTitle = row.englishTitle,
            japaneseTitle = row.japaneseTitle,
            synopsis = row.synopsis,
            posterUrl = row.imageUrl,
            format = row.format,
            status = row.providerStatus,
            episodeCount = row.episodeCount,
            duration = row.duration,
            startDate = row.startDate,
            endDate = row.endDate,
            season = row.season,
            year = row.year,
            providerScore = row.providerScore,
            relations = relations.map {
                AnimeRelation(
                    relation = it.relationType,
                    animeRef = ExternalMediaRef(MediaSource.JIKAN, it.relatedJikanId),
                    title = it.relatedTitle,
                    format = it.relatedFormat
                )
            }
        ),
        fetchedAt = row.detailsUpdatedAt,
        freshness = freshnessPolicy.classify(row.detailsUpdatedAt)
    )
}

internal fun AnimeDetails.toCacheWrite(now: Instant): AnimeCacheWrite = AnimeCacheWrite(
    media = MediaEntity(
        mediaType = MediaType.ANIME,
        title = title,
        originalTitle = japaneseTitle ?: englishTitle,
        overview = synopsis,
        posterUrl = posterUrl,
        releaseDate = startDate,
        createdAt = now,
        metadataUpdatedAt = now
    ),
    details = AnimeDetailsEntity(
        localMediaId = 0,
        format = format,
        providerStatus = status,
        englishTitle = englishTitle,
        japaneseTitle = japaneseTitle,
        synopsis = synopsis,
        episodeCount = episodeCount,
        duration = duration,
        startDate = startDate,
        endDate = endDate,
        season = season,
        year = year,
        providerScore = providerScore,
        imageUrl = posterUrl,
        detailsUpdatedAt = now
    ),
    relations = relations.map {
        AnimeRelationEntity(
            localMediaId = 0,
            relationType = it.relation,
            relatedJikanId = it.animeRef.externalId,
            relatedTitle = it.title,
            relatedFormat = it.format
        )
    }
)

internal data class AnimeCacheWrite(
    val media: MediaEntity,
    val details: AnimeDetailsEntity,
    val relations: List<AnimeRelationEntity>
)

internal fun AnimeProgressEntity.toDomain(): AnimeWatchProgress = AnimeWatchProgress(
    watchedEpisodes = watchedEpisodeCount,
    completedAt = completedAt,
    completionOrigin = completionOrigin,
    updatedAt = updatedAt
)
