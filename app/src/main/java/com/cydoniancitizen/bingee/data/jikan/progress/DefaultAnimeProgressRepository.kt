package com.cydoniancitizen.bingee.data.jikan.progress

import android.database.sqlite.SQLiteException
import com.cydoniancitizen.bingee.core.model.AnimeCompletionOrigin
import com.cydoniancitizen.bingee.core.model.AnimeFormat
import com.cydoniancitizen.bingee.core.model.AnimeStatus
import com.cydoniancitizen.bingee.core.model.AnimeWatchProgress
import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.result.AppError
import com.cydoniancitizen.bingee.core.result.AppResult
import com.cydoniancitizen.bingee.data.jikan.details.toDomain
import com.cydoniancitizen.bingee.data.jikan.details.validAnime
import com.cydoniancitizen.bingee.data.library.local.AnimeDao
import com.cydoniancitizen.bingee.domain.repository.AnimeProgressRepository
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

@Singleton
internal class DefaultAnimeProgressRepository @Inject constructor(private val dao: AnimeDao, private val clock: Clock) :
    AnimeProgressRepository {
    override fun observe(reference: ExternalMediaRef): Flow<AppResult<AnimeWatchProgress?>> {
        if (!reference.validAnime()) return flowOf(AppResult.Failure(AppError.InvalidInput))
        return dao.observeAnime(reference.externalId)
            .map<com.cydoniancitizen.bingee.data.library.local.CachedAnimeRelation?, AppResult<AnimeWatchProgress?>> {
                AppResult.Success(it?.progress?.toDomain())
            }
            .catch {
                if (it is CancellationException) throw it
                emit(AppResult.Failure(it.progressError()))
            }
    }

    override suspend fun increment(reference: ExternalMediaRef): AppResult<Unit> =
        mutate(reference) { row, old -> nextCount(row, old.watchedEpisodes + 1, old) }

    override suspend fun decrement(reference: ExternalMediaRef): AppResult<Unit> =
        mutate(reference) { row, old -> nextCount(row, (old.watchedEpisodes - 1).coerceAtLeast(0), old) }

    override suspend fun setCount(reference: ExternalMediaRef, count: Int): AppResult<Unit> {
        if (count !in 0..AnimeWatchProgress.MAX_WATCHED_EPISODES) return AppResult.Failure(AppError.InvalidInput)
        return mutate(reference) { row, old -> nextCount(row, count, old) }
    }

    override suspend fun markComplete(reference: ExternalMediaRef): AppResult<Unit> = mutate(reference) { row, old ->
        val details = requireNotNull(row.details)
        val count = when (details.format) {
            AnimeFormat.MOVIE -> 1
            else -> maxOf(old.watchedEpisodes, details.episodeCount ?: 1)
        }
        old.copy(
            watchedEpisodes = count,
            completedAt = old.completedAt ?: clock.instant(),
            completionOrigin = AnimeCompletionOrigin.EXPLICIT,
            updatedAt = clock.instant()
        )
    }

    override suspend fun markIncomplete(reference: ExternalMediaRef): AppResult<Unit> = mutate(reference) { row, old ->
        val details = requireNotNull(row.details)
        val count = when {
            details.format == AnimeFormat.MOVIE -> 0
            details.episodeCount != null && old.watchedEpisodes >= details.episodeCount ->
                (details.episodeCount - 1).coerceAtLeast(0)
            else -> old.watchedEpisodes
        }
        old.copy(
            watchedEpisodes = count,
            completedAt = null,
            completionOrigin = null,
            updatedAt = clock.instant()
        )
    }

    override suspend fun reset(reference: ExternalMediaRef): AppResult<Unit> {
        if (!reference.validAnime()) return AppResult.Failure(AppError.InvalidInput)
        return write(reference, null)
    }

    private suspend fun mutate(
        reference: ExternalMediaRef,
        transform: (com.cydoniancitizen.bingee.data.library.local.CachedAnimeRelation, AnimeWatchProgress) ->
        AnimeWatchProgress
    ): AppResult<Unit> {
        if (!reference.validAnime()) return AppResult.Failure(AppError.InvalidInput)
        val row = try {
            dao.getAnime(reference.externalId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            return AppResult.Failure(failure.progressError())
        } ?: return AppResult.Failure(AppError.MissingData)
        if (row.details == null) return AppResult.Failure(AppError.MissingData)
        val now = clock.instant()
        val old = row.progress?.toDomain() ?: AnimeWatchProgress(0, null, null, now)
        val next = try {
            transform(row, old)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: IllegalArgumentException) {
            return AppResult.Failure(AppError.InvalidInput)
        } catch (_: IllegalStateException) {
            return AppResult.Failure(AppError.MissingData)
        }
        return write(reference, next)
    }

    private fun nextCount(
        row: com.cydoniancitizen.bingee.data.library.local.CachedAnimeRelation,
        count: Int,
        old: AnimeWatchProgress
    ): AnimeWatchProgress {
        require(count in 0..AnimeWatchProgress.MAX_WATCHED_EPISODES)
        val details = requireNotNull(row.details)
        require(details.format != AnimeFormat.MOVIE || count in 0..1)
        val total = details.episodeCount?.takeIf { it > 0 }
        val preserveExplicit = old.completionOrigin == AnimeCompletionOrigin.EXPLICIT &&
            (total == null || details.providerStatus == AnimeStatus.AIRING)
        val complete = total != null && count >= total
        return old.copy(
            watchedEpisodes = count,
            completedAt = when {
                preserveExplicit -> old.completedAt
                complete -> old.completedAt ?: clock.instant()
                else -> null
            },
            completionOrigin = when {
                preserveExplicit -> AnimeCompletionOrigin.EXPLICIT
                complete -> old.completionOrigin ?: AnimeCompletionOrigin.INFERRED
                else -> null
            },
            updatedAt = clock.instant()
        )
    }

    private suspend fun write(reference: ExternalMediaRef, value: AnimeWatchProgress?): AppResult<Unit> = try {
        dao.setProgress(reference.source, reference.externalId, value)
        AppResult.Success(Unit)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Throwable) {
        AppResult.Failure(failure.progressError())
    }
}

private fun Throwable.progressError(): AppError = when (this) {
    is IllegalArgumentException -> AppError.MediaTypeMismatch
    is IllegalStateException -> AppError.MissingData
    is SQLiteException -> AppError.LocalStorageFailure
    else -> AppError.Unknown
}
