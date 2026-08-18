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
import com.cydoniancitizen.bingee.domain.calendar.CalendarDateSource
import com.cydoniancitizen.bingee.domain.repository.SeriesRepository
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
internal class DefaultSeriesRepository @Inject constructor(
    private val seriesDao: SeriesDao,
    private val metadataStore: MetadataCalendarStore,
    private val remote: TmdbSeasonRemoteDataSource,
    private val freshnessPolicy: SeasonCacheFreshnessPolicy,
    private val clock: Clock,
    private val dateSource: CalendarDateSource
) : SeriesRepository {
    private val inFlightLock = Mutex()
    private val inFlight = mutableMapOf<SeasonRefreshKey, CompletableDeferred<AppResult<Unit>>>()

    override fun observeSeasons(tmdbId: Long): Flow<AppResult<List<CachedSeason>>> {
        val seriesRef = tmdbReferenceOrNull(tmdbId) ?: return flowOf(AppResult.Failure(AppError.InvalidInput))
        return combine(
            seriesDao.observeSeriesSeasons(seriesRef.source, seriesRef.externalId),
            dateSource.observeDate()
        ) { rows, today ->
            AppResult.Success(rows.map { it.toDomain(seriesRef, today, freshnessPolicy) })
        }
            .catchPersistence()
    }

    override suspend fun refreshSeason(tmdbId: Long, seasonNumber: Int, force: Boolean): AppResult<Unit> {
        val seriesRef = tmdbReferenceOrNull(tmdbId) ?: return AppResult.Failure(AppError.InvalidInput)
        if (seasonNumber < 0) return AppResult.Failure(AppError.InvalidInput)
        val key = SeasonRefreshKey(tmdbId, seasonNumber)
        val mine = CompletableDeferred<AppResult<Unit>>()
        val existing = inFlightLock.withLock {
            inFlight[key]?.also { return@withLock it }
            inFlight[key] = mine
            null
        }
        if (existing != null) return existing.await()
        return try {
            val result = refreshOwned(seriesRef, tmdbId, seasonNumber, force)
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

    private suspend fun refreshOwned(
        seriesRef: ExternalMediaRef,
        tmdbId: Long,
        seasonNumber: Int,
        force: Boolean
    ): AppResult<Unit> {
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
        val remoteResult = remote.load(tmdbId, seasonNumber)
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

    private data class SeasonRefreshKey(val tmdbId: Long, val seasonNumber: Int)
}

private fun tmdbReferenceOrNull(tmdbId: Long): ExternalMediaRef? =
    tmdbId.takeIf { it > 0 }?.let { ExternalMediaRef(MediaSource.TMDB, it.toString()) }

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
