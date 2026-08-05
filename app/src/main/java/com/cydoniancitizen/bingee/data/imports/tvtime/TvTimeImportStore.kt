package com.cydoniancitizen.bingee.data.imports.tvtime

import androidx.room.withTransaction
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.result.AppError
import com.cydoniancitizen.bingee.core.result.AppResult
import com.cydoniancitizen.bingee.data.calendar.ReleaseEventProjector
import com.cydoniancitizen.bingee.data.details.toCacheWrite
import com.cydoniancitizen.bingee.data.imports.model.ImportWarning
import com.cydoniancitizen.bingee.data.imports.model.ImportedSourceIdentity
import com.cydoniancitizen.bingee.data.library.local.BingeeDatabase
import com.cydoniancitizen.bingee.data.library.local.ImportProgressDao
import com.cydoniancitizen.bingee.data.library.local.ImportProgressWriteOutcome
import com.cydoniancitizen.bingee.data.library.local.ImportProvenanceDao
import com.cydoniancitizen.bingee.data.library.local.ImportProvenanceRefEntity
import com.cydoniancitizen.bingee.data.library.local.LibraryDao
import com.cydoniancitizen.bingee.data.library.local.SeriesDao
import com.cydoniancitizen.bingee.data.series.toEntity
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

internal data class TvTimeTimestampConflict(val title: String, val episode: Boolean)

internal data class TvTimeImportReport(
    val newLibraryTitles: List<String>,
    val alreadyPresentTitles: List<String>,
    val movieProgressAdded: Int,
    val movieProgressPreserved: Int,
    val episodeProgressAdded: Int,
    val episodeProgressPreserved: Int,
    val timestampConflicts: List<TvTimeTimestampConflict>,
    val skippedRecordIds: List<String>,
    val invalidRecordCount: Int,
    val unmatchedRecordIds: List<String>,
    val unsupported: com.cydoniancitizen.bingee.data.imports.model.ImportedUnsupportedFields,
    val approximatedMembershipTitles: List<String> = emptyList(),
    val warnings: List<ImportWarning>
)

internal data class TvTimeImportPreview(
    val newLibraryCount: Int,
    val existingLibraryCount: Int,
    val movieProgressToAdd: Int,
    val episodeProgressToAdd: Int,
    val timestampConflictCount: Int,
    val skippedCount: Int,
    val invalidRecordCount: Int,
    val unsupported: com.cydoniancitizen.bingee.data.imports.model.ImportedUnsupportedFields,
    val approximatedMembershipCount: Int = 0
)

internal enum class TvTimeImportWriteStage {
    MEDIA_METADATA,
    LIBRARY_MEMBERSHIP,
    MEDIA_PROVENANCE,
    MEDIA_RELEASE_EVENT,
    MOVIE_PROGRESS,
    SEASON_SUMMARIES,
    SEASON_RELEASE_EVENTS,
    EPISODE_METADATA,
    EPISODE_PROVENANCE,
    EPISODE_RELEASE_EVENT,
    EPISODE_PROGRESS
}

internal fun interface TvTimeImportFailureInjector {
    fun check(stage: TvTimeImportWriteStage)
}

@Singleton
internal class TvTimeImportStore @Inject constructor(
    private val database: BingeeDatabase,
    private val libraryDao: LibraryDao,
    private val detailsDao: com.cydoniancitizen.bingee.data.library.local.DetailsDao,
    private val seriesDao: SeriesDao,
    private val importProgressDao: ImportProgressDao,
    private val provenanceDao: ImportProvenanceDao,
    private val releaseEventDao: com.cydoniancitizen.bingee.data.library.local.ReleaseEventDao,
    private val projector: ReleaseEventProjector
) {
    suspend fun preview(plan: TvTimeImportPlan): TvTimeImportPreview =
        database.withTransaction { calculatePreview(plan) }

    private suspend fun calculatePreview(plan: TvTimeImportPlan): TvTimeImportPreview {
        var newLibrary = 0
        var existingLibrary = 0
        var movieProgress = 0
        var conflicts = 0
        var approximatedMemberships = 0
        plan.media.forEach { change ->
            if (libraryDao.isInLibrary(MediaSource.TMDB, change.candidate.externalRef.externalId)) {
                existingLibrary++
            } else {
                newLibrary++
                if (change.source.createdAt == null) approximatedMemberships++
            }
            val watchedAt = change.source.watchedAt
            if (watchedAt != null) {
                val existing = importProgressDao.getMovieState(
                    MediaSource.TMDB,
                    change.candidate.externalRef.externalId
                )
                if (existing?.watchedAt == null) {
                    movieProgress++
                } else if (existing.watchedAt != watchedAt) {
                    conflicts++
                }
            }
        }
        var episodeProgress = 0
        plan.episodes.forEach { change ->
            val existing = importProgressDao.getEpisodeState(
                MediaSource.TMDB,
                change.episode.externalRef.externalId
            )
            if (existing?.watchedAt ==
                null
            ) {
                episodeProgress++
            } else if (existing.watchedAt != change.source.watchedAt) {
                conflicts++
            }
        }
        return TvTimeImportPreview(
            newLibraryCount = newLibrary,
            existingLibraryCount = existingLibrary,
            movieProgressToAdd = movieProgress,
            episodeProgressToAdd = episodeProgress,
            timestampConflictCount = conflicts,
            skippedCount = plan.skippedRecordIds.size,
            invalidRecordCount = plan.invalidRecordCount,
            unsupported = plan.unsupported,
            approximatedMembershipCount = approximatedMemberships
        )
    }

    suspend fun import(
        plan: TvTimeImportPlan,
        expectedPreview: TvTimeImportPreview? = null,
        failureInjector: TvTimeImportFailureInjector = TvTimeImportFailureInjector {}
    ): AppResult<TvTimeImportReport> = try {
        check(plan.profileId == TV_TIME_PROFILE_ID) { "Unsupported TV Time import profile" }
        val report = database.withTransaction {
            check(expectedPreview == null || calculatePreview(plan) == expectedPreview) {
                "Local data changed after TV Time import preview"
            }
            val newLibrary = mutableListOf<String>()
            val existingLibrary = mutableListOf<String>()
            val timestampConflicts = mutableListOf<TvTimeTimestampConflict>()
            val approximatedMembershipTitles = mutableListOf<String>()
            var movieAdded = 0
            var moviePreserved = 0
            var episodeAdded = 0
            var episodePreserved = 0

            plan.media.forEach { change ->
                val ref = change.candidate.externalRef
                val wasMember = libraryDao.isInLibrary(MediaSource.TMDB, ref.externalId)
                val fetchedAt = plan.confirmedAt
                val write = change.details.toCacheWrite(fetchedAt)
                detailsDao.storeDetails(
                    candidate = write.media,
                    source = MediaSource.TMDB,
                    externalId = ref.externalId,
                    details = write.details,
                    genres = write.genres
                )
                failureInjector.check(TvTimeImportWriteStage.MEDIA_METADATA)
                check(
                    libraryDao.addExistingToLibrary(
                        MediaSource.TMDB,
                        ref.externalId,
                        change.source.createdAt ?: plan.confirmedAt
                    ) !=
                        null
                )
                failureInjector.check(TvTimeImportWriteStage.LIBRARY_MEMBERSHIP)
                if (!wasMember && change.source.createdAt == null) {
                    approximatedMembershipTitles += change.source.title
                }
                if (wasMember) existingLibrary += change.source.title else newLibrary += change.source.title
                val media = checkNotNull(libraryDao.getMediaByExternalRef(MediaSource.TMDB, ref.externalId))
                change.source.identities.forEach { identity ->
                    provenanceDao.add(identity.toMediaRef(media.localMediaId))
                }
                failureInjector.check(TvTimeImportWriteStage.MEDIA_PROVENANCE)
                if (change.details.mediaType == com.cydoniancitizen.bingee.core.model.MediaType.MOVIE) {
                    releaseEventDao.reconcileMovie(ref, projector.movie(change.details, fetchedAt))
                    failureInjector.check(TvTimeImportWriteStage.MEDIA_RELEASE_EVENT)
                    val watchedAt = change.source.watchedAt
                    if (watchedAt != null) {
                        val result = importProgressDao.addMovieProgress(MediaSource.TMDB, ref.externalId, watchedAt)
                        when (result) {
                            ImportProgressWriteOutcome.ADDED -> movieAdded++
                            ImportProgressWriteOutcome.PRESERVED -> moviePreserved++
                            ImportProgressWriteOutcome.CONFLICT -> {
                                moviePreserved++
                                timestampConflicts += TvTimeTimestampConflict(change.source.title, false)
                            }
                            ImportProgressWriteOutcome.NOT_FOUND -> error("Canonical movie disappeared")
                        }
                        failureInjector.check(TvTimeImportWriteStage.MOVIE_PROGRESS)
                    }
                } else {
                    seriesDao.upsertSeasonSummaries(
                        MediaSource.TMDB,
                        ref.externalId,
                        change.seasons.map { it.toEntity(fetchedAt) }
                    )
                    failureInjector.check(TvTimeImportWriteStage.SEASON_SUMMARIES)
                    change.seasons.forEach { season ->
                        releaseEventDao.reconcileSeason(season.externalRef, projector.season(season, fetchedAt))
                    }
                    failureInjector.check(TvTimeImportWriteStage.SEASON_RELEASE_EVENTS)
                }
            }

            plan.episodes.groupBy { "${it.episode.seriesRef.externalId}:${it.season.seasonNumber}" }
                .values.forEach { changes ->
                    val first = changes.first()
                    val seriesRef = first.episode.seriesRef
                    val season = first.season
                    seriesDao.storeSeasonEpisodes(
                        source = MediaSource.TMDB,
                        seriesExternalId = seriesRef.externalId,
                        season = season.toEntity(plan.confirmedAt),
                        episodes = changes.map { it.episode.toEntity(plan.confirmedAt) },
                        fetchedAt = plan.confirmedAt
                    )
                    failureInjector.check(TvTimeImportWriteStage.EPISODE_METADATA)
                    checkNotNull(seriesDao.getSeason(MediaSource.TMDB, season.externalRef.externalId))
                    releaseEventDao.reconcileSeason(season.externalRef, projector.season(season, plan.confirmedAt))
                    changes.forEach { change ->
                        val storedEpisode = checkNotNull(
                            seriesDao.getEpisodeForImport(MediaSource.TMDB, change.episode.externalRef.externalId)
                        )
                        change.source.identities.forEach { identity ->
                            provenanceDao.add(identity.toEpisodeRef(storedEpisode.localEpisodeId))
                        }
                        failureInjector.check(TvTimeImportWriteStage.EPISODE_PROVENANCE)
                        releaseEventDao.reconcileEpisode(
                            change.episode.externalRef,
                            projector.episode(change.episode, plan.confirmedAt)
                        )
                        failureInjector.check(TvTimeImportWriteStage.EPISODE_RELEASE_EVENT)
                        val result = importProgressDao.addEpisodeProgress(
                            MediaSource.TMDB,
                            change.episode.externalRef.externalId,
                            change.source.watchedAt
                        )
                        when (result) {
                            ImportProgressWriteOutcome.ADDED -> episodeAdded++
                            ImportProgressWriteOutcome.PRESERVED -> episodePreserved++
                            ImportProgressWriteOutcome.CONFLICT -> {
                                episodePreserved++
                                timestampConflicts += TvTimeTimestampConflict(change.source.title, true)
                            }
                            ImportProgressWriteOutcome.NOT_FOUND -> error("Canonical episode disappeared")
                        }
                        failureInjector.check(TvTimeImportWriteStage.EPISODE_PROGRESS)
                    }
                }

            TvTimeImportReport(
                newLibraryTitles = newLibrary,
                alreadyPresentTitles = existingLibrary,
                movieProgressAdded = movieAdded,
                movieProgressPreserved = moviePreserved,
                episodeProgressAdded = episodeAdded,
                episodeProgressPreserved = episodePreserved,
                timestampConflicts = timestampConflicts,
                skippedRecordIds = plan.skippedRecordIds,
                invalidRecordCount = plan.invalidRecordCount,
                unmatchedRecordIds = plan.unmatchedRecordIds,
                unsupported = plan.unsupported,
                warnings = plan.warnings,
                approximatedMembershipTitles = approximatedMembershipTitles
            )
        }
        AppResult.Success(report)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: android.database.sqlite.SQLiteException) {
        AppResult.Failure(AppError.LocalStorageFailure)
    } catch (_: IllegalArgumentException) {
        AppResult.Failure(AppError.CorruptedData)
    } catch (_: IllegalStateException) {
        AppResult.Failure(AppError.CorruptedData)
    } catch (_: Exception) {
        AppResult.Failure(AppError.Unknown)
    }
}

private fun ImportedSourceIdentity.toMediaRef(localMediaId: Long): ImportProvenanceRefEntity =
    ImportProvenanceRefEntity(
        namespace = namespace.name,
        externalId = value,
        targetType = "MEDIA",
        localMediaId = localMediaId
    )

private fun ImportedSourceIdentity.toEpisodeRef(localEpisodeId: Long): ImportProvenanceRefEntity =
    ImportProvenanceRefEntity(
        namespace = namespace.name,
        externalId = value,
        targetType = "EPISODE",
        localEpisodeId = localEpisodeId
    )
