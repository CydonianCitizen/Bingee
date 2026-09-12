package com.cydoniancitizen.bingee.data.details

import android.database.sqlite.SQLiteException
import com.cydoniancitizen.bingee.core.model.CacheFreshness
import com.cydoniancitizen.bingee.core.model.CachedMediaDetails
import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.core.result.AppError
import com.cydoniancitizen.bingee.core.result.AppResult
import com.cydoniancitizen.bingee.data.CacheFreshnessPolicy
import com.cydoniancitizen.bingee.data.calendar.MetadataCalendarStore
import com.cydoniancitizen.bingee.data.library.local.CachedDetailsRelation
import com.cydoniancitizen.bingee.data.library.local.DetailsDao
import com.cydoniancitizen.bingee.data.tmdb.details.TmdbDetailsRemoteDataSource
import com.cydoniancitizen.bingee.domain.repository.MediaDetailsRepository
import java.time.Clock
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
internal class DefaultMediaDetailsRepository @Inject constructor(
    private val detailsDao: DetailsDao,
    private val metadataStore: MetadataCalendarStore,
    private val client: TmdbDetailsRemoteDataSource,
    private val freshnessPolicy: CacheFreshnessPolicy,
    private val clock: Clock
) : MediaDetailsRepository {
    private val inFlightLock = Mutex()

    // Keyed by type too: TMDB reuses one ID for a movie and an unrelated series.
    private val inFlight = mutableMapOf<Pair<Long, MediaType>, CompletableDeferred<AppResult<Unit>>>()

    override fun observeDetails(tmdbId: Long, mediaType: MediaType): Flow<AppResult<CachedMediaDetails?>> {
        val reference = tmdbReferenceOrNull(tmdbId)
            ?: return flowOf(AppResult.Failure(AppError.InvalidInput))
        return detailsDao.observeCachedDetails(reference.source, mediaType, reference.externalId)
            .map<CachedDetailsRelation?, AppResult<CachedMediaDetails?>> { row ->
                AppResult.Success(row?.toDomain(reference, freshnessPolicy))
            }
            .catch { throwable ->
                if (throwable is CancellationException) throw throwable
                emit(AppResult.Failure(throwable.toPersistenceError()))
            }
    }

    override suspend fun refreshDetails(tmdbId: Long, mediaType: MediaType, force: Boolean): AppResult<Unit> {
        val normalized = tmdbReferenceOrNull(tmdbId)
            ?: return AppResult.Failure(AppError.InvalidInput)

        val key = tmdbId to mediaType
        val mine = CompletableDeferred<AppResult<Unit>>()
        val existing = inFlightLock.withLock {
            inFlight[key]?.also { return@withLock it }
            inFlight[key] = mine
            null
        }
        if (existing != null) return existing.await()

        return try {
            val result = refreshOwned(normalized, mediaType, force)
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
        reference: ExternalMediaRef,
        mediaType: MediaType,
        force: Boolean
    ): AppResult<Unit> {
        val cached = try {
            detailsDao.getCachedDetails(reference.source, mediaType, reference.externalId)
                ?.toDomain(reference, freshnessPolicy)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (throwable: Throwable) {
            return AppResult.Failure(throwable.toPersistenceError())
        }
        if (cached != null && cached.details.mediaType != mediaType) {
            return AppResult.Failure(AppError.CorruptedData)
        }
        if (!force && cached?.freshness == CacheFreshness.FRESH) return AppResult.Success(Unit)

        val remote = client.load(reference.externalId.toLong(), mediaType)
        if (remote is AppResult.Failure) return remote
        val payload = (remote as AppResult.Success).value
        val details = payload.details
        if (details.externalRef != reference || details.mediaType != mediaType) {
            return AppResult.Failure(AppError.InvalidRemoteResponse)
        }
        return try {
            val fetchedAt = clock.instant()
            metadataStore.storeDetails(reference, details, payload.seasons, fetchedAt)
            AppResult.Success(Unit)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (throwable: Throwable) {
            AppResult.Failure(throwable.toPersistenceError())
        }
    }
}

private fun tmdbReferenceOrNull(tmdbId: Long): ExternalMediaRef? =
    tmdbId.takeIf { it > 0 }?.let { ExternalMediaRef(MediaSource.TMDB, it.toString()) }

private fun Throwable.toPersistenceError(): AppError = when (this) {
    is IllegalArgumentException,
    is IllegalStateException -> AppError.CorruptedData
    is SQLiteException -> AppError.LocalStorageFailure
    else -> AppError.Unknown
}
