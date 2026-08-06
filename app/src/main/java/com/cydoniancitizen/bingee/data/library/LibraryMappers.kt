package com.cydoniancitizen.bingee.data.library

import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.LibraryEntry
import com.cydoniancitizen.bingee.core.model.LibraryProgress
import com.cydoniancitizen.bingee.core.model.MediaSearchResult
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.core.model.MovieWatchState
import com.cydoniancitizen.bingee.core.model.PersonalRating
import com.cydoniancitizen.bingee.core.model.SeriesProgress
import com.cydoniancitizen.bingee.data.library.local.ExternalRefEntity
import com.cydoniancitizen.bingee.data.library.local.LibraryDao
import com.cydoniancitizen.bingee.data.library.local.LibraryItemWithRefs
import com.cydoniancitizen.bingee.data.library.local.MediaEntity
import com.cydoniancitizen.bingee.data.library.local.MediaRatingEntity
import java.time.Instant

internal fun MediaSearchResult.toMediaEntity(now: Instant): MediaEntity = MediaEntity(
    mediaType = mediaType,
    title = title.trim().also { require(it.isNotEmpty()) { "Media title must not be blank" } },
    originalTitle = originalTitle.normalizedOptionalText(),
    overview = overview.normalizedOptionalText(),
    posterUrl = posterUrl.normalizedOptionalText(),
    releaseDate = releaseDate,
    createdAt = now,
    metadataUpdatedAt = now
)

internal fun LibraryItemWithRefs.toDomain(
    preferredRef: ExternalMediaRef? = null,
    progressRow: LibraryDao.LibraryProgressRow? = null,
    rating: MediaRatingEntity? = null,
    animeAvailable: Boolean = true
): LibraryEntry {
    val refs = externalRefs.map(ExternalRefEntity::toDomain)
    require(refs.isNotEmpty()) { "Persisted library item has no external reference" }
    val selectedRef = if (!animeAvailable) {
        refs.firstOrNull { it.source == com.cydoniancitizen.bingee.core.model.MediaSource.TMDB }
            ?: preferredRef?.takeIf(refs::contains)
            ?: refs.minWith(compareBy<ExternalMediaRef> { it.source.name }.thenBy { it.externalId })
    } else {
        preferredRef?.takeIf(refs::contains)
            ?: refs.minWith(compareBy<ExternalMediaRef> { it.source.name }.thenBy { it.externalId })
    }
    return LibraryEntry(
        mediaRef = selectedRef,
        mediaType = media.mediaType,
        title = media.title,
        originalTitle = media.originalTitle,
        posterUrl = media.posterUrl,
        releaseDate = media.releaseDate,
        overview = media.overview,
        addedAt = addedAt,
        progress = progressRow.toDomainProgress(media.mediaType),
        personalRating = rating?.let { PersonalRating(it.ratingValue) }
    )
}

private fun LibraryDao.LibraryProgressRow?.toDomainProgress(mediaType: MediaType): LibraryProgress = when {
    this == null -> LibraryProgress.Unavailable
    mediaType == MediaType.MOVIE -> LibraryProgress.Movie(
        movieWatchedAt?.let(MovieWatchState::Watched) ?: MovieWatchState.Unwatched
    )
    mediaType == MediaType.ANIME -> LibraryProgress.Anime(
        watchedEpisodes = animeWatchedEpisodes,
        totalEpisodes = animeEpisodeTotal,
        completed = animeCompletedAt != null || (
            animeEpisodeTotal != null && animeEpisodeTotal > 0 &&
                animeWatchedEpisodes >= animeEpisodeTotal
            )
    )
    trackableEpisodes == 0 -> LibraryProgress.Unavailable
    else -> LibraryProgress.Series(
        SeriesProgress(
            watchedEpisodes = watchedEpisodes,
            trackableEpisodes = trackableEpisodes,
            completedSeasons = completedSeasons,
            trackableSeasons = trackableSeasons,
            isComplete = watchedEpisodes == trackableEpisodes
        )
    )
}

internal fun ExternalRefEntity.toDomain(): ExternalMediaRef = ExternalMediaRef(source = source, externalId = externalId)

private fun String?.normalizedOptionalText(): String? = this?.trim()?.takeIf(String::isNotEmpty)
