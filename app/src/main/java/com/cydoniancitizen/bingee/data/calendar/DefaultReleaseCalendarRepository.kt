package com.cydoniancitizen.bingee.data.calendar

import android.database.sqlite.SQLiteException
import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.ReleaseEvent
import com.cydoniancitizen.bingee.core.model.ReleaseSubjectIdentity
import com.cydoniancitizen.bingee.core.result.AppError
import com.cydoniancitizen.bingee.core.result.AppResult
import com.cydoniancitizen.bingee.data.library.local.CalendarRefreshStateEntity
import com.cydoniancitizen.bingee.data.library.local.ReleaseEventDao
import com.cydoniancitizen.bingee.data.library.local.ReleaseEventRow
import com.cydoniancitizen.bingee.domain.repository.ReleaseCalendarRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

@Singleton
internal class DefaultReleaseCalendarRepository @Inject constructor(
    private val dao: ReleaseEventDao,
    private val clock: Clock
) : ReleaseCalendarRepository {
    override fun observeEvents(fromDate: LocalDate): Flow<AppResult<List<ReleaseEvent>>> =
        observeMappedEvents(fromDate, throughDate = null)

    override fun observeEvents(fromDate: LocalDate, throughDate: LocalDate): Flow<AppResult<List<ReleaseEvent>>> =
        observeMappedEvents(fromDate, throughDate)

    private fun observeMappedEvents(fromDate: LocalDate, throughDate: LocalDate?): Flow<AppResult<List<ReleaseEvent>>> =
        dao.observeActiveEvents(fromDate, throughDate)
            .map<List<ReleaseEventRow>, AppResult<List<ReleaseEvent>>> { rows ->
                val events = rows.map(ReleaseEventRow::toDomain)
                AppResult.Success(events)
            }
            .catchPersistence()

    override fun observeLastSuccessfulRefresh(): Flow<AppResult<Instant?>> = dao.observeLastSuccessfulRefresh()
        .map<Instant?, AppResult<Instant?>> { AppResult.Success(it) }
        .catchPersistence()

    override suspend fun getEvents(fromDate: LocalDate, throughDate: LocalDate): AppResult<List<ReleaseEvent>> =
        persistenceRead {
            dao.getActiveEventsBetween(fromDate, throughDate)
                .map(ReleaseEventRow::toDomain)
        }

    override suspend fun backfill(): AppResult<Unit> = persistenceWrite {
        dao.backfill(clock.instant())
    }

    override suspend fun markRefreshSuccessful(at: Instant): AppResult<Unit> = persistenceWrite {
        dao.replaceRefreshState(CalendarRefreshStateEntity(lastSuccessfulRefreshAt = at))
    }
}

private fun ReleaseEventRow.toDomain(): ReleaseEvent = ReleaseEvent(
    mediaRef = ExternalMediaRef(source, parentExternalId),
    subject = ReleaseSubjectIdentity(source, subjectType, subjectExternalId, eventType),
    mediaType = mediaType,
    eventDate = eventDate,
    title = mediaTitle,
    posterUrl = posterUrl,
    seasonNumber = seasonNumber,
    episodeNumber = episodeNumber,
    subjectTitle = subjectTitle
)

private fun <T> Flow<AppResult<T>>.catchPersistence(): Flow<AppResult<T>> = catch { throwable ->
    if (throwable is CancellationException) throw throwable
    emit(AppResult.Failure(throwable.persistenceError()))
}

private suspend fun persistenceWrite(block: suspend () -> Unit): AppResult<Unit> = try {
    block()
    AppResult.Success(Unit)
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (throwable: Throwable) {
    AppResult.Failure(throwable.persistenceError())
}

private suspend fun <T> persistenceRead(block: suspend () -> T): AppResult<T> = try {
    AppResult.Success(block())
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (throwable: Throwable) {
    AppResult.Failure(throwable.persistenceError())
}

private fun Throwable.persistenceError(): AppError = when (this) {
    is IllegalArgumentException,
    is IllegalStateException -> AppError.CorruptedData
    is SQLiteException -> AppError.LocalStorageFailure
    else -> AppError.Unknown
}
