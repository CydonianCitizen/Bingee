package com.cydoniancitizen.bingee.data.calendar

import android.content.Context
import android.database.sqlite.SQLiteException
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.cydoniancitizen.bingee.core.model.BackgroundRefreshTarget
import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.result.AppError
import com.cydoniancitizen.bingee.core.result.AppResult
import com.cydoniancitizen.bingee.data.library.local.LibraryDao
import com.cydoniancitizen.bingee.data.settings.bingeePreferences
import com.cydoniancitizen.bingee.domain.repository.BackgroundRefreshPlanner
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

@Singleton
internal class RoomBackgroundRefreshPlanner @Inject constructor(
    private val libraryDao: LibraryDao,
    private val clock: Clock,
    @param:ApplicationContext private val context: Context
) : BackgroundRefreshPlanner {
    override suspend fun claimSeasons(
        mediaRef: ExternalMediaRef,
        seasonNumbers: List<Int>,
        prioritySeasonNumber: Int?
    ): AppResult<List<Int>> {
        if (seasonNumbers.isEmpty()) return AppResult.Success(emptyList())
        val key = intPreferencesKey("background_season_cursor_${mediaRef.source}_${mediaRef.externalId}")
        var selected = emptyList<Int>()
        return try {
            context.bingeePreferences.edit { preferences ->
                selected = rotateSeasonBatch(seasonNumbers, preferences[key], prioritySeasonNumber)
                preferences[key] = selected.last()
            }
            AppResult.Success(selected)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: IOException) {
            AppResult.Failure(AppError.LocalStorageFailure)
        }
    }

    override suspend fun plan(limit: Int): AppResult<List<BackgroundRefreshTarget>> {
        if (limit <= 0) return AppResult.Failure(AppError.InvalidInput)
        return try {
            AppResult.Success(
                libraryDao.claimBackgroundRefreshCandidates(limit, clock.instant()).map { row ->
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

internal fun rotateSeasonBatch(
    seasonNumbers: List<Int>,
    lastAttempted: Int?,
    prioritySeasonNumber: Int? = null
): List<Int> {
    val priority = listOfNotNull(prioritySeasonNumber?.takeIf { it in seasonNumbers })
    val sorted = (seasonNumbers.distinct() - priority.toSet()).sorted()
    val start = sorted.indexOfFirst { lastAttempted == null || it > lastAttempted }
        .takeIf { it >= 0 } ?: 0
    // Keep a rotating slot even when the priority season keeps failing.
    return priority + (sorted.drop(start) + sorted.take(start))
        .take(BackgroundRefreshPlanner.SEASON_LIMIT - priority.size)
}
