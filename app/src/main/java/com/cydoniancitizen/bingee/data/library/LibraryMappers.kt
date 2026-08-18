package com.cydoniancitizen.bingee.data.library

import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.Genre
import com.cydoniancitizen.bingee.core.model.LibraryEntry
import com.cydoniancitizen.bingee.core.model.LibraryProgress
import com.cydoniancitizen.bingee.core.model.MediaSearchResult
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.core.model.MovieWatchState
import com.cydoniancitizen.bingee.core.model.PersonalRating
import com.cydoniancitizen.bingee.core.model.PersonalViewingEntry
import com.cydoniancitizen.bingee.core.model.SeriesProgress
import com.cydoniancitizen.bingee.core.model.WatchedEpisodeActivity
import com.cydoniancitizen.bingee.core.model.resolveTmdbRef
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
    rating: MediaRatingEntity? = null
): LibraryEntry {
    val refs = externalRefs.map(ExternalRefEntity::toDomain)
    require(refs.isNotEmpty()) { "Persisted library item has no external reference" }
    val selectedRef = refs.resolveTmdbRef()
        ?: preferredRef?.takeIf(refs::contains)
        ?: refs.minWith(compareBy<ExternalMediaRef> { it.source.name }.thenBy { it.externalId })
    val domainProgress = progressRow.toDomainProgress(media.mediaType)
    val domainWatchedDate = progressRow?.let {
        if (media.mediaType == MediaType.MOVIE) {
            it.movieWatchedDate
        } else if (it.trackableEpisodes > 0 &&
            it.watchedEpisodes == it.trackableEpisodes &&
            it.hasSufficientCoverage
        ) {
            it.seriesWatchedDate
        } else {
            null
        }
    }
    return LibraryEntry(
        mediaRef = selectedRef,
        mediaType = media.mediaType,
        title = media.title,
        originalTitle = media.originalTitle,
        posterUrl = media.posterUrl,
        releaseDate = media.releaseDate,
        overview = media.overview,
        addedAt = addedAt ?: media.createdAt,
        progress = domainProgress,
        personalRating = rating?.let { PersonalRating(it.ratingValue) },
        isFavorite = media.isFavorite,
        favoriteAddedAt = media.favoriteAddedAt,
        watchedDate = domainWatchedDate,
        isAbandoned = progressRow?.isAbandoned == true,
        inLibrary = inLibrary
    )
}

internal fun LibraryDao.LibraryProgressRow?.toDomainProgress(mediaType: MediaType): LibraryProgress = when {
    this == null -> LibraryProgress.Unavailable
    mediaType == MediaType.MOVIE -> LibraryProgress.Movie(
        movieWatchedAt?.let { MovieWatchState.Watched(it, movieWatchedDate) } ?: MovieWatchState.Unwatched
    )
    trackableEpisodes == 0 -> LibraryProgress.Unavailable
    else -> LibraryProgress.Series(
        SeriesProgress(
            watchedEpisodes = watchedEpisodes,
            trackableEpisodes = trackableEpisodes,
            completedSeasons = completedSeasons,
            trackableSeasons = trackableSeasons,
            isComplete = trackableEpisodes > 0 && watchedEpisodes == trackableEpisodes && hasSufficientCoverage,
            watchedDate = seriesWatchedDate,
            lastWatchedAt = lastProgressAt,
            nextEpisode = if (nextSeasonNumber != null && nextEpisodeNumber != null) {
                com.cydoniancitizen.bingee.core.model.EpisodePosition(nextSeasonNumber, nextEpisodeNumber)
            } else {
                null
            }
        )
    )
}

internal fun ExternalRefEntity.toDomain(): ExternalMediaRef = ExternalMediaRef(source = source, externalId = externalId)

internal fun LibraryDao.PersonalViewingRow.toDomain(
    currentProgress: LibraryDao.LibraryProgressRow? = null,
    genres: List<Genre> = emptyList(),
    watchedRegularEpisodeActivities: List<WatchedEpisodeActivity> = emptyList()
): PersonalViewingEntry = PersonalViewingEntry(
    mediaRef = ExternalMediaRef(source, externalId),
    mediaType = media.mediaType,
    title = media.title,
    originalTitle = media.originalTitle,
    posterUrl = media.posterUrl,
    addedAt = membershipAddedAt ?: media.createdAt,
    inLibrary = inLibrary,
    isFavorite = media.isFavorite,
    isAbandoned = isAbandoned,
    personalRating = ratingValue?.let(::PersonalRating),
    personalRatingUpdatedAt = ratingUpdatedAt,
    movieWatchedAt = movieWatchedAt,
    watchedRegularEpisodes = watchedRegularEpisodes,
    seriesCompletedAt = seriesCompletedAt,
    watchedDate = if (media.mediaType == MediaType.MOVIE) movieWatchedDate else seriesWatchedDate,
    movieRuntimeMinutes = movieRuntimeMinutes,
    watchedRegularRuntimeMinutes = watchedRegularRuntimeMinutes,
    watchedRegularEpisodesWithoutRuntime = watchedRegularEpisodesWithoutRuntime,
    watchedRegularEpisodeActivities = watchedRegularEpisodeActivities,
    seriesIsCurrentlyComplete = if (media.mediaType == MediaType.SERIES && currentProgress != null) {
        (currentProgress.toDomainProgress(media.mediaType) as? LibraryProgress.Series)?.progress?.isComplete == true
    } else {
        null
    },
    genres = genres,
    releaseDate = media.releaseDate
)

internal fun LibraryDao.PersonalViewingGenreRow.toDomainOrNull(): Genre? =
    if (source != null && genreId != null) Genre(name = name, source = source, genreId = genreId) else null

internal fun LibraryDao.PersonalViewingActivityRow.toDomain(): WatchedEpisodeActivity =
    WatchedEpisodeActivity(watchedAt = watchedAt, runtimeMinutes = runtimeMinutes)

private fun String?.normalizedOptionalText(): String? = this?.trim()?.takeIf(String::isNotEmpty)
