package com.cydoniancitizen.bingee.data.progress

import android.database.sqlite.SQLiteException
import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.core.model.MovieWatchState
import com.cydoniancitizen.bingee.core.result.AppError
import com.cydoniancitizen.bingee.core.result.AppResult
import com.cydoniancitizen.bingee.data.library.local.ProgressWriteOutcome
import com.cydoniancitizen.bingee.data.library.local.WatchProgressDao
import com.cydoniancitizen.bingee.domain.repository.WatchProgressRepository
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

@Singleton
internal class DefaultWatchProgressRepository @Inject constructor(
    private val dao: WatchProgressDao,
    private val clock: Clock
) : WatchProgressRepository {
    override fun observeMovie(reference: ExternalMediaRef): Flow<AppResult<MovieWatchState>> {
        val normalized = reference.normalizedRefOrNull()
            ?: return flowOf(AppResult.Failure(reference.invalidReferenceError()))
        return dao.observeMovieProgress(normalized.source, normalized.externalId)
            .map { row ->
                when {
                    row == null -> AppResult.Failure(AppError.MissingData)
                    row.mediaType != MediaType.MOVIE -> AppResult.Failure(AppError.MediaTypeMismatch)
                    row.watchedAt == null -> AppResult.Success(MovieWatchState.Unwatched)
                    else -> AppResult.Success(MovieWatchState.Watched(row.watchedAt))
                }
            }
            .catch { throwable ->
                if (throwable is CancellationException) throw throwable
                emit(AppResult.Failure(throwable.toPersistenceError()))
            }
    }

    override suspend fun markEpisodeWatched(episodeRef: ExternalMediaRef): AppResult<Unit> = episodeRef.write {
        dao.markEpisodeWatched(
            source = it.source,
            externalId = it.externalId,
            today = LocalDate.now(clock),
            watchedAt = clock.instant()
        )
    }

    override suspend fun markEpisodeUnwatched(episodeRef: ExternalMediaRef): AppResult<Unit> =
        episodeRef.write { dao.markEpisodeUnwatched(it.source, it.externalId) }

    override suspend fun markSeasonWatched(seasonRef: ExternalMediaRef): AppResult<Unit> = seasonRef.write {
        dao.markSeasonWatched(
            source = it.source,
            externalId = it.externalId,
            today = LocalDate.now(clock),
            watchedAt = clock.instant()
        )
    }

    override suspend fun markSeasonUnwatched(seasonRef: ExternalMediaRef): AppResult<Unit> =
        seasonRef.write { dao.markSeasonUnwatched(it.source, it.externalId) }

    override suspend fun markMovieWatched(reference: ExternalMediaRef): AppResult<Unit> =
        reference.write { dao.markMovieWatched(it.source, it.externalId, clock.instant()) }

    override suspend fun markMovieUnwatched(reference: ExternalMediaRef): AppResult<Unit> =
        reference.write { dao.markMovieUnwatched(it.source, it.externalId) }

    override suspend fun markSeriesWatched(reference: ExternalMediaRef): AppResult<Unit> = reference.write {
        dao.markSeriesWatched(
            source = it.source,
            externalId = it.externalId,
            completedAt = clock.instant(),
            today = LocalDate.now(clock)
        )
    }

    override suspend fun markSeriesUnwatched(reference: ExternalMediaRef): AppResult<Unit> =
        reference.write { dao.markSeriesUnwatched(it.source, it.externalId) }

    private suspend fun ExternalMediaRef.write(
        block: suspend (ExternalMediaRef) -> ProgressWriteOutcome
    ): AppResult<Unit> {
        val normalized = normalizedRefOrNull() ?: return AppResult.Failure(invalidReferenceError())
        return try {
            block(normalized).toResult()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (throwable: Throwable) {
            AppResult.Failure(throwable.toPersistenceError())
        }
    }
}

private fun ProgressWriteOutcome.toResult(): AppResult<Unit> = when (this) {
    ProgressWriteOutcome.SUCCESS -> AppResult.Success(Unit)
    ProgressWriteOutcome.NOT_FOUND -> AppResult.Failure(AppError.MissingData)
    ProgressWriteOutcome.NOT_IN_LIBRARY -> AppResult.Failure(AppError.InvalidInput)
    ProgressWriteOutcome.NOT_TRACKABLE -> AppResult.Failure(AppError.NotTrackable)
    ProgressWriteOutcome.INCOMPLETE -> AppResult.Failure(AppError.InvalidInput)
    ProgressWriteOutcome.MEDIA_TYPE_MISMATCH -> AppResult.Failure(AppError.MediaTypeMismatch)
}

private fun ExternalMediaRef.normalizedRefOrNull(): ExternalMediaRef? {
    if (source != MediaSource.TMDB) return null
    val id = externalId.trim().takeIf { it.toLongOrNull()?.let { value -> value > 0 } == true } ?: return null
    return ExternalMediaRef(source, id)
}

private fun ExternalMediaRef.invalidReferenceError(): AppError =
    if (source == MediaSource.TMDB) AppError.InvalidInput else AppError.UnsupportedData

private fun Throwable.toPersistenceError(): AppError = when (this) {
    is IllegalArgumentException,
    is IllegalStateException -> AppError.CorruptedData
    is SQLiteException -> AppError.LocalStorageFailure
    else -> AppError.Unknown
}
