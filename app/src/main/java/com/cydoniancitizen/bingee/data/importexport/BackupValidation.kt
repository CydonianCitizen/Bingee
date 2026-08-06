package com.cydoniancitizen.bingee.data.importexport

import com.cydoniancitizen.bingee.core.model.AnimeWatchProgress
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
        require(
            document.schemaVersion in BACKUP_SCHEMA_VERSION_V1..BACKUP_SCHEMA_VERSION,
            BackupFailureKind.UNSUPPORTED_VERSION
        )
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
            val expectedSource = if (media.mediaType == MediaType.ANIME) MediaSource.JIKAN else MediaSource.TMDB
            require(media.primaryRef.source == expectedSource, BackupFailureKind.CONFLICTING_REFERENCE)
            if (document.schemaVersion == BACKUP_SCHEMA_VERSION_V1) {
                require(media.mediaType != MediaType.ANIME, BackupFailureKind.VALIDATION)
            }
            require(
                media.externalRefs.all { it.source == expectedSource },
                BackupFailureKind.CONFLICTING_REFERENCE
            )
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
            require(season.mediaRef.source == MediaSource.TMDB, BackupFailureKind.CONFLICTING_REFERENCE)
            require(season.externalRef.source == MediaSource.TMDB, BackupFailureKind.CONFLICTING_REFERENCE)
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
            require(episode.seasonRef.source == MediaSource.TMDB, BackupFailureKind.CONFLICTING_REFERENCE)
            require(episode.externalRef.source == MediaSource.TMDB, BackupFailureKind.CONFLICTING_REFERENCE)
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

        if (document.schemaVersion < BACKUP_SCHEMA_VERSION_V3) {
            require(data.mediaLinkGroups.isEmpty(), BackupFailureKind.VALIDATION)
            require(data.mediaLinkAudit.isEmpty(), BackupFailureKind.VALIDATION)
        }

        val availableMediaIdentities = buildSet {
            data.media.forEach { media ->
                media.externalRefs.forEach { ref ->
                    add("${ref.source.name}:${media.mediaType.name}:${ref.externalId}")
                }
            }
        }

        val groupUuids = hashSetOf<String>()
        val activeGroupMemberKeys = hashSetOf<String>()
        data.mediaLinkGroups.forEach { group ->
            require(group.groupId.isNotBlank(), BackupFailureKind.VALIDATION)
            require(group.groupId.length <= BackupLimits.MAX_STRING, BackupFailureKind.VALIDATION)
            require(groupUuids.add(group.groupId), BackupFailureKind.DUPLICATE_IDENTITY)
            require(group.members.size == 2, BackupFailureKind.VALIDATION)
            val m1 = group.members[0]
            val m2 = group.members[1]
            require(m1 != m2, BackupFailureKind.VALIDATION)
            checkMediaIdentity(m1)
            checkMediaIdentity(m2)
            require(!group.updatedAt.isBefore(group.createdAt), BackupFailureKind.VALIDATION)

            val key1 = "${m1.source.name}:${m1.mediaType.name}:${m1.externalId}"
            val key2 = "${m2.source.name}:${m2.mediaType.name}:${m2.externalId}"

            require(availableMediaIdentities.contains(key1), BackupFailureKind.MISSING_REFERENCE)
            require(availableMediaIdentities.contains(key2), BackupFailureKind.MISSING_REFERENCE)

            require(activeGroupMemberKeys.add(key1), BackupFailureKind.CONFLICTING_REFERENCE)
            require(activeGroupMemberKeys.add(key2), BackupFailureKind.CONFLICTING_REFERENCE)

            checkMediaIdentity(group.preferredPresentation)
            val prefKey =
                "${group.preferredPresentation.source.name}:${group.preferredPresentation.mediaType.name}:${group.preferredPresentation.externalId}"
            require(prefKey == key1 || prefKey == key2, BackupFailureKind.VALIDATION)
        }

        val auditEventKeys = hashSetOf<String>()
        data.mediaLinkAudit.forEach { audit ->
            require(audit.groupId.isNotBlank(), BackupFailureKind.VALIDATION)
            require(audit.groupId.length <= BackupLimits.MAX_STRING, BackupFailureKind.VALIDATION)
            require(audit.members.size == 2, BackupFailureKind.VALIDATION)
            val m1 = audit.members[0]
            val m2 = audit.members[1]
            require(m1 != m2, BackupFailureKind.VALIDATION)
            checkMediaIdentity(m1)
            checkMediaIdentity(m2)
            val k1 = "${m1.source.name}:${m1.mediaType.name}:${m1.externalId}"
            val k2 = "${m2.source.name}:${m2.mediaType.name}:${m2.externalId}"

            if (audit.preferredPresentation != null) {
                checkMediaIdentity(audit.preferredPresentation)
                val prefK =
                    "${audit.preferredPresentation.source.name}:${audit.preferredPresentation.mediaType.name}:${audit.preferredPresentation.externalId}"
                require(prefK == k1 || prefK == k2, BackupFailureKind.VALIDATION)
            }

            val eventKey =
                "${audit.groupId}|${audit.action.name}|${audit.timestamp}|${audit.origin.name}|$k1|$k2|${audit.preferredPresentation}"
            require(auditEventKeys.add(eventKey), BackupFailureKind.DUPLICATE_IDENTITY)
        }

        val auditByGroup = data.mediaLinkAudit.groupBy { it.groupId }
        data.mediaLinkGroups.forEach { group ->
            val history = auditByGroup[group.groupId]
            if (history != null) {
                val sortedHistory = history.sortedBy { it.timestamp }
                require(
                    sortedHistory.any {
                        it.action ==
                            com.cydoniancitizen.bingee.core.model.MediaLinkAuditAction.LINKED
                    },
                    BackupFailureKind.VALIDATION
                )
                val lastEvent = sortedHistory.last()
                require(
                    lastEvent.action != com.cydoniancitizen.bingee.core.model.MediaLinkAuditAction.UNLINKED,
                    BackupFailureKind.CONFLICTING_REFERENCE
                )
            }
        }

        val animeDetailRefs = hashSetOf<String>()
        data.animeDetails.forEach { details ->
            val media = mediaByRef[details.mediaRef.key()] ?: missing()
            require(media.mediaType == MediaType.ANIME, BackupFailureKind.CONFLICTING_REFERENCE)
            require(details.mediaRef.source == MediaSource.JIKAN, BackupFailureKind.CONFLICTING_REFERENCE)
            require(animeDetailRefs.add(details.mediaRef.key()), BackupFailureKind.DUPLICATE_IDENTITY)
            require(details.episodeCount == null || details.episodeCount > 0, BackupFailureKind.VALIDATION)
            require(details.year == null || details.year in 1900..9999, BackupFailureKind.VALIDATION)
            require(details.providerScore == null || details.providerScore in 0.0..10.0, BackupFailureKind.VALIDATION)
            checkText(details.englishTitle)
            checkText(details.japaneseTitle)
            checkText(details.synopsis)
            checkText(details.duration)
            checkText(details.season)
            checkUrl(details.posterUrl)
        }

        val animeRelationKeys = hashSetOf<String>()
        data.animeRelations.forEach { relation ->
            val media = mediaByRef[relation.mediaRef.key()] ?: missing()
            require(media.mediaType == MediaType.ANIME, BackupFailureKind.CONFLICTING_REFERENCE)
            require(relation.mediaRef.source == MediaSource.JIKAN, BackupFailureKind.CONFLICTING_REFERENCE)
            checkProvider(relation.relatedRef)
            require(relation.relatedRef.source == MediaSource.JIKAN, BackupFailureKind.CONFLICTING_REFERENCE)
            checkText(relation.relationType)
            checkText(relation.relatedTitle)
            require(relation.relationType.isNotBlank(), BackupFailureKind.VALIDATION)
            require(relation.relatedTitle.isNotBlank(), BackupFailureKind.VALIDATION)
            require(
                animeRelationKeys.add(
                    "${relation.mediaRef.key()}|${relation.relationType}|${relation.relatedRef.key()}"
                ),
                BackupFailureKind.DUPLICATE_IDENTITY
            )
        }

        val animeProgressRefs = hashSetOf<String>()
        data.animeProgress.forEach { progress ->
            val media = mediaByRef[progress.mediaRef.key()] ?: missing()
            require(media.mediaType == MediaType.ANIME, BackupFailureKind.CONFLICTING_REFERENCE)
            require(progress.mediaRef.source == MediaSource.JIKAN, BackupFailureKind.CONFLICTING_REFERENCE)
            require(
                progress.watchedEpisodeCount in 0..AnimeWatchProgress.MAX_WATCHED_EPISODES,
                BackupFailureKind.VALIDATION
            )
            require(
                (progress.completedAt == null) == (progress.completionOrigin == null),
                BackupFailureKind.VALIDATION
            )
            require(animeProgressRefs.add(progress.mediaRef.key()), BackupFailureKind.DUPLICATE_IDENTITY)
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
        require(ref.source == MediaSource.TMDB || ref.source == MediaSource.JIKAN, BackupFailureKind.VALIDATION)
        require(ref.externalId.isNotBlank(), BackupFailureKind.VALIDATION)
        require(
            ref.source != MediaSource.JIKAN || ref.externalId.matches(Regex("[1-9][0-9]*")),
            BackupFailureKind.VALIDATION
        )
        require(ref.externalId.length <= BackupLimits.MAX_STRING, BackupFailureKind.VALIDATION)
    }

    private fun checkMediaIdentity(identity: BackupMediaIdentity) {
        require(
            identity.source == MediaSource.TMDB || identity.source == MediaSource.JIKAN,
            BackupFailureKind.VALIDATION
        )
        require(identity.externalId.isNotBlank(), BackupFailureKind.VALIDATION)
        require(
            identity.source != MediaSource.JIKAN || identity.externalId.matches(Regex("[1-9][0-9]*")),
            BackupFailureKind.VALIDATION
        )
        require(identity.externalId.length <= BackupLimits.MAX_STRING, BackupFailureKind.VALIDATION)
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
