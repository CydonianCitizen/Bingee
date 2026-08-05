package com.cydoniancitizen.bingee.data.jikan.details

import android.database.sqlite.SQLiteException
import com.cydoniancitizen.bingee.core.model.CacheFreshness
import com.cydoniancitizen.bingee.core.model.CachedAnimeDetails
import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.result.AppError
import com.cydoniancitizen.bingee.core.result.AppResult
import com.cydoniancitizen.bingee.data.details.CacheFreshnessPolicy
import com.cydoniancitizen.bingee.data.library.local.AnimeDao
import com.cydoniancitizen.bingee.domain.repository.AnimeDetailsRepository
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

@Singleton
internal class DefaultAnimeDetailsRepository @Inject constructor(
    private val animeDao: AnimeDao,
    private val client: JikanDetailsClient,
    private val store: AnimeMetadataStore,
    private val freshnessPolicy: CacheFreshnessPolicy,
    private val clock: Clock
) : AnimeDetailsRepository {
    override fun observeDetails(reference: ExternalMediaRef): Flow<AppResult<CachedAnimeDetails?>> {
        if (!reference.validAnime()) return flowOf(AppResult.Failure(AppError.InvalidInput))
        return animeDao.observeAnime(reference.externalId)
            .map<com.cydoniancitizen.bingee.data.library.local.CachedAnimeRelation?, AppResult<CachedAnimeDetails?>> {
                AppResult.Success(it?.toCachedDomain(reference, freshnessPolicy))
            }
            .catch {
                if (it is CancellationException) throw it
                emit(AppResult.Failure(it.persistenceError()))
            }
    }

    override suspend fun refreshDetails(reference: ExternalMediaRef, force: Boolean): AppResult<Unit> {
        if (!reference.validAnime()) return AppResult.Failure(AppError.InvalidInput)
        val cached = try {
            animeDao.getAnime(reference.externalId)?.toCachedDomain(reference, freshnessPolicy)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            return AppResult.Failure(failure.persistenceError())
        }
        if (!force && cached?.freshness == CacheFreshness.FRESH) return AppResult.Success(Unit)
        return when (val remote = client.load(reference)) {
            is AppResult.Failure -> remote
            is AppResult.Success -> try {
                store.store(remote.value, clock.instant())
                AppResult.Success(Unit)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                AppResult.Failure(failure.persistenceError())
            }
        }
    }
}

internal fun ExternalMediaRef.validAnime(): Boolean =
    source == MediaSource.JIKAN && externalId.toLongOrNull()?.let { it > 0 } == true

private fun Throwable.persistenceError(): AppError = when (this) {
    is SQLiteException -> AppError.LocalStorageFailure
    is IllegalArgumentException, is IllegalStateException -> AppError.CorruptedData
    else -> AppError.Unknown
}
