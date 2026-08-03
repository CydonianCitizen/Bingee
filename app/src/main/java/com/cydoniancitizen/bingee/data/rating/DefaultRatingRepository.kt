package com.cydoniancitizen.bingee.data.rating

import android.database.sqlite.SQLiteException
import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.PersonalRating
import com.cydoniancitizen.bingee.core.result.AppError
import com.cydoniancitizen.bingee.core.result.AppResult
import com.cydoniancitizen.bingee.data.library.local.MediaRatingEntity
import com.cydoniancitizen.bingee.data.library.local.RatingDao
import com.cydoniancitizen.bingee.data.library.local.RatingWriteOutcome
import com.cydoniancitizen.bingee.domain.repository.RatingRepository
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

@Singleton
internal class DefaultRatingRepository @Inject constructor(private val ratingDao: RatingDao, private val clock: Clock) :
    RatingRepository {
    override fun observeRating(reference: ExternalMediaRef): Flow<AppResult<PersonalRating?>> {
        val externalId = reference.externalId.trim()
        if (externalId.isEmpty()) return flowOf(AppResult.Failure(AppError.InvalidInput))
        return ratingDao.observeRating(reference.source, externalId)
            .map<MediaRatingEntity?, AppResult<PersonalRating?>> { entity ->
                AppResult.Success(entity?.let { PersonalRating(it.ratingValue) })
            }
            .catch { throwable ->
                if (throwable is CancellationException) throw throwable
                emit(AppResult.Failure(throwable.toAppError()))
            }
    }

    override suspend fun setRating(reference: ExternalMediaRef, rating: PersonalRating): AppResult<Unit> =
        write(reference) { externalId ->
            ratingDao.setRating(reference.source, externalId, rating.value, clock.instant())
        }

    override suspend fun removeRating(reference: ExternalMediaRef): AppResult<Unit> =
        write(reference) { externalId -> ratingDao.removeRating(reference.source, externalId) }

    private suspend fun write(
        reference: ExternalMediaRef,
        block: suspend (String) -> RatingWriteOutcome
    ): AppResult<Unit> {
        val externalId = reference.externalId.trim()
        if (externalId.isEmpty()) return AppResult.Failure(AppError.InvalidInput)
        return try {
            when (block(externalId)) {
                RatingWriteOutcome.SUCCESS,
                RatingWriteOutcome.UNCHANGED -> AppResult.Success(Unit)
                RatingWriteOutcome.NOT_FOUND -> AppResult.Failure(AppError.MissingData)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (throwable: Exception) {
            AppResult.Failure(throwable.toAppError())
        }
    }
}

private fun Throwable.toAppError(): AppError = when (this) {
    is IllegalArgumentException -> AppError.InvalidInput
    is IllegalStateException -> AppError.CorruptedData
    is SQLiteException -> AppError.LocalStorageFailure
    else -> AppError.Unknown
}
