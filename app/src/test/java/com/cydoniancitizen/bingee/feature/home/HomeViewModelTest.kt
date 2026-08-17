package com.cydoniancitizen.bingee.feature.home

import androidx.lifecycle.viewModelScope
import com.cydoniancitizen.bingee.core.model.CalendarRefreshOutcome
import com.cydoniancitizen.bingee.core.model.CalendarRefreshSummary
import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.LibraryEntry
import com.cydoniancitizen.bingee.core.model.LibraryQuery
import com.cydoniancitizen.bingee.core.model.MediaSearchResult
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
import com.cydoniancitizen.bingee.domain.repository.FeaturedReleasesRepository
import com.cydoniancitizen.bingee.domain.repository.LibraryRepository
import com.cydoniancitizen.bingee.domain.repository.ReleaseCalendarRepository
import com.cydoniancitizen.bingee.testutil.MainDispatcherRule
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
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
    fun newestRefreshWinsAndCancelledRefreshDoesNotSurfaceAsError() = runTest(mainDispatcherRule.dispatcher) {
        val firstResult = CompletableDeferred<CalendarRefreshSummary>()
        val secondResult = CompletableDeferred<CalendarRefreshSummary>()
        val secondStarted = CompletableDeferred<Unit>()
        val coordinator = FakeCoordinator(success()).apply {
            refreshAction = { call ->
                when (call) {
                    1 -> withContext(NonCancellable) { firstResult.await() }
                    2 -> {
                        secondStarted.complete(Unit)
                        secondResult.await()
                    }
                    else -> success()
                }
            }
        }
        val viewModel = viewModel(FakeCalendarRepository(emptyList()), coordinator)
        runCurrent()

        viewModel.refresh()
        runCurrent()
        viewModel.refresh()
        runCurrent()
        secondStarted.await()

        secondResult.complete(success())
        runCurrent()
        assertEquals(HomeRefreshState.Complete, viewModel.uiState.value.refresh)

        firstResult.complete(
            CalendarRefreshSummary(CalendarRefreshOutcome.COMPLETE_FAILURE, 1, 0, 1, 0, AppError.Unknown)
        )
        runCurrent()
        assertEquals(HomeRefreshState.Complete, viewModel.uiState.value.refresh)
        assertEquals(2, coordinator.calls)
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
            ReleaseCalendarWindow(),
            FakeFeaturedRepo(),
            FakeLibraryRepo()
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

    private fun viewModel(
        repository: ReleaseCalendarRepository,
        coordinator: CalendarRefreshCoordinator,
        featuredRepository: com.cydoniancitizen.bingee.domain.repository.FeaturedReleasesRepository =
            FakeFeaturedRepo(),
        libraryRepository: com.cydoniancitizen.bingee.domain.repository.LibraryRepository = FakeLibraryRepo()
    ) = HomeViewModel(
        repository,
        coordinator,
        FakeDateSource(today),
        ReleaseCalendarWindow(),
        featuredRepository,
        libraryRepository
    ).also(createdViewModels::add)

    private class FakeFeaturedRepo : FeaturedReleasesRepository {
        override suspend fun getFeaturedReleases(): AppResult<List<MediaSearchResult>> = AppResult.Success(emptyList())
    }

    private class FakeLibraryRepo : LibraryRepository {
        override fun observeEntries(query: LibraryQuery): Flow<AppResult<List<LibraryEntry>>> =
            MutableStateFlow(AppResult.Success(emptyList()))

        override fun observeEntryCount(): Flow<AppResult<Int>> = MutableStateFlow(AppResult.Success(0))

        override fun observeEntry(ref: ExternalMediaRef): Flow<AppResult<LibraryEntry?>> =
            MutableStateFlow(AppResult.Success(null))

        override fun observeMembershipRefs(): Flow<AppResult<Set<ExternalMediaRef>>> =
            MutableStateFlow(AppResult.Success(emptySet()))
        override fun observePersonalViewing() =
            MutableStateFlow<AppResult<List<com.cydoniancitizen.bingee.core.model.PersonalViewingEntry>>>(
                AppResult.Success(emptyList())
            )

        override suspend fun add(result: MediaSearchResult): AppResult<LibraryEntry> =
            AppResult.Failure(AppError.Unknown)

        override suspend fun add(ref: ExternalMediaRef): AppResult<LibraryEntry> = AppResult.Failure(AppError.Unknown)

        override suspend fun remove(ref: ExternalMediaRef): AppResult<Unit> = AppResult.Success(Unit)

        override suspend fun isInLibrary(ref: ExternalMediaRef): AppResult<Boolean> = AppResult.Success(false)

        override suspend fun setFavorite(ref: ExternalMediaRef, isFavorite: Boolean): AppResult<Unit> =
            AppResult.Success(Unit)

        override suspend fun setFavorite(result: MediaSearchResult, isFavorite: Boolean): AppResult<Unit> =
            AppResult.Success(Unit)

        override suspend fun setWatchedDate(ref: ExternalMediaRef, watchedDate: LocalDate?): AppResult<Unit> =
            AppResult.Success(Unit)
    }

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
        override suspend fun getEvents(fromDate: LocalDate, throughDate: LocalDate): AppResult<List<ReleaseEvent>> =
            AppResult.Success(emptyList())
        override suspend fun backfill(): AppResult<Unit> {
            backfillCalls++
            return AppResult.Success(Unit)
        }
        override suspend fun markRefreshSuccessful(at: Instant): AppResult<Unit> = AppResult.Success(Unit)
    }

    private class FakeCoordinator(var summary: CalendarRefreshSummary) : CalendarRefreshCoordinator {
        var calls = 0
        var refreshAction: suspend (Int) -> CalendarRefreshSummary = { summary }
        override suspend fun refresh(): CalendarRefreshSummary {
            calls++
            return refreshAction(calls)
        }

        override suspend fun refresh(
            targets: List<com.cydoniancitizen.bingee.core.model.BackgroundRefreshTarget>
        ): CalendarRefreshSummary = refresh()
    }
}
