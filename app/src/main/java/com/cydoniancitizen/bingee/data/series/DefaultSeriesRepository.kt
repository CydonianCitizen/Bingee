package com.cydoniancitizen.bingee.data.series

import android.database.sqlite.SQLiteException
import com.cydoniancitizen.bingee.core.model.CacheFreshness
import com.cydoniancitizen.bingee.core.model.CachedSeason
import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.result.AppError
import com.cydoniancitizen.bingee.core.result.AppResult
import com.cydoniancitizen.bingee.data.calendar.MetadataCalendarStore
import com.cydoniancitizen.bingee.data.library.local.SeriesDao
import com.cydoniancitizen.bingee.data.tmdb.series.TmdbSeasonRemoteDataSource
import com.cydoniancitizen.bingee.domain.repository.SeriesRepository
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
internal class DefaultSeriesRepository @Inject constructor(
    private val seriesDao: SeriesDao,
    private val metadataStore: MetadataCalendarStore,
    private val remote: TmdbSeasonRemoteDataSource,
    private val freshnessPolicy: SeasonCacheFreshnessPolicy,
    private val clock: Clock
) : SeriesRepository {
    private val inFlightLock = Mutex()
    private val inFlight = mutableMapOf<SeasonRefreshKey, CompletableDeferred<AppResult<Unit>>>()

    override fun observeSeasons(seriesRef: ExternalMediaRef): Flow<AppResult<List<CachedSeason>>> {
        val normalized = seriesRef.normalizedTmdbOrNull()
            ?: return flowOf(AppResult.Failure(invalidReferenceError(seriesRef)))
        return seriesDao.observeSeriesSeasons(normalized.source, normalized.externalId)
            .map { rows ->
                val today = LocalDate.now(clock)
                AppResult.Success(rows.map { it.toDomain(normalized, today, freshnessPolicy) })
            }
            .catchPersistence()
    }

    override suspend fun refreshSeason(
        seriesRef: ExternalMediaRef,
        seasonNumber: Int,
        force: Boolean
    ): AppResult<Unit> {
        val normalized = seriesRef.normalizedTmdbOrNull()
            ?: return AppResult.Failure(invalidReferenceError(seriesRef))
        if (seasonNumber < 0) return AppResult.Failure(AppError.InvalidInput)
        val key = SeasonRefreshKey(normalized, seasonNumber)
        val mine = CompletableDeferred<AppResult<Unit>>()
        val existing = inFlightLock.withLock {
            inFlight[key]?.also { return@withLock it }
            inFlight[key] = mine
            null
        }
        if (existing != null) return existing.await()
        return try {
            val result = refreshOwned(normalized, seasonNumber, force)
            mine.complete(result)
            result
        } catch (cancelled: CancellationException) {
            mine.cancel(cancelled)
            throw cancelled
        } finally {
            inFlightLock.withLock {
                if (inFlight[key] === mine) inFlight.remove(key)
            }
        }
    }

    private suspend fun refreshOwned(seriesRef: ExternalMediaRef, seasonNumber: Int, force: Boolean): AppResult<Unit> {
        val cached = try {
            seriesDao.getSeasonForSeries(seriesRef.source, seriesRef.externalId, seasonNumber)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (throwable: Throwable) {
            return AppResult.Failure(throwable.toPersistenceError())
        }
        if (!force && cached?.episodesFetchedAt?.let(freshnessPolicy::classify) == CacheFreshness.FRESH) {
            return AppResult.Success(Unit)
        }
        val remoteResult = remote.load(seriesRef, seasonNumber)
        if (remoteResult is AppResult.Failure) return remoteResult
        val payload = (remoteResult as AppResult.Success).value
        if (payload.season.seriesRef != seriesRef || payload.season.seasonNumber != seasonNumber) {
            return AppResult.Failure(AppError.InvalidRemoteResponse)
        }
        return try {
            val fetchedAt = clock.instant()
            metadataStore.storeSeason(seriesRef, payload, fetchedAt)
            AppResult.Success(Unit)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (throwable: Throwable) {
            AppResult.Failure(throwable.toPersistenceError())
        }
    }

    private data class SeasonRefreshKey(val seriesRef: ExternalMediaRef, val seasonNumber: Int)
}

private fun ExternalMediaRef.normalizedTmdbOrNull(): ExternalMediaRef? {
    if (source != MediaSource.TMDB) return null
    val id = externalId.trim().takeIf { it.toLongOrNull()?.let { value -> value > 0 } == true } ?: return null
    return ExternalMediaRef(source, id)
}

private fun invalidReferenceError(reference: ExternalMediaRef): AppError =
    if (reference.source == MediaSource.TMDB) AppError.InvalidInput else AppError.UnsupportedData

private fun <T> Flow<AppResult<T>>.catchPersistence(): Flow<AppResult<T>> = catch { throwable ->
    if (throwable is CancellationException) throw throwable
    emit(AppResult.Failure(throwable.toPersistenceError()))
}

private fun Throwable.toPersistenceError(): AppError = when (this) {
    is IllegalArgumentException,
    is IllegalStateException -> AppError.CorruptedData
    is SQLiteException -> AppError.LocalStorageFailure
    else -> AppError.Unknown
}
