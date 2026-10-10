package com.cydoniancitizen.bingee.data.library

import com.cydoniancitizen.bingee.core.model.ContinueWatchingItem
import com.cydoniancitizen.bingee.core.model.EpisodePosition
import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.LibraryEntry
import com.cydoniancitizen.bingee.core.model.LibraryQuery
import com.cydoniancitizen.bingee.core.model.MediaSearchResult
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.core.model.PersonalViewingEntry
import com.cydoniancitizen.bingee.core.model.SeriesProgress
import com.cydoniancitizen.bingee.core.model.applyLibraryStateAndSort
import com.cydoniancitizen.bingee.core.model.distinctByCanonicalIdentity
import com.cydoniancitizen.bingee.core.model.isSeriesComplete
import com.cydoniancitizen.bingee.core.model.normalizeLibrarySearch
import com.cydoniancitizen.bingee.core.model.tmdbIdOrNull
import com.cydoniancitizen.bingee.core.result.AppError
import com.cydoniancitizen.bingee.core.result.AppResult
import com.cydoniancitizen.bingee.data.library.local.LibraryDao
import com.cydoniancitizen.bingee.data.library.local.MediaEntity
import com.cydoniancitizen.bingee.data.library.local.ProgressWriteOutcome
import com.cydoniancitizen.bingee.data.library.local.RatingDao
import com.cydoniancitizen.bingee.data.library.local.WatchProgressDao
import com.cydoniancitizen.bingee.data.toPersistenceError
import com.cydoniancitizen.bingee.di.DefaultDispatcher
import com.cydoniancitizen.bingee.domain.calendar.CalendarDateSource
import com.cydoniancitizen.bingee.domain.policy.ContinueWatchingPolicy
import com.cydoniancitizen.bingee.domain.repository.LibraryRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.shareIn

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
internal class DefaultLibraryRepository @Inject constructor(
    private val libraryDao: LibraryDao,
    private val watchProgressDao: WatchProgressDao,
    private val ratingDao: RatingDao,
    private val clock: Clock,
    private val dateSource: CalendarDateSource,
    @param:DefaultDispatcher private val projectionDispatcher: CoroutineDispatcher
) : LibraryRepository {
    private val progressScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * The library progress projection is the heaviest query in the app, and the collection, Details and
     * personal-viewing paths all need the same rows for the same day. Sharing one subscription keeps Room
     * from running it once per collector on every invalidation.
     *
     * Failures travel as values because a shared flow cannot deliver an upstream throwable to its
     * subscribers; each caller unwraps with [Result.getOrThrow] so [asPersistenceResult] maps the error
     * per emission without ending the observation when the shared read retries.
     */
    private val libraryProgress: Flow<Result<List<LibraryDao.LibraryProgressRow>>> =
        dateSource.observeDate()
            .flatMapLatest { today -> libraryDao.observeLibraryProgress(today).map { Result.success(it) } }
            // ponytail: fixed-delay resubscribe, enough for a transient read failure; add backoff only if
            // a real failure mode turns out to persist for longer than a user waits on the retry button.
            .retryWhen { cause, _ ->
                emit(Result.failure(cause))
                delay(PROGRESS_RETRY_DELAY_MILLIS)
                true
            }
            .shareIn(
                scope = progressScope,
                started = SharingStarted.WhileSubscribed(stopTimeoutMillis = PROGRESS_STOP_TIMEOUT_MILLIS),
                replay = 1
            )

    override fun observeEntries(query: LibraryQuery): Flow<AppResult<List<LibraryEntry>>> = combine(
        libraryDao.observeLibraryItems(
            query.mediaFilter.mediaType,
            query.searchQuery.toSqlLikePattern()
        ),
        libraryProgress,
        ratingDao.observeActiveLibraryRatings()
    ) { rows, progressResult, ratings ->
        progressResult.map { progressRows ->
            val progressByMedia = progressRows.associateBy { it.localMediaId }
            val ratingsByMedia = ratings.associateBy { it.localMediaId }
            val entries = rows.map { row ->
                val localMediaId = row.media.localMediaId
                row.toDomain(
                    progressRow = progressByMedia[localMediaId],
                    rating = ratingsByMedia[localMediaId]
                )
            }
            applyLibraryStateAndSort(entries, query)
        }
    }.asPersistenceResult { it.getOrThrow() }.flowOn(projectionDispatcher)

    override fun observeEntry(ref: ExternalMediaRef, mediaType: MediaType): Flow<AppResult<LibraryEntry?>> {
        val normalized = ref.normalized()
        return combine(
            libraryDao.observeLibraryItem(normalized.source, mediaType, normalized.externalId),
            libraryProgress
        ) { row, progressResult ->
            progressResult.map { progressRows ->
                row?.toDomain(
                    preferredRef = normalized,
                    progressRow = progressRows.firstOrNull { it.localMediaId == row.media.localMediaId }
                )
            }
        }.asPersistenceResult { it.getOrThrow() }
    }

    override fun observeMembershipRefs(): Flow<AppResult<Set<Pair<ExternalMediaRef, MediaType>>>> =
        libraryDao.observeMembershipRefs().asPersistenceResult { rows ->
            rows.mapTo(linkedSetOf()) { it.toDomain() to it.mediaType }
        }

    override fun observePersonalViewing(): Flow<AppResult<List<PersonalViewingEntry>>> = combine(
        libraryDao.observePersonalViewing(),
        libraryProgress,
        libraryDao.observePersonalViewingGenres(),
        libraryDao.observePersonalViewingActivities()
    ) { rows, progressResult, genreRows, activityRows ->
        progressResult.map { progressRows ->
            val progressByMedia = progressRows.associateBy { it.localMediaId }
            val genresByMedia = genreRows.mapNotNull { row ->
                row.toDomainOrNull()?.let { row.localMediaId to it }
            }.groupBy({ it.first }, { it.second })
                .mapValues { (_, genres) -> genres.distinctByCanonicalIdentity() }
            val activitiesByMedia = activityRows.groupBy { it.localMediaId }
            rows.map { row ->
                row.toDomain(
                    currentProgress = progressByMedia[row.media.localMediaId],
                    genres = genresByMedia[row.media.localMediaId].orEmpty(),
                    watchedRegularEpisodeActivities = activitiesByMedia[row.media.localMediaId]
                        .orEmpty()
                        .map { it.toDomain() }
                )
            }
        }
    }.asPersistenceResult { it.getOrThrow() }

    override fun observeContinueWatching(): Flow<AppResult<List<ContinueWatchingItem>>> =
        dateSource.observeDate().flatMapLatest { today ->
            libraryDao.observeContinueWatchingRows(MediaSource.TMDB, today)
        }.asPersistenceResult { rows -> ContinueWatchingPolicy.select(rows.map { it.toDomain() }) }

    override suspend fun add(result: MediaSearchResult): AppResult<LibraryEntry> {
        val prepared =
            try {
                if (result.externalRef.tmdbIdOrNull() == null) {
                    return AppResult.Failure(result.externalRef.invalidRuntimeTmdbError())
                }
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

    override suspend fun add(ref: ExternalMediaRef, mediaType: MediaType): AppResult<LibraryEntry> =
        ref.withValidTmdbId { externalId ->
            val now = clock.instant()
            try {
                val existingItem = libraryDao.addExistingToLibrary(ref.source, mediaType, externalId, now)
                if (existingItem != null) {
                    AppResult.Success(existingItem.toDomain(preferredRef = ExternalMediaRef(ref.source, externalId)))
                } else {
                    AppResult.Failure(AppError.MissingData)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (throwable: Exception) {
                AppResult.Failure(throwable.toPersistenceError())
            }
        }

    override suspend fun remove(ref: ExternalMediaRef, mediaType: MediaType): AppResult<Unit> =
        withNormalizedExternalId(ref) { externalId ->
            persistenceRead {
                libraryDao.removeMembership(ref.source, mediaType, externalId)
                Unit
            }
        }

    override suspend fun setFavorite(
        ref: ExternalMediaRef,
        mediaType: MediaType,
        isFavorite: Boolean
    ): AppResult<Unit> = withNormalizedExternalId(ref) { externalId ->
        persistenceRead {
            val updated = libraryDao.updateFavoriteState(
                ref.source,
                mediaType,
                externalId,
                isFavorite,
                clock.instant()
            )
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
                    candidate = result.toMediaEntity(now).copy(favoriteAddedAt = now.takeIf { isFavorite }),
                    source = result.externalRef.source,
                    externalId = externalId,
                    isFavorite = isFavorite
                )
            }
        }

    override suspend fun setWatchedDate(
        ref: ExternalMediaRef,
        mediaType: MediaType,
        watchedDate: LocalDate?
    ): AppResult<Unit> = withNormalizedExternalId(ref) { externalId ->
        persistenceRead {
            val outcome = watchProgressDao.setMediaWatchedDate(
                source = ref.source,
                mediaType = mediaType,
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

private const val PROGRESS_STOP_TIMEOUT_MILLIS = 5_000L
private const val PROGRESS_RETRY_DELAY_MILLIS = 1_000L

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
        isComplete = isSeriesComplete(watchedEpisodes, trackableEpisodes, hasSufficientCoverage)
    ),
    nextEpisode = if (nextSeasonNumber != null && nextEpisodeNumber != null) {
        EpisodePosition(nextSeasonNumber, nextEpisodeNumber)
    } else {
        null
    },
    updatedAt = lastProgressAt,
    isAbandoned = isAbandoned,
    lastWatchedEpisode = if (lastSeasonNumber != null && lastEpisodeNumber != null) {
        EpisodePosition(lastSeasonNumber, lastEpisodeNumber)
    } else {
        null
    },
    nextEpisodeRef = if (nextEpisodeSource != null && nextEpisodeExternalId != null) {
        ExternalMediaRef(nextEpisodeSource, nextEpisodeExternalId)
    } else {
        null
    }
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
    // Shared progress failures are values: a failed projection must not close its consumer before recovery.
    map { value -> persistenceRead { transform(value) } }
        .catch { throwable ->
            if (throwable is CancellationException) throw throwable
            emit(AppResult.Failure(throwable.toPersistenceError()))
        }

private suspend fun <T> persistenceRead(block: suspend () -> T): AppResult<T> = try {
    AppResult.Success(block())
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (throwable: Exception) {
    AppResult.Failure(throwable.toPersistenceError())
}

private suspend fun <T> ExternalMediaRef.withValidTmdbId(block: suspend (String) -> AppResult<T>): AppResult<T> =
    if (tmdbIdOrNull() == null) {
        AppResult.Failure(invalidRuntimeTmdbError())
    } else {
        block(externalId.trim())
    }

private fun ExternalMediaRef.invalidRuntimeTmdbError(): AppError =
    if (source == MediaSource.TMDB) AppError.InvalidInput else AppError.UnsupportedData

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
