package com.cydoniancitizen.bingee.data.library

import android.database.sqlite.SQLiteException
import com.cydoniancitizen.bingee.core.model.ContinueWatchingItem
import com.cydoniancitizen.bingee.core.model.EpisodePosition
import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.LibraryEntry
import com.cydoniancitizen.bingee.core.model.LibraryQuery
import com.cydoniancitizen.bingee.core.model.MediaSearchResult
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.core.model.SeriesProgress
import com.cydoniancitizen.bingee.core.model.applyLibraryStateAndSort
import com.cydoniancitizen.bingee.core.model.normalizeLibrarySearch
import com.cydoniancitizen.bingee.core.result.AppError
import com.cydoniancitizen.bingee.core.result.AppResult
import com.cydoniancitizen.bingee.data.library.local.LibraryDao
import com.cydoniancitizen.bingee.data.library.local.MediaEntity
import com.cydoniancitizen.bingee.data.library.local.ProgressWriteOutcome
import com.cydoniancitizen.bingee.data.library.local.RatingDao
import com.cydoniancitizen.bingee.data.library.local.WatchProgressDao
import com.cydoniancitizen.bingee.domain.policy.ContinueWatchingPolicy
import com.cydoniancitizen.bingee.domain.repository.LibraryRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

@Singleton
internal class DefaultLibraryRepository @Inject constructor(
    private val libraryDao: LibraryDao,
    private val watchProgressDao: WatchProgressDao,
    private val ratingDao: RatingDao,
    private val clock: Clock
) : LibraryRepository {
    override fun observeEntries(query: LibraryQuery): Flow<AppResult<List<LibraryEntry>>> {
        val items = libraryDao.observeLibraryItems(
            query.mediaFilter.mediaType,
            query.searchQuery.toSqlLikePattern()
        )
        return combine(
            items,
            libraryDao.observeLibraryProgress(LocalDate.now(clock)),
            ratingDao.observeActiveLibraryRatings()
        ) { rows, progress, ratings ->
            val progressByMedia = progress.associateBy { it.localMediaId }
            val ratingsByMedia = ratings.associateBy { it.localMediaId }
            val entries = rows.map { row ->
                val localMediaId = row.media.localMediaId
                row.toDomain(
                    progressRow = progressByMedia[localMediaId],
                    rating = ratingsByMedia[localMediaId]
                )
            }
            applyLibraryStateAndSort(entries, query)
        }.asPersistenceResult { it }
    }

    override fun observeEntryCount(): Flow<AppResult<Int>> =
        libraryDao.observeLibraryEntryCount().asPersistenceResult { it }

    override fun observeEntry(ref: ExternalMediaRef): Flow<AppResult<LibraryEntry?>> {
        val normalized = ref.normalized()
        return combine(
            libraryDao.observeLibraryItem(normalized.source, normalized.externalId),
            libraryDao.observeLibraryProgress(LocalDate.now(clock))
        ) { row, progress ->
            row?.toDomain(
                preferredRef = normalized,
                progressRow = progress.firstOrNull { it.localMediaId == row.media.localMediaId }
            )
        }.asPersistenceResult { it }
    }

    override fun observeMembershipRefs(): Flow<AppResult<Set<ExternalMediaRef>>> =
        libraryDao.observeMembershipRefs().asPersistenceResult { rows ->
            rows.mapTo(linkedSetOf()) { it.toDomain() }
        }

    override fun observeContinueWatching(): Flow<AppResult<List<ContinueWatchingItem>>> =
        libraryDao.observeContinueWatchingRows(MediaSource.TMDB, LocalDate.now(clock))
            .asPersistenceResult { rows -> ContinueWatchingPolicy.select(rows.map { it.toDomain() }) }

    override suspend fun add(result: MediaSearchResult): AppResult<LibraryEntry> {
        val prepared =
            try {
                val now = clock.instant()
                PreparedLibraryAdd(
                    ref = result.externalRef.normalized(),
                    media = result.toMediaEntity(now),
                    addedAt = now
                )
            } catch (_: IllegalArgumentException) {
                return AppResult.Failure(AppError.InvalidInput)
            }
        return persistenceRead {
            val row = libraryDao.addToLibrary(
                prepared.media,
                prepared.ref.source,
                prepared.ref.externalId,
                prepared.addedAt
            )
            row.toDomain(preferredRef = prepared.ref)
        }
    }

    override suspend fun add(ref: ExternalMediaRef): AppResult<LibraryEntry> =
        withNormalizedExternalId(ref) { externalId ->
            val now = clock.instant()
            try {
                val existingItem = libraryDao.addExistingToLibrary(ref.source, externalId, now)
                if (existingItem != null) {
                    AppResult.Success(existingItem.toDomain(preferredRef = ExternalMediaRef(ref.source, externalId)))
                } else {
                    AppResult.Failure(AppError.MissingData)
                }
            } catch (_: IllegalArgumentException) {
                AppResult.Failure(AppError.CorruptedData)
            } catch (_: IllegalStateException) {
                AppResult.Failure(AppError.CorruptedData)
            } catch (_: SQLiteException) {
                AppResult.Failure(AppError.LocalStorageFailure)
            } catch (_: Exception) {
                AppResult.Failure(AppError.Unknown)
            }
        }

    override suspend fun remove(ref: ExternalMediaRef): AppResult<Unit> = withNormalizedExternalId(ref) { externalId ->
        persistenceRead {
            libraryDao.removeMembership(ref.source, externalId)
            Unit
        }
    }

    override suspend fun isInLibrary(ref: ExternalMediaRef): AppResult<Boolean> =
        withNormalizedExternalId(ref) { externalId ->
            persistenceRead { libraryDao.isInLibrary(ref.source, externalId) }
        }

    override suspend fun setFavorite(ref: ExternalMediaRef, isFavorite: Boolean): AppResult<Unit> =
        withNormalizedExternalId(ref) { externalId ->
            persistenceRead {
                val updated = libraryDao.updateFavoriteState(ref.source, externalId, isFavorite)
                if (updated == 0) {
                    throw IllegalStateException("Media entity not found for favorite state update")
                }
            }
        }

    override suspend fun setFavorite(result: MediaSearchResult, isFavorite: Boolean): AppResult<Unit> =
        withNormalizedExternalId(result.externalRef) { externalId ->
            persistenceRead {
                val now = clock.instant()
                libraryDao.ensureMediaAndSetFavorite(
                    candidate = result.toMediaEntity(now),
                    source = result.externalRef.source,
                    externalId = externalId,
                    isFavorite = isFavorite
                )
            }
        }

    override suspend fun setWatchedDate(ref: ExternalMediaRef, watchedDate: LocalDate?): AppResult<Unit> =
        withNormalizedExternalId(ref) { externalId ->
            persistenceRead {
                val outcome = watchProgressDao.setMediaWatchedDate(
                    source = ref.source,
                    externalId = externalId,
                    watchedDate = watchedDate,
                    now = clock.instant()
                )
                if (outcome == ProgressWriteOutcome.NOT_FOUND) {
                    throw IllegalStateException("Media entity not found for watched date update")
                }
            }
        }

    override suspend fun setSeriesAbandoned(ref: ExternalMediaRef, isAbandoned: Boolean): AppResult<Unit> =
        withNormalizedExternalId(ref) { externalId ->
            persistenceRead {
                when (libraryDao.setSeriesAbandoned(ref.source, externalId, isAbandoned)) {
                    ProgressWriteOutcome.SUCCESS -> Unit
                    ProgressWriteOutcome.NOT_FOUND -> throw IllegalStateException("Media entity not found")
                    ProgressWriteOutcome.NOT_IN_LIBRARY -> throw IllegalArgumentException("Series is not in library")
                    ProgressWriteOutcome.MEDIA_TYPE_MISMATCH -> throw IllegalArgumentException("Series required")
                    ProgressWriteOutcome.NOT_TRACKABLE,
                    ProgressWriteOutcome.INCOMPLETE -> throw IllegalStateException("Invalid series state")
                }
            }
        }
}

private fun LibraryDao.ContinueWatchingRow.toDomain() = ContinueWatchingItem(
    mediaRef = ExternalMediaRef(source, externalId),
    mediaType = mediaType,
    title = title,
    posterUrl = posterUrl,
    progress = SeriesProgress(
        watchedEpisodes = watchedEpisodes,
        trackableEpisodes = trackableEpisodes,
        completedSeasons = completedSeasons,
        trackableSeasons = trackableSeasons,
        isComplete = trackableEpisodes > 0 && watchedEpisodes == trackableEpisodes && hasSufficientCoverage
    ),
    nextEpisode = if (nextSeasonNumber != null && nextEpisodeNumber != null) {
        EpisodePosition(nextSeasonNumber, nextEpisodeNumber)
    } else {
        null
    },
    updatedAt = lastProgressAt,
    isAbandoned = isAbandoned
)

internal fun String.toSqlLikePattern(): String {
    val normalized = normalizeLibrarySearch(this)
    if (normalized.isEmpty()) return "%"
    val escaped = buildString(normalized.length) {
        normalized.forEach { character ->
            if (character == '\\' || character == '%' || character == '_') append('\\')
            append(character)
        }
    }
    return "%$escaped%"
}

private data class PreparedLibraryAdd(val ref: ExternalMediaRef, val media: MediaEntity, val addedAt: Instant)

private fun ExternalMediaRef.normalized(): ExternalMediaRef =
    ExternalMediaRef(source = source, externalId = normalizedExternalId())

private fun ExternalMediaRef.normalizedExternalId(): String =
    externalId.trim().also { require(it.isNotEmpty()) { "External media ID must not be blank" } }

private fun <T, R> Flow<T>.asPersistenceResult(transform: (T) -> R): Flow<AppResult<R>> =
    map<T, AppResult<R>> { value -> AppResult.Success(transform(value)) }
        .catch { throwable ->
            if (throwable is CancellationException) throw throwable
            val error =
                when (throwable) {
                    is IllegalArgumentException,
                    is IllegalStateException -> AppError.CorruptedData
                    is SQLiteException -> AppError.LocalStorageFailure
                    else -> AppError.Unknown
                }
            emit(AppResult.Failure(error))
        }

private suspend fun <T> persistenceRead(block: suspend () -> T): AppResult<T> = try {
    AppResult.Success(block())
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: IllegalArgumentException) {
    AppResult.Failure(AppError.CorruptedData)
} catch (_: SQLiteException) {
    AppResult.Failure(AppError.LocalStorageFailure)
} catch (_: Exception) {
    AppResult.Failure(AppError.Unknown)
}

private suspend fun <T> withNormalizedExternalId(
    ref: ExternalMediaRef,
    block: suspend (String) -> AppResult<T>
): AppResult<T> = try {
    block(ref.normalizedExternalId())
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: IllegalArgumentException) {
    AppResult.Failure(AppError.InvalidInput)
}
