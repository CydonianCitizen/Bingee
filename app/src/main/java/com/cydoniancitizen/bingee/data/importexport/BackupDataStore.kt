package com.cydoniancitizen.bingee.data.importexport

import androidx.room.withTransaction
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.data.library.local.BingeeDatabase
import com.cydoniancitizen.bingee.data.library.local.EpisodeEntity
import com.cydoniancitizen.bingee.data.library.local.EpisodeWatchProgressEntity
import com.cydoniancitizen.bingee.data.library.local.ExternalRefEntity
import com.cydoniancitizen.bingee.data.library.local.LibraryMembershipEntity
import com.cydoniancitizen.bingee.data.library.local.MediaEntity
import com.cydoniancitizen.bingee.data.library.local.MediaRatingEntity
import com.cydoniancitizen.bingee.data.library.local.MovieWatchProgressEntity
import com.cydoniancitizen.bingee.data.library.local.PortablePreferencesEntity
import com.cydoniancitizen.bingee.data.library.local.PortableSnapshotDao
import com.cydoniancitizen.bingee.data.library.local.ReleaseEventDao
import com.cydoniancitizen.bingee.data.library.local.SeasonEntity
import com.cydoniancitizen.bingee.data.library.local.SeriesStateOverrideEntity
import com.cydoniancitizen.bingee.data.library.local.SeriesWatchProgressEntity
import com.cydoniancitizen.bingee.data.settings.DataStoreReleaseNotificationPreferences
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

internal enum class RestoreStage {
    MEDIA,
    EXTERNAL_REFERENCES,
    SEASONS,
    EPISODES,
    LIBRARY_MEMBERSHIP,
    MOVIE_PROGRESS,
    SERIES_PROGRESS,
    SERIES_STATE,
    EPISODE_PROGRESS,
    RATINGS,
    PORTABLE_PREFERENCES,
    RELEASE_EVENTS
}

internal fun interface RestoreFailureInjector {
    fun check(stage: RestoreStage)
}

@Singleton
internal class BackupDataStore @Inject constructor(
    private val database: BingeeDatabase,
    private val snapshotDao: PortableSnapshotDao,
    private val releaseEventDao: ReleaseEventDao,
    private val notificationPreferences: DataStoreReleaseNotificationPreferences
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

            val portableMediaIds = buildSet {
                addAll(rows.memberships.map { it.localMediaId })
                addAll(rows.ratings.map { it.localMediaId })
                addAll(rows.movieProgress.map { it.localMediaId })
                addAll(rows.seriesProgress.map { it.localMediaId })
                addAll(rows.seriesStateOverrides.map { it.localMediaId })
                addAll(episodeMediaIds)
                addAll(rows.media.filter { it.isFavorite }.map { it.localMediaId })
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
                    releaseDate = entity.releaseDate,
                    isFavorite = entity.isFavorite
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
                    .map { BackupMovieProgress(mediaRefById.getValue(it.localMediaId), it.watchedAt, it.watchedDate) }
                    .sortedWith(compareBy({ it.mediaRef.source.name }, { it.mediaRef.externalId })),
                seriesProgress = rows.seriesProgress.filter { it.localMediaId in portableMediaIds }
                    .map {
                        BackupSeriesProgress(mediaRefById.getValue(it.localMediaId), it.completedAt, it.watchedDate)
                    }
                    .sortedWith(compareBy({ it.mediaRef.source.name }, { it.mediaRef.externalId })),
                abandonedSeries = rows.seriesStateOverrides
                    .filter { it.isAbandoned && it.localMediaId in portableMediaIds }
                    .map { BackupAbandonedSeries(mediaRefById.getValue(it.localMediaId)) }
                    .sortedWith(compareBy({ it.mediaRef.source.name }, { it.mediaRef.externalId })),
                episodeProgress = rows.episodeProgress.filter { it.localEpisodeId in episodeRefById }
                    .map { BackupEpisodeProgress(episodeRefById.getValue(it.localEpisodeId), it.watchedAt) }
                    .sortedWith(compareBy({ it.episodeRef.source.name }, { it.episodeRef.externalId })),
                ratings = rows.ratings.filter { it.localMediaId in portableMediaIds }
                    .map {
                        BackupRating(mediaRefById.getValue(it.localMediaId), it.ratingValue, it.ratedAt, it.updatedAt)
                    }
                    .sortedWith(compareBy({ it.mediaRef.source.name }, { it.mediaRef.externalId })),
                preferences = dataPreferences
            )
        }
    }

    suspend fun currentLibraryCount(): Int = snapshotDao.countLibraryEntries()

    suspend fun restore(
        plan: ValidatedBackupPlan,
        failureInjector: RestoreFailureInjector = RestoreFailureInjector {}
    ) {
        database.withTransaction {
            val data = plan.document.data
            val exportedAt = plan.document.exportedAt
            snapshotDao.deleteNotificationDeliveries()
            snapshotDao.deleteReleaseEvents()
            snapshotDao.deleteEpisodeProgress()
            snapshotDao.deleteMovieProgress()
            snapshotDao.deleteSeriesProgress()
            snapshotDao.deleteSeriesStateOverrides()
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
                        metadataUpdatedAt = exportedAt,
                        isFavorite = media.isFavorite
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

            data.abandonedSeries.forEach { abandoned ->
                snapshotDao.insertSeriesStateOverride(
                    SeriesStateOverrideEntity(checkNotNull(mediaIds[abandoned.mediaRef.key()]))
                )
            }
            failureInjector.check(RestoreStage.SERIES_STATE)

            data.movieProgress.forEach { progress ->
                snapshotDao.insertMovieProgress(
                    MovieWatchProgressEntity(
                        localMediaId = checkNotNull(mediaIds[progress.mediaRef.key()]),
                        watchedAt = progress.watchedAt,
                        watchedDate = progress.watchedDate
                    )
                )
            }
            failureInjector.check(RestoreStage.MOVIE_PROGRESS)

            data.seriesProgress.forEach { progress ->
                snapshotDao.insertSeriesProgress(
                    SeriesWatchProgressEntity(
                        localMediaId = checkNotNull(mediaIds[progress.mediaRef.key()]),
                        watchedDate = progress.watchedDate,
                        completedAt = progress.completedAt
                    )
                )
            }
            failureInjector.check(RestoreStage.SERIES_PROGRESS)

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
            failureInjector.check(RestoreStage.RELEASE_EVENTS)
        }
    }

    suspend fun createPortableBackup(exportedAt: Instant): ByteArray = withContext(Dispatchers.IO) {
        val document = BackupDocument(
            formatId = BACKUP_FORMAT_ID,
            schemaVersion = BACKUP_SCHEMA_VERSION,
            exportedAt = exportedAt,
            data = readPortableData()
        )
        BackupJsonCodec.encode(document)
    }

    private fun BackupRef.key(): String = "${source.name}:$externalId"
}
