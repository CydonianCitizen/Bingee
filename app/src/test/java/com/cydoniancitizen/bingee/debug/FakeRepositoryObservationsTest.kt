package com.cydoniancitizen.bingee.debug

import com.cydoniancitizen.bingee.core.model.ContinueWatchingItem
import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.LibraryEntry
import com.cydoniancitizen.bingee.core.model.LibraryMediaFilter
import com.cydoniancitizen.bingee.core.model.LibraryProgress
import com.cydoniancitizen.bingee.core.model.LibraryQuery
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.core.model.ReleaseEvent
import com.cydoniancitizen.bingee.core.model.ReleaseEventType
import com.cydoniancitizen.bingee.core.model.ReleaseSubjectIdentity
import com.cydoniancitizen.bingee.core.model.ReleaseSubjectType
import com.cydoniancitizen.bingee.core.model.SeriesProgress
import com.cydoniancitizen.bingee.core.result.AppError
import com.cydoniancitizen.bingee.core.result.AppResult
import com.cydoniancitizen.bingee.domain.repository.LibraryRepository
import com.cydoniancitizen.bingee.domain.repository.ReleaseCalendarRepository
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FakeRepositoryObservationsTest {
    @Test
    fun debugContinueWatchingKeepsCanonicalEligibilityAndRecentFirstOrdering() = runTest {
        val older = series("older", watchedAt = Instant.EPOCH)
        val recent = series("recent", watchedAt = Instant.EPOCH.plusSeconds(1))
        val repository = FakeLibraryRepository(
            listOf(
                older,
                recent,
                series("complete", watched = 3),
                series("abandoned").copy(isAbandoned = true),
                series("removed").copy(inLibrary = false)
            )
        )
        val result = repository.observeContinueWatching().first() as AppResult.Success
        assertEquals(listOf(recent.mediaRef, older.mediaRef), result.value.map { it.mediaRef })
    }

    @Test
    fun fakeContinuationForwardsFailureRecoveryAndCancellationOnTheSameFlow() = runTest {
        val source = MutableStateFlow<AppResult<List<LibraryEntry>>>(AppResult.Failure(AppError.LocalStorageFailure))
        var requestedQuery: LibraryQuery? = null
        val repository = object : LibraryRepository by FakeLibraryRepository() {
            override fun observeEntries(query: LibraryQuery): Flow<AppResult<List<LibraryEntry>>> {
                requestedQuery = query
                return source
            }
        }
        val results = mutableListOf<AppResult<List<ContinueWatchingItem>>>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.observeFakeContinueWatching().collect { results += it }
        }
        runCurrent()
        assertEquals(LibraryMediaFilter.TV_SERIES, requestedQuery?.mediaFilter)
        assertEquals(source.value, results.single())
        source.value = AppResult.Success(listOf(series("recovered")))
        runCurrent()
        val recovered = results.last() as AppResult.Success
        assertEquals(listOf("recovered"), recovered.value.map { it.mediaRef.externalId })
        assertEquals(1, source.subscriptionCount.value)
        job.cancel()
        runCurrent()
        assertEquals(0, source.subscriptionCount.value)
    }

    @Test
    fun fakeCalendarKeepsInclusiveUpperBoundFailureRecoveryAndCancellation() = runTest {
        val day = LocalDate.of(2026, 10, 10)
        val source = MutableStateFlow<AppResult<List<ReleaseEvent>>>(
            AppResult.Success(
                listOf(movie("today", day), movie("last", day.plusDays(1)), movie("later", day.plusDays(2)))
            )
        )
        var requestedFrom: LocalDate? = null
        val repository = object : ReleaseCalendarRepository {
            override fun observeEvents(fromDate: LocalDate): Flow<AppResult<List<ReleaseEvent>>> =
                source.also { requestedFrom = fromDate }
            override fun observeEvents(fromDate: LocalDate, throughDate: LocalDate) =
                observeFakeEvents(fromDate, throughDate)
            override fun observeLastSuccessfulRefresh(): Flow<AppResult<Instant?>> = flowOf(AppResult.Success(null))
            override suspend fun getEvents(fromDate: LocalDate, throughDate: LocalDate) = source.value
            override suspend fun backfill(): AppResult<Unit> = AppResult.Success(Unit)
            override suspend fun markRefreshSuccessful(at: Instant): AppResult<Unit> = AppResult.Success(Unit)
        }
        val results = mutableListOf<AppResult<List<ReleaseEvent>>>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.observeEvents(day, day.plusDays(1)).collect { results += it }
        }
        runCurrent()
        assertEquals(day, requestedFrom)
        assertEquals(listOf("today", "last"), (results.single() as AppResult.Success).value.map { it.title })
        source.value = AppResult.Failure(AppError.LocalStorageFailure)
        runCurrent()
        assertEquals(source.value, results.last())
        source.value = AppResult.Success(listOf(movie("new", day.plusDays(1))))
        runCurrent()
        assertEquals(listOf("new"), (results.last() as AppResult.Success).value.map { it.title })
        job.cancel()
        runCurrent()
        assertEquals(0, source.subscriptionCount.value)
    }

    private fun series(id: String, watched: Int = 1, watchedAt: Instant = Instant.EPOCH) = LibraryEntry(
        mediaRef = ExternalMediaRef(MediaSource.TMDB, id),
        mediaType = MediaType.SERIES,
        title = id,
        addedAt = Instant.EPOCH,
        progress = LibraryProgress.Series(SeriesProgress(watched, 3, 0, 1, watched == 3, lastWatchedAt = watchedAt))
    )

    private fun movie(id: String, date: LocalDate) = ReleaseEvent(
        mediaRef = ExternalMediaRef(MediaSource.TMDB, "parent-$id"),
        subject = ReleaseSubjectIdentity(
            MediaSource.TMDB,
            ReleaseSubjectType.MEDIA,
            id,
            ReleaseEventType.MOVIE_RELEASE
        ),
        mediaType = MediaType.MOVIE,
        eventDate = date,
        title = id
    )
}
