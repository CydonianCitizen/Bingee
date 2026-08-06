package com.cydoniancitizen.bingee.data.importexport

import androidx.room.withTransaction
import com.cydoniancitizen.bingee.core.model.AnimeFormat
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.data.library.local.AnimeDetailsEntity
import com.cydoniancitizen.bingee.data.library.local.AnimeProgressEntity
import com.cydoniancitizen.bingee.data.library.local.AnimeRelationEntity
import com.cydoniancitizen.bingee.data.library.local.BingeeDatabase
import com.cydoniancitizen.bingee.data.library.local.EpisodeEntity
import com.cydoniancitizen.bingee.data.library.local.EpisodeWatchProgressEntity
import com.cydoniancitizen.bingee.data.library.local.ExternalRefEntity
import com.cydoniancitizen.bingee.data.library.local.LibraryMembershipEntity
import com.cydoniancitizen.bingee.data.library.local.MediaEntity
import com.cydoniancitizen.bingee.data.library.local.MediaLinkAuditEntity
import com.cydoniancitizen.bingee.data.library.local.MediaLinkAuditMemberEntity
import com.cydoniancitizen.bingee.data.library.local.MediaLinkDao
import com.cydoniancitizen.bingee.data.library.local.MediaLinkGroupEntity
import com.cydoniancitizen.bingee.data.library.local.MediaLinkMemberEntity
import com.cydoniancitizen.bingee.data.library.local.MediaRatingEntity
import com.cydoniancitizen.bingee.data.library.local.MovieWatchProgressEntity
import com.cydoniancitizen.bingee.data.library.local.PortablePreferencesEntity
import com.cydoniancitizen.bingee.data.library.local.PortableSnapshotDao
import com.cydoniancitizen.bingee.data.library.local.ReleaseEventDao
import com.cydoniancitizen.bingee.data.library.local.SeasonEntity
import com.cydoniancitizen.bingee.data.settings.DataStoreReleaseNotificationPreferences
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

internal enum class RestoreStage {
    MEDIA,
    EXTERNAL_REFERENCES,
    SEASONS,
    ANIME_DETAILS,
    ANIME_RELATIONS,
    ANIME_PROGRESS,
    EPISODES,
    LIBRARY_MEMBERSHIP,
    MOVIE_PROGRESS,
    EPISODE_PROGRESS,
    RATINGS,
    PORTABLE_PREFERENCES,
    RELEASE_EVENTS,
    ANIME_RELEASE_EVENTS,
    ACTIVE_LINK_GROUPS,
    ACTIVE_LINK_MEMBERS,
    PREFERRED_PRESENTATION,
    LINK_AUDIT,
    LINK_AUDIT_MEMBERS
}

internal fun interface RestoreFailureInjector {
    fun check(stage: RestoreStage)
}

@Singleton
internal class BackupDataStore @Inject constructor(
    private val database: BingeeDatabase,
    private val snapshotDao: PortableSnapshotDao,
    private val releaseEventDao: ReleaseEventDao,
    private val notificationPreferences: DataStoreReleaseNotificationPreferences,
    private val mediaLinkDao: MediaLinkDao = database.mediaLinkDao()
) {
    suspend fun readPortableData(): BackupData {
        notificationPreferences.preferences.first()
        return database.withTransaction {
            val rows = snapshotDao.readSnapshot()
            val seasonById = rows.seasons.associateBy { it.localSeasonId }
            val watchedEpisodeIds = rows.episodeProgress.map { it.localEpisodeId }.toSet()
            val episodeMediaIds = rows.episodes
                .filter { it.localEpisodeId in watchedEpisodeIds }
                .mapNotNull { episode -> seasonById[episode.localSeasonId]?.localMediaId }
                .toSet()

            val linkGroupEntities = mediaLinkDao.getGroupEntities()
            val activeLinkMemberMediaIds = hashSetOf<Long>()
            val backupLinkGroups = linkGroupEntities.map { groupEntity ->
                val membersWithIdentity = mediaLinkDao.findMembersWithIdentityByGroupId(groupEntity.localGroupId)
                val memberIdentities = membersWithIdentity.map { m ->
                    activeLinkMemberMediaIds.add(m.localMediaId)
                    BackupMediaIdentity(m.source, m.mediaType, m.externalId)
                }.sortedWith(compareBy({ it.source.name }, { it.mediaType.name }, { it.externalId }))

                val preferredMember =
                    membersWithIdentity.firstOrNull { it.localMediaId == groupEntity.preferredPresentationMediaId }
                        ?: error("Preferred presentation member not found for group ${groupEntity.groupUuid}")

                BackupMediaLinkGroup(
                    groupId = groupEntity.groupUuid,
                    members = memberIdentities,
                    preferredPresentation = BackupMediaIdentity(
                        preferredMember.source,
                        preferredMember.mediaType,
                        preferredMember.externalId
                    ),
                    createdAt = groupEntity.createdAt,
                    updatedAt = groupEntity.updatedAt
                )
            }.sortedBy { it.groupId }

            val auditEntities = mediaLinkDao.getAuditTrail()
            val backupLinkAudits = auditEntities.map { auditEntity ->
                val auditMembers = mediaLinkDao.getAuditMembers(auditEntity.auditId)
                val memberIdentities = auditMembers.map { m ->
                    BackupMediaIdentity(m.source, m.mediaType, m.externalId)
                }.sortedWith(compareBy({ it.source.name }, { it.mediaType.name }, { it.externalId }))

                val preferredIdentity = if (auditEntity.preferredSource != null &&
                    auditEntity.preferredMediaType != null &&
                    auditEntity.preferredExternalId != null
                ) {
                    BackupMediaIdentity(
                        auditEntity.preferredSource,
                        auditEntity.preferredMediaType,
                        auditEntity.preferredExternalId
                    )
                } else {
                    null
                }

                BackupMediaLinkAudit(
                    groupId = auditEntity.groupUuid,
                    action = auditEntity.action,
                    timestamp = auditEntity.actionTimestamp,
                    origin = auditEntity.origin,
                    members = memberIdentities,
                    preferredPresentation = preferredIdentity
                )
            }.sortedWith(
                compareBy(
                    { it.timestamp },
                    { it.groupId },
                    { it.action.name },
                    { it.members.firstOrNull()?.source?.name },
                    { it.members.firstOrNull()?.mediaType?.name },
                    { it.members.firstOrNull()?.externalId }
                )
            )

            val portableMediaIds = buildSet {
                addAll(rows.memberships.map { it.localMediaId })
                addAll(rows.ratings.map { it.localMediaId })
                addAll(rows.movieProgress.map { it.localMediaId })
                addAll(episodeMediaIds)
                addAll(rows.animeProgress.map { it.localMediaId })
                addAll(activeLinkMemberMediaIds)
            }
            val media = rows.media.filter { it.localMediaId in portableMediaIds }
            val refsByMedia = rows.refs.groupBy { it.localMediaId }
            val primaryByMedia = media.associate { entity ->
                entity.localMediaId to refsByMedia.getValue(entity.localMediaId)
                    .map { BackupRef(it.source, it.externalId) }
                    .sortedWith(compareBy({ it.source.name }, { it.externalId }))
                    .first()
            }
            val mediaRecords = media.map { entity ->
                BackupMedia(
                    primaryRef = primaryByMedia.getValue(entity.localMediaId),
                    externalRefs = refsByMedia.getValue(entity.localMediaId)
                        .map { BackupRef(it.source, it.externalId) }
                        .sortedWith(compareBy({ it.source.name }, { it.externalId })),
                    mediaType = entity.mediaType,
                    title = entity.title,
                    originalTitle = entity.originalTitle,
                    overview = entity.overview,
                    posterUrl = entity.posterUrl,
                    releaseDate = entity.releaseDate
                )
            }.sortedWith(compareBy({ it.primaryRef.source.name }, { it.primaryRef.externalId }, { it.mediaType.name }))

            val selectedSeries = media.filter { it.mediaType == MediaType.SERIES }.map { it.localMediaId }.toSet()
            val seasons = rows.seasons.filter { it.localMediaId in selectedSeries }
            val seasonParentRefs = seasons.associate { it.localSeasonId to primaryByMedia.getValue(it.localMediaId) }
            val seasonRecords = seasons.map { season ->
                BackupSeason(
                    mediaRef = seasonParentRefs.getValue(season.localSeasonId),
                    externalRef = BackupRef(season.source, season.externalId),
                    seasonNumber = season.seasonNumber,
                    name = season.name,
                    overview = season.overview,
                    posterUrl = season.posterUrl,
                    airDate = season.airDate,
                    episodeCount = season.episodeCount
                )
            }.sortedWith(
                compareBy({
                    it.mediaRef.source.name
                }, {
                    it.mediaRef.externalId
                }, { it.seasonNumber }, { it.externalRef.source.name }, { it.externalRef.externalId })
            )

            val seasonRefs = seasons.associate { it.localSeasonId to BackupRef(it.source, it.externalId) }
            val seasonIds = seasons.map { it.localSeasonId }.toSet()
            val episodes = rows.episodes.filter { it.localSeasonId in seasonIds }
            val episodeRecords = episodes.map { episode ->
                BackupEpisode(
                    seasonRef = seasonRefs.getValue(episode.localSeasonId),
                    externalRef = BackupRef(episode.source, episode.externalId),
                    episodeNumber = episode.episodeNumber,
                    title = episode.title,
                    overview = episode.overview,
                    airDate = episode.airDate,
                    runtimeMinutes = episode.runtimeMinutes,
                    stillUrl = episode.stillUrl
                )
            }.sortedWith(
                compareBy({
                    it.seasonRef.source.name
                }, {
                    it.seasonRef.externalId
                }, { it.episodeNumber }, { it.externalRef.source.name }, { it.externalRef.externalId })
            )

            val mediaRefById = primaryByMedia
            val episodeRefById = rows.episodes.associate { it.localEpisodeId to BackupRef(it.source, it.externalId) }
            val dataPreferences = rows.preferences?.let {
                BackupPreferences(
                    it.notificationLeadDays,
                    it.notifyMovieReleases,
                    it.notifySeasonPremieres,
                    it.notifyEpisodeAirings
                )
            } ?: BackupPreferences(1, true, true, true)
            BackupData(
                media = mediaRecords,
                seasons = seasonRecords,
                episodes = episodeRecords,
                library = rows.memberships.filter { it.localMediaId in portableMediaIds }
                    .map { BackupLibraryEntry(mediaRefById.getValue(it.localMediaId), it.addedAt) }
                    .sortedWith(compareBy({ it.mediaRef.source.name }, { it.mediaRef.externalId })),
                movieProgress = rows.movieProgress.filter { it.localMediaId in portableMediaIds }
                    .map { BackupMovieProgress(mediaRefById.getValue(it.localMediaId), it.watchedAt) }
                    .sortedWith(compareBy({ it.mediaRef.source.name }, { it.mediaRef.externalId })),
                episodeProgress = rows.episodeProgress.filter { it.localEpisodeId in episodeRefById }
                    .map { BackupEpisodeProgress(episodeRefById.getValue(it.localEpisodeId), it.watchedAt) }
                    .sortedWith(compareBy({ it.episodeRef.source.name }, { it.episodeRef.externalId })),
                ratings = rows.ratings.filter { it.localMediaId in portableMediaIds }
                    .map {
                        BackupRating(mediaRefById.getValue(it.localMediaId), it.ratingValue, it.ratedAt, it.updatedAt)
                    }
                    .sortedWith(compareBy({ it.mediaRef.source.name }, { it.mediaRef.externalId })),
                preferences = dataPreferences,
                animeDetails = rows.animeDetails
                    .filter { it.localMediaId in portableMediaIds }
                    .map {
                        BackupAnimeDetails(
                            mediaRef = mediaRefById.getValue(it.localMediaId),
                            format = it.format,
                            status = it.providerStatus,
                            englishTitle = it.englishTitle,
                            japaneseTitle = it.japaneseTitle,
                            synopsis = it.synopsis,
                            episodeCount = it.episodeCount,
                            duration = it.duration,
                            startDate = it.startDate,
                            endDate = it.endDate,
                            season = it.season,
                            year = it.year,
                            providerScore = it.providerScore,
                            posterUrl = it.imageUrl
                        )
                    }
                    .sortedWith(compareBy({ it.mediaRef.source.name }, { it.mediaRef.externalId })),
                animeRelations = rows.animeRelations
                    .filter { it.localMediaId in portableMediaIds }
                    .map {
                        BackupAnimeRelation(
                            mediaRef = mediaRefById.getValue(it.localMediaId),
                            relationType = it.relationType,
                            relatedRef = BackupRef(
                                MediaSource.JIKAN,
                                it.relatedJikanId
                            ),
                            relatedTitle = it.relatedTitle,
                            relatedFormat = it.relatedFormat
                        )
                    }
                    .sortedWith(
                        compareBy(
                            { it.mediaRef.externalId },
                            { it.relationType },
                            { it.relatedRef.externalId }
                        )
                    ),
                animeProgress = rows.animeProgress
                    .filter { it.localMediaId in portableMediaIds }
                    .map {
                        BackupAnimeProgress(
                            mediaRef = mediaRefById.getValue(it.localMediaId),
                            watchedEpisodeCount = it.watchedEpisodeCount,
                            completedAt = it.completedAt,
                            completionOrigin = it.completionOrigin,
                            updatedAt = it.updatedAt
                        )
                    }
                    .sortedWith(compareBy({ it.mediaRef.source.name }, { it.mediaRef.externalId })),
                mediaLinkGroups = backupLinkGroups,
                mediaLinkAudit = backupLinkAudits
            )
        }
    }

    suspend fun currentLibraryCount(): Int = database.withTransaction {
        snapshotDao.readSnapshot().memberships.size
    }

    suspend fun restore(
        plan: ValidatedBackupPlan,
        failureInjector: RestoreFailureInjector = RestoreFailureInjector {}
    ) {
        database.withTransaction {
            val data = plan.document.data
            val exportedAt = plan.document.exportedAt
            snapshotDao.deleteNotificationDeliveries()
            snapshotDao.deleteLinkAuditMembers()
            snapshotDao.deleteLinkAudit()
            snapshotDao.deleteLinkMembers()
            snapshotDao.deleteLinkGroups()
            snapshotDao.deleteReleaseEvents()
            snapshotDao.deleteEpisodeProgress()

            snapshotDao.deleteAnimeProgress()
            snapshotDao.deleteAnimeRelations()
            snapshotDao.deleteAnimeDetails()
            snapshotDao.deleteMovieProgress()
            snapshotDao.deleteRatings()
            snapshotDao.deleteMemberships()
            snapshotDao.deleteEpisodes()
            snapshotDao.deleteSeasons()
            snapshotDao.deleteGenres()
            snapshotDao.deleteDetails()
            snapshotDao.deleteRefs()
            snapshotDao.deleteMedia()
            snapshotDao.deleteCalendarRefreshState()
            snapshotDao.deletePreferences()

            val mediaIds = linkedMapOf<String, Long>()
            val mediaIdsByIdentityKey = linkedMapOf<String, Long>()
            data.media.forEach { media ->
                val localId = snapshotDao.insertMedia(
                    MediaEntity(
                        mediaType = media.mediaType,
                        title = media.title,
                        originalTitle = media.originalTitle,
                        overview = media.overview,
                        posterUrl = media.posterUrl,
                        releaseDate = media.releaseDate,
                        createdAt = exportedAt,
                        metadataUpdatedAt = exportedAt
                    )
                )
                mediaIds[media.primaryRef.key()] = localId
                media.externalRefs.forEach { ref ->
                    val identityKey = "${ref.source.name}:${media.mediaType.name}:${ref.externalId}"
                    mediaIdsByIdentityKey[identityKey] = localId
                }
            }
            failureInjector.check(RestoreStage.MEDIA)

            data.media.forEach { media ->
                val localId = checkNotNull(mediaIds[media.primaryRef.key()])
                media.externalRefs.forEach { ref ->
                    mediaIds[ref.key()] = localId
                    snapshotDao.insertExternalRef(ExternalRefEntity(localId, ref.source, ref.externalId))
                }
            }
            failureInjector.check(RestoreStage.EXTERNAL_REFERENCES)
            data.animeDetails.forEach { details ->
                snapshotDao.insertAnimeDetails(
                    AnimeDetailsEntity(
                        localMediaId = checkNotNull(mediaIds[details.mediaRef.key()]),
                        format = details.format,
                        providerStatus = details.status,
                        englishTitle = details.englishTitle,
                        japaneseTitle = details.japaneseTitle,
                        synopsis = details.synopsis,
                        episodeCount = details.episodeCount,
                        duration = details.duration,
                        startDate = details.startDate,
                        endDate = details.endDate,
                        season = details.season,
                        year = details.year,
                        providerScore = details.providerScore,
                        imageUrl = details.posterUrl,
                        detailsUpdatedAt = exportedAt
                    )
                )
            }
            failureInjector.check(RestoreStage.ANIME_DETAILS)

            data.animeRelations.forEach { relation ->
                snapshotDao.insertAnimeRelation(
                    AnimeRelationEntity(
                        localMediaId = checkNotNull(mediaIds[relation.mediaRef.key()]),
                        relationType = relation.relationType,
                        relatedJikanId = relation.relatedRef.externalId,
                        relatedTitle = relation.relatedTitle,
                        relatedFormat = relation.relatedFormat
                            ?: AnimeFormat.UNKNOWN
                    )
                )
            }
            failureInjector.check(RestoreStage.ANIME_RELATIONS)

            data.animeProgress.forEach { progress ->
                snapshotDao.insertAnimeProgress(
                    AnimeProgressEntity(
                        localMediaId = checkNotNull(mediaIds[progress.mediaRef.key()]),
                        watchedEpisodeCount = progress.watchedEpisodeCount,
                        completedAt = progress.completedAt,
                        completionOrigin = progress.completionOrigin,
                        updatedAt = progress.updatedAt
                    )
                )
            }
            failureInjector.check(RestoreStage.ANIME_PROGRESS)

            val seasonIds = linkedMapOf<String, Long>()
            data.seasons.forEach { season ->
                val localId = snapshotDao.insertSeason(
                    SeasonEntity(
                        localMediaId = checkNotNull(mediaIds[season.mediaRef.key()]),
                        source = season.externalRef.source,
                        externalId = season.externalRef.externalId,
                        seasonNumber = season.seasonNumber,
                        name = season.name,
                        overview = season.overview,
                        posterUrl = season.posterUrl,
                        airDate = season.airDate,
                        episodeCount = season.episodeCount,
                        metadataUpdatedAt = exportedAt,
                        episodesFetchedAt = null
                    )
                )
                seasonIds[season.externalRef.key()] = localId
            }
            failureInjector.check(RestoreStage.SEASONS)

            val episodeIds = linkedMapOf<String, Long>()
            data.episodes.forEach { episode ->
                val localId = snapshotDao.insertEpisode(
                    EpisodeEntity(
                        localSeasonId = checkNotNull(seasonIds[episode.seasonRef.key()]),
                        source = episode.externalRef.source,
                        externalId = episode.externalRef.externalId,
                        episodeNumber = episode.episodeNumber,
                        title = episode.title,
                        overview = episode.overview,
                        airDate = episode.airDate,
                        runtimeMinutes = episode.runtimeMinutes,
                        stillUrl = episode.stillUrl,
                        metadataUpdatedAt = exportedAt
                    )
                )
                episodeIds[episode.externalRef.key()] = localId
            }
            failureInjector.check(RestoreStage.EPISODES)

            data.library.forEach { entry ->
                snapshotDao.insertMembership(
                    LibraryMembershipEntity(checkNotNull(mediaIds[entry.mediaRef.key()]), entry.addedAt)
                )
            }
            failureInjector.check(RestoreStage.LIBRARY_MEMBERSHIP)
            data.movieProgress.forEach { progress ->
                snapshotDao.insertMovieProgress(
                    MovieWatchProgressEntity(checkNotNull(mediaIds[progress.mediaRef.key()]), progress.watchedAt)
                )
            }
            failureInjector.check(RestoreStage.MOVIE_PROGRESS)
            data.episodeProgress.forEach { progress ->
                snapshotDao.insertEpisodeProgress(
                    EpisodeWatchProgressEntity(checkNotNull(episodeIds[progress.episodeRef.key()]), progress.watchedAt)
                )
            }
            failureInjector.check(RestoreStage.EPISODE_PROGRESS)
            data.ratings.forEach { rating ->
                snapshotDao.insertRating(
                    MediaRatingEntity(
                        checkNotNull(mediaIds[rating.mediaRef.key()]),
                        rating.rating,
                        rating.ratedAt,
                        rating.updatedAt
                    )
                )
            }
            failureInjector.check(RestoreStage.RATINGS)
            snapshotDao.replacePreferences(
                PortablePreferencesEntity(
                    notificationLeadDays = data.preferences.notificationLeadDays,
                    notifyMovieReleases = data.preferences.notifyMovieReleases,
                    notifySeasonPremieres = data.preferences.notifySeasonPremieres,
                    notifyEpisodeAirings = data.preferences.notifyEpisodeAirings,
                    legacyBridgeCompleted = true
                )
            )
            failureInjector.check(RestoreStage.PORTABLE_PREFERENCES)
            releaseEventDao.backfill(exportedAt)
            failureInjector.check(RestoreStage.ANIME_RELEASE_EVENTS)
            failureInjector.check(RestoreStage.RELEASE_EVENTS)

            data.mediaLinkGroups.forEach { group ->
                val prefKey =
                    "${group.preferredPresentation.source.name}:${group.preferredPresentation.mediaType.name}:${group.preferredPresentation.externalId}"
                val preferredLocalId = checkNotNull(mediaIdsByIdentityKey[prefKey]) {
                    "Missing local media ID for preferred presentation $prefKey"
                }

                val groupEntity = MediaLinkGroupEntity(
                    groupUuid = group.groupId,
                    preferredPresentationMediaId = preferredLocalId,
                    createdAt = group.createdAt,
                    updatedAt = group.updatedAt
                )
                val localGroupId = mediaLinkDao.insertGroup(groupEntity)
                failureInjector.check(RestoreStage.ACTIVE_LINK_GROUPS)

                val memberEntities = group.members.map { member ->
                    val memberKey = "${member.source.name}:${member.mediaType.name}:${member.externalId}"
                    val memberLocalId = checkNotNull(mediaIdsByIdentityKey[memberKey]) {
                        "Missing local media ID for member $memberKey"
                    }
                    MediaLinkMemberEntity(
                        localGroupId = localGroupId,
                        localMediaId = memberLocalId,
                        addedAt = group.createdAt
                    )
                }
                mediaLinkDao.insertMembers(memberEntities)
                failureInjector.check(RestoreStage.ACTIVE_LINK_MEMBERS)
                failureInjector.check(RestoreStage.PREFERRED_PRESENTATION)
            }

            data.mediaLinkAudit.forEach { audit ->
                val auditEntity = MediaLinkAuditEntity(
                    groupUuid = audit.groupId,
                    action = audit.action,
                    actionTimestamp = audit.timestamp,
                    origin = audit.origin,
                    preferredSource = audit.preferredPresentation?.source,
                    preferredMediaType = audit.preferredPresentation?.mediaType,
                    preferredExternalId = audit.preferredPresentation?.externalId
                )
                val auditId = mediaLinkDao.insertAudit(auditEntity)
                failureInjector.check(RestoreStage.LINK_AUDIT)

                val auditMemberEntities = audit.members.map { member ->
                    MediaLinkAuditMemberEntity(
                        auditId = auditId,
                        source = member.source,
                        mediaType = member.mediaType,
                        externalId = member.externalId
                    )
                }
                mediaLinkDao.insertAuditMembers(auditMemberEntities)
                failureInjector.check(RestoreStage.LINK_AUDIT_MEMBERS)
            }
        }
    }

    private fun BackupRef.key(): String = "${source.name}:$externalId"
}

internal data class ExportedBackup(val bytes: ByteArray, val filename: String)

@Singleton
internal class BackupExporter @Inject constructor(
    private val dataStore: BackupDataStore,
    private val clock: java.time.Clock
) {
    suspend fun export(): ExportedBackup {
        val exportedAt = clock.instant()
        val document = BackupDocument(BACKUP_FORMAT_ID, BACKUP_SCHEMA_VERSION, exportedAt, dataStore.readPortableData())
        return withContext(Dispatchers.Default) {
            check(BackupValidator.validate(document) is BackupValidationResult.Success)
            ExportedBackup(
                bytes = BackupJsonCodec.encode(document),
                filename = "bingee-backup-${exportedAt.atZone(java.time.ZoneOffset.UTC).toLocalDate()}.json"
            )
        }
    }
}
