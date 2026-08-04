package com.cydoniancitizen.bingee.data.details

import android.database.sqlite.SQLiteException
import com.cydoniancitizen.bingee.core.model.CacheFreshness
import com.cydoniancitizen.bingee.core.model.CachedMediaDetails
import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.core.result.AppError
import com.cydoniancitizen.bingee.core.result.AppResult
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
    private val inFlight = mutableMapOf<ExternalMediaRef, InFlightRefresh>()

    override fun observeDetails(reference: ExternalMediaRef): Flow<AppResult<CachedMediaDetails?>> {
        val normalized = reference.normalizedOrNull()
            ?: return flowOf(AppResult.Failure(AppError.InvalidInput))
        if (normalized.source != MediaSource.TMDB) {
            return flowOf(AppResult.Failure(AppError.UnsupportedData))
        }
        return detailsDao.observeCachedDetails(normalized.source, normalized.externalId)
            .map<CachedDetailsRelation?, AppResult<CachedMediaDetails?>> { row ->
                AppResult.Success(row?.toDomain(normalized, freshnessPolicy))
            }
            .catch { throwable ->
                if (throwable is CancellationException) throw throwable
                emit(AppResult.Failure(throwable.toPersistenceError()))
            }
    }

    override suspend fun refreshDetails(
        reference: ExternalMediaRef,
        mediaType: MediaType,
        force: Boolean
    ): AppResult<Unit> {
        val normalized = reference.normalizedOrNull()
            ?: return AppResult.Failure(AppError.InvalidInput)
        if (normalized.source != MediaSource.TMDB) {
            return AppResult.Failure(AppError.UnsupportedData)
        }

        val mine = CompletableDeferred<AppResult<Unit>>()
        val existing = inFlightLock.withLock {
            inFlight[normalized]?.also { return@withLock it }
            inFlight[normalized] = InFlightRefresh(mediaType, mine)
            null
        }
        if (existing != null) {
            if (existing.mediaType != mediaType) return AppResult.Failure(AppError.CorruptedData)
            return existing.result.await()
        }

        return try {
            val result = refreshOwned(normalized, mediaType, force)
            mine.complete(result)
            result
        } catch (cancelled: CancellationException) {
            mine.cancel(cancelled)
            throw cancelled
        } finally {
            inFlightLock.withLock {
                if (inFlight[normalized]?.result === mine) inFlight.remove(normalized)
            }
        }
    }

    private suspend fun refreshOwned(
        reference: ExternalMediaRef,
        mediaType: MediaType,
        force: Boolean
    ): AppResult<Unit> {
        val cached = try {
            detailsDao.getCachedDetails(reference.source, reference.externalId)
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

        val remote = client.load(reference, mediaType)
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

private data class InFlightRefresh(val mediaType: MediaType, val result: CompletableDeferred<AppResult<Unit>>)

private fun ExternalMediaRef.normalizedOrNull(): ExternalMediaRef? {
    val id = externalId.trim().takeIf(String::isNotEmpty) ?: return null
    if (source == MediaSource.TMDB && (id.toLongOrNull()?.takeIf { it > 0 } == null)) return null
    return ExternalMediaRef(source, id)
}

private fun Throwable.toPersistenceError(): AppError = when (this) {
    is IllegalArgumentException,
    is IllegalStateException -> AppError.CorruptedData
    is SQLiteException -> AppError.LocalStorageFailure
    else -> AppError.Unknown
}
