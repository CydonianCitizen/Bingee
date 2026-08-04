package com.cydoniancitizen.bingee.feature.home

import androidx.lifecycle.viewModelScope
import com.cydoniancitizen.bingee.core.model.CalendarRefreshOutcome
import com.cydoniancitizen.bingee.core.model.CalendarRefreshSummary
import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.core.model.ReleaseCalendarWindow
import com.cydoniancitizen.bingee.core.model.ReleaseEvent
import com.cydoniancitizen.bingee.core.model.ReleaseEventType
import com.cydoniancitizen.bingee.core.model.ReleaseSubjectIdentity
import com.cydoniancitizen.bingee.core.model.ReleaseSubjectType
import com.cydoniancitizen.bingee.core.result.AppError
import com.cydoniancitizen.bingee.core.result.AppResult
import com.cydoniancitizen.bingee.domain.calendar.CalendarDateSource
import com.cydoniancitizen.bingee.domain.repository.CalendarRefreshCoordinator
import com.cydoniancitizen.bingee.domain.repository.ReleaseCalendarRepository
import com.cydoniancitizen.bingee.testutil.MainDispatcherRule
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()
    private val now = Instant.parse("2026-08-03T12:00:00Z")
    private val today = LocalDate.of(2026, 8, 3)
    private val createdViewModels = mutableListOf<HomeViewModel>()

    @After
    fun cancelViewModels() {
        createdViewModels.forEach { it.viewModelScope.cancel() }
    }

    @Test
    fun localEventsLoadGroupAndBackfillOnceWithoutAutomaticRemoteRefresh() = runTest(mainDispatcherRule.dispatcher) {
        val repository = FakeCalendarRepository(
            listOf(
                event("movie", ReleaseSubjectType.MEDIA, ReleaseEventType.MOVIE_RELEASE, today),
                event("episode", ReleaseSubjectType.EPISODE, ReleaseEventType.EPISODE_AIRING, today)
            )
        )
        val coordinator = FakeCoordinator(success())

        val viewModel = viewModel(repository, coordinator)
        runCurrent()

        val content = viewModel.uiState.value.content as HomeContentState.Events
        assertEquals(1, content.groups.size)
        assertEquals(
            listOf(ReleaseEventType.EPISODE_AIRING, ReleaseEventType.MOVIE_RELEASE),
            content.groups.single().events.map { it.subject.eventType }
        )
        assertEquals(1, repository.backfillCalls)
        assertEquals(0, coordinator.calls)
        assertEquals(today.minusDays(7), repository.requestedFrom)
    }

    @Test
    fun manualPartialFailureKeepsEventsVisibleAndPreventsDuplicateTap() = runTest(mainDispatcherRule.dispatcher) {
        val repository = FakeCalendarRepository(
            listOf(event("movie", ReleaseSubjectType.MEDIA, ReleaseEventType.MOVIE_RELEASE, today))
        )
        val coordinator = FakeCoordinator(
            CalendarRefreshSummary(
                CalendarRefreshOutcome.PARTIAL_SUCCESS,
                titlesConsidered = 2,
                operationsSucceeded = 1,
                operationsFailed = 1,
                operationsSkipped = 0,
                representativeError = AppError.NetworkUnavailable
            )
        )
        val viewModel = viewModel(repository, coordinator)
        runCurrent()

        viewModel.refresh()
        viewModel.refresh()
        assertTrue(viewModel.uiState.value.content is HomeContentState.Events)
        runCurrent()

        assertEquals(1, coordinator.calls)
        assertTrue(viewModel.uiState.value.refresh is HomeRefreshState.Partial)
        assertTrue(viewModel.uiState.value.content is HomeContentState.Events)
    }

    @Test
    fun credentialAndCompleteFailureFeedbackNeverReplaceCachedContent() = runTest(mainDispatcherRule.dispatcher) {
        val repository = FakeCalendarRepository(
            listOf(event("movie", ReleaseSubjectType.MEDIA, ReleaseEventType.MOVIE_RELEASE, today))
        )
        val coordinator = FakeCoordinator(
            CalendarRefreshSummary(
                CalendarRefreshOutcome.CREDENTIAL_REQUIRED,
                1,
                0,
                0,
                1
            )
        )
        val viewModel = viewModel(repository, coordinator)
        runCurrent()
        viewModel.refresh()
        runCurrent()

        assertEquals(HomeRefreshState.CredentialRequired, viewModel.uiState.value.refresh)
        assertTrue(viewModel.uiState.value.content is HomeContentState.Events)

        coordinator.summary = CalendarRefreshSummary(
            CalendarRefreshOutcome.COMPLETE_FAILURE,
            1,
            0,
            1,
            0,
            AppError.NetworkUnavailable
        )
        viewModel.refresh()
        runCurrent()
        assertEquals(HomeRefreshState.Failed(AppError.NetworkUnavailable), viewModel.uiState.value.refresh)
        assertTrue(viewModel.uiState.value.content is HomeContentState.Events)
    }

    @Test
    fun lastSuccessfulRefreshAndLocalPersistenceFailureAreRepresented() = runTest(mainDispatcherRule.dispatcher) {
        val repository = FakeCalendarRepository(emptyList())
        repository.last.value = AppResult.Success(now)
        val viewModel = viewModel(repository, FakeCoordinator(success()))
        runCurrent()
        assertEquals(now, viewModel.uiState.value.lastSuccessfulRefreshAt)
        assertEquals(HomeContentState.Empty, viewModel.uiState.value.content)

        repository.events.value = AppResult.Failure(AppError.LocalStorageFailure)
        runCurrent()
        assertEquals(
            HomeContentState.Error(AppError.LocalStorageFailure),
            viewModel.uiState.value.content
        )
    }

    @Test
    fun dateRolloverRequeriesSevenDayWindowFromInjectedDateSource() = runTest(mainDispatcherRule.dispatcher) {
        val repository = FakeCalendarRepository(emptyList())
        val dates = FakeDateSource(today)
        val viewModel = HomeViewModel(
            repository,
            FakeCoordinator(success()),
            dates,
            ReleaseCalendarWindow()
        ).also(createdViewModels::add)
        runCurrent()
        assertEquals(LocalDate.of(2026, 7, 27), repository.requestedFrom)

        dates.advanceTo(today.plusDays(1))
        runCurrent()

        assertEquals(LocalDate.of(2026, 7, 28), repository.requestedFrom)
        assertEquals(today.plusDays(1), viewModel.uiState.value.today)
    }

    private fun event(
        id: String,
        subjectType: ReleaseSubjectType,
        type: ReleaseEventType,
        date: LocalDate
    ): ReleaseEvent {
        val series = subjectType != ReleaseSubjectType.MEDIA
        return ReleaseEvent(
            mediaRef = ExternalMediaRef(MediaSource.TMDB, "parent-$id"),
            subject = ReleaseSubjectIdentity(MediaSource.TMDB, subjectType, id, type),
            mediaType = if (series) MediaType.SERIES else MediaType.MOVIE,
            eventDate = date,
            title = "Title $id",
            seasonNumber = 1.takeIf { series },
            episodeNumber = 1.takeIf { subjectType == ReleaseSubjectType.EPISODE }
        )
    }

    private fun success() = CalendarRefreshSummary(
        CalendarRefreshOutcome.COMPLETE_SUCCESS,
        1,
        1,
        0,
        0
    )

    private fun viewModel(repository: ReleaseCalendarRepository, coordinator: CalendarRefreshCoordinator) =
        HomeViewModel(repository, coordinator, FakeDateSource(today), ReleaseCalendarWindow())
            .also(createdViewModels::add)

    private class FakeDateSource(initial: LocalDate) : CalendarDateSource {
        private val dates = MutableStateFlow(initial)
        override fun currentDate(): LocalDate = dates.value
        override fun observeDate(): Flow<LocalDate> = dates
        fun advanceTo(date: LocalDate) {
            dates.value = date
        }
    }

    private class FakeCalendarRepository(initial: List<ReleaseEvent>) : ReleaseCalendarRepository {
        val events = MutableStateFlow<AppResult<List<ReleaseEvent>>>(AppResult.Success(initial))
        val last = MutableStateFlow<AppResult<Instant?>>(AppResult.Success(null))
        var requestedFrom: LocalDate? = null
        var backfillCalls = 0

        override fun observeEvents(fromDate: LocalDate): Flow<AppResult<List<ReleaseEvent>>> {
            requestedFrom = fromDate
            return events
        }

        override fun observeLastSuccessfulRefresh(): Flow<AppResult<Instant?>> = last
        override suspend fun backfill(): AppResult<Unit> {
            backfillCalls++
            return AppResult.Success(Unit)
        }
        override suspend fun markRefreshSuccessful(at: Instant): AppResult<Unit> = AppResult.Success(Unit)
    }

    private class FakeCoordinator(var summary: CalendarRefreshSummary) : CalendarRefreshCoordinator {
        var calls = 0
        override suspend fun refresh(): CalendarRefreshSummary {
            calls++
            return summary
        }
    }
}
