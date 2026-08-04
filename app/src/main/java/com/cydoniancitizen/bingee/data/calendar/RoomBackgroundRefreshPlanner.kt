package com.cydoniancitizen.bingee.data.calendar

import android.database.sqlite.SQLiteException
import com.cydoniancitizen.bingee.core.model.BackgroundRefreshTarget
import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.result.AppError
import com.cydoniancitizen.bingee.core.result.AppResult
import com.cydoniancitizen.bingee.data.library.local.LibraryDao
import com.cydoniancitizen.bingee.domain.repository.BackgroundRefreshPlanner
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

@Singleton
internal class RoomBackgroundRefreshPlanner @Inject constructor(private val libraryDao: LibraryDao) :
    BackgroundRefreshPlanner {
    override suspend fun plan(limit: Int): AppResult<List<BackgroundRefreshTarget>> {
        if (limit <= 0) return AppResult.Failure(AppError.InvalidInput)
        return try {
            AppResult.Success(
                libraryDao.getBackgroundRefreshCandidates(limit).map { row ->
                    BackgroundRefreshTarget(
                        mediaRef = ExternalMediaRef(row.source, row.externalId),
                        mediaType = row.mediaType
                    )
                }
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: SQLiteException) {
            AppResult.Failure(AppError.LocalStorageFailure)
        } catch (_: Exception) {
            AppResult.Failure(AppError.Unknown)
        }
    }
}
