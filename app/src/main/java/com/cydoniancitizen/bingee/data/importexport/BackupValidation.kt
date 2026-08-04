package com.cydoniancitizen.bingee.data.importexport

import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType

internal data class ValidatedBackupPlan(val document: BackupDocument)

internal data class BackupValidationFailure(val kind: BackupFailureKind) : Exception()

internal sealed interface BackupValidationResult {
    data class Success(val plan: ValidatedBackupPlan) : BackupValidationResult
    data class Failure(val failure: BackupValidationFailure) : BackupValidationResult
}

internal data class BackupPreview(
    val schemaVersion: Int,
    val exportedAt: String,
    val mediaCount: Int,
    val movieCount: Int,
    val seriesCount: Int,
    val libraryCount: Int,
    val watchedMovieCount: Int,
    val watchedEpisodeCount: Int,
    val ratingCount: Int,
    val currentLibraryCount: Int,
    val notificationLeadDays: Int,
    val notifyMovieReleases: Boolean,
    val notifySeasonPremieres: Boolean,
    val notifyEpisodeAirings: Boolean
)

internal object BackupValidator {
    fun validate(document: BackupDocument): BackupValidationResult = try {
        require(document.formatId == BACKUP_FORMAT_ID, BackupFailureKind.WRONG_FORMAT)
        require(document.schemaVersion == BACKUP_SCHEMA_VERSION, BackupFailureKind.UNSUPPORTED_VERSION)
        val data = document.data
        require(data.media.size <= BackupLimits.MAX_MEDIA, BackupFailureKind.TOO_LARGE)
        require(data.seasons.size <= BackupLimits.MAX_SEASONS, BackupFailureKind.TOO_LARGE)
        require(data.episodes.size <= BackupLimits.MAX_EPISODES, BackupFailureKind.TOO_LARGE)

        val mediaByRef = linkedMapOf<String, BackupMedia>()
        val mediaPrimaryKeys = hashSetOf<String>()
        data.media.forEach { media ->
            require(media.title.isNotBlank(), BackupFailureKind.VALIDATION)
            checkText(media.title)
            checkText(media.originalTitle)
            checkText(media.overview)
            checkUrl(media.posterUrl)
            checkProvider(media.primaryRef)
            val refs = media.externalRefs
            require(refs.isNotEmpty(), BackupFailureKind.MISSING_REFERENCE)
            val localRefs = hashSetOf<String>()
            refs.forEach { ref ->
                checkProvider(ref)
                require(localRefs.add(ref.key()), BackupFailureKind.DUPLICATE_IDENTITY)
                val previous = mediaByRef[ref.key()]
                if (previous == null) {
                    mediaByRef[ref.key()] = media
                } else {
                    throw BackupValidationFailure(
                        if (previous == media) {
                            BackupFailureKind.DUPLICATE_IDENTITY
                        } else {
                            BackupFailureKind.CONFLICTING_REFERENCE
                        }
                    )
                }
            }
            require(refs.any { it.key() == media.primaryRef.key() }, BackupFailureKind.MISSING_REFERENCE)
            require(mediaPrimaryKeys.add(media.primaryRef.key()), BackupFailureKind.DUPLICATE_IDENTITY)
        }

        val seasonsByRef = linkedMapOf<String, BackupSeason>()
        val seasonNumbers = hashSetOf<String>()
        data.seasons.forEach { season ->
            checkProvider(season.mediaRef)
            checkProvider(season.externalRef)
            val media = mediaByRef[season.mediaRef.key()] ?: missing()
            require(media.mediaType == MediaType.SERIES, BackupFailureKind.CONFLICTING_REFERENCE)
            require(media.primaryRef.source == season.externalRef.source, BackupFailureKind.CONFLICTING_REFERENCE)
            require(season.seasonNumber >= 0, BackupFailureKind.VALIDATION)
            require(season.episodeCount >= 0, BackupFailureKind.VALIDATION)
            checkText(season.name)
            checkText(season.overview)
            checkUrl(season.posterUrl)
            val previousSeason = seasonsByRef[season.externalRef.key()]
            if (previousSeason != null) {
                throw BackupValidationFailure(
                    if (previousSeason == season) {
                        BackupFailureKind.DUPLICATE_IDENTITY
                    } else {
                        BackupFailureKind.CONFLICTING_REFERENCE
                    }
                )
            }
            seasonsByRef[season.externalRef.key()] = season
            require(
                seasonNumbers.add("${season.mediaRef.key()}|${season.seasonNumber}"),
                BackupFailureKind.DUPLICATE_IDENTITY
            )
        }

        val episodesByRef = linkedMapOf<String, BackupEpisode>()
        val episodeNumbers = hashSetOf<String>()
        data.episodes.forEach { episode ->
            checkProvider(episode.seasonRef)
            checkProvider(episode.externalRef)
            val season = seasonsByRef[episode.seasonRef.key()] ?: missing()
            require(season.externalRef.source == episode.externalRef.source, BackupFailureKind.CONFLICTING_REFERENCE)
            require(episode.episodeNumber > 0, BackupFailureKind.VALIDATION)
            checkText(episode.title)
            checkText(episode.overview)
            checkUrl(episode.stillUrl)
            require(episode.runtimeMinutes == null || episode.runtimeMinutes > 0, BackupFailureKind.VALIDATION)
            val previousEpisode = episodesByRef[episode.externalRef.key()]
            if (previousEpisode != null) {
                throw BackupValidationFailure(
                    if (previousEpisode == episode) {
                        BackupFailureKind.DUPLICATE_IDENTITY
                    } else {
                        BackupFailureKind.CONFLICTING_REFERENCE
                    }
                )
            }
            episodesByRef[episode.externalRef.key()] = episode
            require(
                episodeNumbers.add("${episode.seasonRef.key()}|${episode.episodeNumber}"),
                BackupFailureKind.DUPLICATE_IDENTITY
            )
        }

        val libraryRefs = hashSetOf<String>()
        data.library.forEach { entry ->
            require(mediaByRef.containsKey(entry.mediaRef.key()), BackupFailureKind.MISSING_REFERENCE)
            require(libraryRefs.add(entry.mediaRef.key()), BackupFailureKind.DUPLICATE_IDENTITY)
        }

        val movieProgressRefs = hashSetOf<String>()
        data.movieProgress.forEach { progress ->
            val media = mediaByRef[progress.mediaRef.key()] ?: missing()
            require(media.mediaType == MediaType.MOVIE, BackupFailureKind.CONFLICTING_REFERENCE)
            require(movieProgressRefs.add(progress.mediaRef.key()), BackupFailureKind.DUPLICATE_IDENTITY)
        }

        val episodeProgressRefs = hashSetOf<String>()
        data.episodeProgress.forEach { progress ->
            require(episodesByRef.containsKey(progress.episodeRef.key()), BackupFailureKind.MISSING_REFERENCE)
            require(episodeProgressRefs.add(progress.episodeRef.key()), BackupFailureKind.DUPLICATE_IDENTITY)
        }

        val ratingRefs = hashSetOf<String>()
        data.ratings.forEach { rating ->
            require(mediaByRef.containsKey(rating.mediaRef.key()), BackupFailureKind.MISSING_REFERENCE)
            require(rating.rating in 1..10, BackupFailureKind.VALIDATION)
            require(!rating.updatedAt.isBefore(rating.ratedAt), BackupFailureKind.VALIDATION)
            require(ratingRefs.add(rating.mediaRef.key()), BackupFailureKind.DUPLICATE_IDENTITY)
        }

        require(data.preferences.notificationLeadDays in setOf(0, 1, 3, 7), BackupFailureKind.VALIDATION)
        BackupValidationResult.Success(ValidatedBackupPlan(document))
    } catch (failure: BackupValidationFailure) {
        BackupValidationResult.Failure(failure)
    } catch (_: Exception) {
        BackupValidationResult.Failure(BackupValidationFailure(BackupFailureKind.VALIDATION))
    }

    fun preview(plan: ValidatedBackupPlan, currentLibraryCount: Int): BackupPreview {
        val data = plan.document.data
        return BackupPreview(
            schemaVersion = plan.document.schemaVersion,
            exportedAt = plan.document.exportedAt.toString(),
            mediaCount = data.media.size,
            movieCount = data.media.count { it.mediaType == MediaType.MOVIE },
            seriesCount = data.media.count { it.mediaType == MediaType.SERIES },
            libraryCount = data.library.size,
            watchedMovieCount = data.movieProgress.size,
            watchedEpisodeCount = data.episodeProgress.size,
            ratingCount = data.ratings.size,
            currentLibraryCount = currentLibraryCount,
            notificationLeadDays = data.preferences.notificationLeadDays,
            notifyMovieReleases = data.preferences.notifyMovieReleases,
            notifySeasonPremieres = data.preferences.notifySeasonPremieres,
            notifyEpisodeAirings = data.preferences.notifyEpisodeAirings
        )
    }

    private fun checkProvider(ref: BackupRef) {
        require(ref.source == MediaSource.TMDB, BackupFailureKind.VALIDATION)
        require(ref.externalId.isNotBlank(), BackupFailureKind.VALIDATION)
        require(ref.externalId.length <= BackupLimits.MAX_STRING, BackupFailureKind.VALIDATION)
    }

    private fun checkText(value: String?) {
        require(value == null || value.length <= BackupLimits.MAX_STRING, BackupFailureKind.VALIDATION)
    }

    private fun checkUrl(value: String?) {
        require(value == null || value.length <= BackupLimits.MAX_URL, BackupFailureKind.VALIDATION)
    }

    private fun missing(): Nothing = throw BackupValidationFailure(BackupFailureKind.MISSING_REFERENCE)

    private fun BackupRef.key(): String = "${source.name}:$externalId"

    private fun require(condition: Boolean, kind: BackupFailureKind) {
        if (!condition) throw BackupValidationFailure(kind)
    }
}
