package com.cydoniancitizen.bingee.feature.notifications

import com.cydoniancitizen.bingee.core.model.CalendarRefreshOutcome
import com.cydoniancitizen.bingee.core.model.CalendarRefreshSummary
import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.LibraryEntry
import com.cydoniancitizen.bingee.core.model.LibraryProgress
import com.cydoniancitizen.bingee.core.model.LibraryQuery
import com.cydoniancitizen.bingee.core.model.MediaSearchResult
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.core.model.ReleaseEvent
import com.cydoniancitizen.bingee.core.model.ReleaseEventType
import com.cydoniancitizen.bingee.core.model.ReleaseSubjectIdentity
import com.cydoniancitizen.bingee.core.model.ReleaseSubjectType
import com.cydoniancitizen.bingee.core.model.SeriesProgress
import com.cydoniancitizen.bingee.core.result.AppError
import com.cydoniancitizen.bingee.core.result.AppResult
import com.cydoniancitizen.bingee.domain.calendar.CalendarDateSource
import com.cydoniancitizen.bingee.domain.repository.CalendarRefreshCoordinator
import com.cydoniancitizen.bingee.domain.repository.LibraryRepository
import com.cydoniancitizen.bingee.domain.repository.ReleaseCalendarRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val fixedClock = Clock.fixed(Instant.parse("2026-08-08T12:00:00Z"), ZoneId.of("UTC"))
    private val today = LocalDate.now(fixedClock)

    private val fakeLibraryRepository = FakeLibraryRepository()
    private val fakeCalendarRepository = FakeReleaseCalendarRepository()
    private val fakeRefreshCoordinator = FakeCalendarRefreshCoordinator()
    private val fakeDateSource = FakeDateSource(today)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun noFollowedSeriesShowsEmptyNoFollowedState() = runTest {
        fakeLibraryRepository.setEntries(emptyList())
        fakeCalendarRepository.setEvents(emptyList())

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(NotificationsContentState.NoFollowedSeries, state.contentState)
    }

    @Test
    fun followedSeriesWithNoEventsShowsEmptyNoEventsState() = runTest {
        val seriesEntry = createLibraryEntry("101", MediaType.SERIES)
        fakeLibraryRepository.setEntries(listOf(seriesEntry))
        fakeCalendarRepository.setEvents(emptyList())

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(NotificationsContentState.NoEvents, state.contentState)
    }

    @Test
    fun initialLoadFailureShowsErrorNotEmptyState() = runTest {
        fakeLibraryRepository.setEntries(listOf(createLibraryEntry("101", MediaType.SERIES)))
        fakeCalendarRepository.setFailure(AppError.LocalStorageFailure)

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.contentState is NotificationsContentState.Error)
        assertEquals(NotificationRefreshState.Failed, viewModel.uiState.value.refreshState)
    }

    @Test
    fun loadFailurePreservesCachedContent() = runTest {
        val series = createLibraryEntry("101", MediaType.SERIES)
        fakeLibraryRepository.setEntries(listOf(series))
        fakeCalendarRepository.setEvents(listOf(createEvent("101", MediaType.SERIES, today)))

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        fakeCalendarRepository.setFailure(AppError.LocalStorageFailure)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.contentState is NotificationsContentState.Content)
        assertEquals(NotificationRefreshState.Failed, viewModel.uiState.value.refreshState)
    }

    @Test
    fun retryRecoversFromLoadFailure() = runTest {
        val series = createLibraryEntry("101", MediaType.SERIES)
        val event = createEvent("101", MediaType.SERIES, today)
        fakeLibraryRepository.setEntries(listOf(series))
        fakeCalendarRepository.setFailure(AppError.LocalStorageFailure)
        fakeCalendarRepository.completeObservationAfterFailure = true
        fakeRefreshCoordinator.refreshAction = { fakeCalendarRepository.setEvents(listOf(event)) }

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.refresh()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.contentState is NotificationsContentState.Content)
        assertEquals(NotificationRefreshState.Idle, viewModel.uiState.value.refreshState)
        assertEquals(null, viewModel.uiState.value.error)
    }

    @Test
    fun cancellationDoesNotBecomeLoadError() = runTest {
        fakeCalendarRepository.cancelOnObserve = true

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(NotificationsContentState.Loading, viewModel.uiState.value.contentState)
        assertEquals(null, viewModel.uiState.value.error)
    }

    @Test
    fun followedSeriesProducesEventsWatchLaterInProgressCompletedAndAnime() = runTest {
        val watchLater = createLibraryEntry(
            "101",
            MediaType.SERIES,
            LibraryProgress.Series(SeriesProgress(0, 10, 0, 1, false))
        )
        val inProgress = createLibraryEntry(
            "102",
            MediaType.SERIES,
            LibraryProgress.Series(SeriesProgress(5, 10, 0, 1, false))
        )
        val completed = createLibraryEntry(
            "103",
            MediaType.SERIES,
            LibraryProgress.Series(SeriesProgress(10, 10, 1, 1, true))
        )
        val animeAsSeries = createLibraryEntry("104", MediaType.SERIES) // TMDB anime TV series
        val movieEntry = createLibraryEntry("201", MediaType.MOVIE)

        fakeLibraryRepository.setEntries(listOf(watchLater, inProgress, completed, animeAsSeries, movieEntry))

        val watchLaterEvent = createEvent("101", MediaType.SERIES, today)
        val inProgressEvent = createEvent("102", MediaType.SERIES, today)
        val completedEvent = createEvent("103", MediaType.SERIES, today.plusDays(5))
        val animeEvent = createEvent("104", MediaType.SERIES, today.minusDays(2))
        val movieEvent = createEvent("201", MediaType.MOVIE, today) // Excluded!
        val unrelatedSeriesEvent = createEvent("999", MediaType.SERIES, today) // Excluded!

        fakeCalendarRepository.setEvents(
            listOf(watchLaterEvent, inProgressEvent, completedEvent, animeEvent, movieEvent, unrelatedSeriesEvent)
        )

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.contentState is NotificationsContentState.Content)
        val content = state.contentState as NotificationsContentState.Content
        val allEvents = content.groups.flatMap { it.items }

        assertEquals(4, allEvents.size)
        assertTrue(allEvents.any { it.mediaRef.externalId == "101" })
        assertTrue(allEvents.any { it.mediaRef.externalId == "102" })
        assertTrue(allEvents.any { it.mediaRef.externalId == "103" })
        assertTrue(allEvents.any { it.mediaRef.externalId == "104" })
        assertTrue(allEvents.none { it.mediaRef.externalId == "201" }) // Movie excluded
        assertTrue(allEvents.none { it.mediaRef.externalId == "999" }) // Unrelated excluded
    }

    @Test
    fun eventOrderingAndGroupingCategoriesAreCorrect() = runTest {
        val series = createLibraryEntry("101", MediaType.SERIES)
        fakeLibraryRepository.setEntries(listOf(series))

        val upcomingEvent = createEvent("101", MediaType.SERIES, today.plusDays(3))
        val todayEvent = createEvent("101", MediaType.SERIES, today)
        val thisWeekEvent = createEvent("101", MediaType.SERIES, today.minusDays(3))
        val earlierEvent = createEvent("101", MediaType.SERIES, today.minusDays(15))

        fakeCalendarRepository.setEvents(listOf(earlierEvent, thisWeekEvent, todayEvent, upcomingEvent))

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.contentState is NotificationsContentState.Content)
        val groups = (state.contentState as NotificationsContentState.Content).groups

        assertEquals(4, groups.size)
        assertEquals(NotificationGroupCategory.UPCOMING, groups[0].category)
        assertEquals(NotificationGroupCategory.TODAY, groups[1].category)
        assertEquals(NotificationGroupCategory.THIS_WEEK, groups[2].category)
        assertEquals(NotificationGroupCategory.EARLIER, groups[3].category)
    }

    @Test
    fun observationUsesBoundedPastAndFutureWindow() = runTest {
        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(today.minusDays(NotificationsViewModel.RECENT_WINDOW_DAYS), fakeCalendarRepository.observedFrom)
        assertEquals(
            today.plusDays(NotificationsViewModel.UPCOMING_WINDOW_DAYS),
            fakeCalendarRepository.observedThrough
        )
        assertEquals(today, viewModel.uiState.value.today)
    }

    @Test
    fun dateRolloverUpdatesGroupingWithoutWallClockWait() = runTest {
        val series = createLibraryEntry("101", MediaType.SERIES)
        fakeLibraryRepository.setEntries(listOf(series))
        fakeCalendarRepository.setEvents(listOf(createEvent("101", MediaType.SERIES, today.plusDays(1))))

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(today, viewModel.uiState.value.today)
        assertEquals(
            NotificationGroupCategory.UPCOMING,
            (viewModel.uiState.value.contentState as NotificationsContentState.Content).groups.single().category
        )

        fakeDateSource.advanceTo(today.plusDays(1))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(today.plusDays(1), viewModel.uiState.value.today)
        assertEquals(
            NotificationGroupCategory.TODAY,
            (viewModel.uiState.value.contentState as NotificationsContentState.Content).groups.single().category
        )
    }

    @Test
    fun refreshSuccessUpdatesState() = runTest {
        val series = createLibraryEntry("101", MediaType.SERIES)
        fakeLibraryRepository.setEntries(listOf(series))
        fakeCalendarRepository.setEvents(listOf(createEvent("101", MediaType.SERIES, today)))
        fakeRefreshCoordinator.summaryToReturn =
            CalendarRefreshSummary(CalendarRefreshOutcome.COMPLETE_SUCCESS, 1, 0, 0, 0, null)

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.refresh()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(NotificationRefreshState.Idle, state.refreshState)
    }

    @Test
    fun refreshFailurePreservesCachedContent() = runTest {
        val series = createLibraryEntry("101", MediaType.SERIES)
        fakeLibraryRepository.setEntries(listOf(series))
        val cachedEvent = createEvent("101", MediaType.SERIES, today)
        fakeCalendarRepository.setEvents(listOf(cachedEvent))
        fakeRefreshCoordinator.summaryToReturn = CalendarRefreshSummary(
            CalendarRefreshOutcome.COMPLETE_FAILURE,
            0,
            1,
            0,
            0,
            AppError.NetworkUnavailable
        )

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.refresh()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(NotificationRefreshState.Failed, state.refreshState)
        assertTrue(state.contentState is NotificationsContentState.Content)
        val content = state.contentState as NotificationsContentState.Content
        assertEquals(1, content.groups.flatMap { it.items }.size)
    }

    private fun createViewModel() = NotificationsViewModel(
        calendarRepository = fakeCalendarRepository,
        libraryRepository = fakeLibraryRepository,
        refreshCoordinator = fakeRefreshCoordinator,
        dateSource = fakeDateSource
    )

    private fun createLibraryEntry(
        id: String,
        mediaType: MediaType,
        progress: LibraryProgress = LibraryProgress.Unavailable
    ) = LibraryEntry(
        mediaRef = ExternalMediaRef(MediaSource.TMDB, id),
        mediaType = mediaType,
        title = "Series $id",
        addedAt = Instant.EPOCH,
        inLibrary = true,
        progress = progress
    )

    private fun createEvent(id: String, mediaType: MediaType, date: LocalDate) = ReleaseEvent(
        mediaRef = ExternalMediaRef(MediaSource.TMDB, id),
        subject = ReleaseSubjectIdentity(
            MediaSource.TMDB,
            if (mediaType == MediaType.MOVIE) ReleaseSubjectType.MEDIA else ReleaseSubjectType.EPISODE,
            "ep-$id",
            if (mediaType == MediaType.MOVIE) ReleaseEventType.MOVIE_RELEASE else ReleaseEventType.EPISODE_AIRING
        ),
        mediaType = mediaType,
        eventDate = date,
        title = "Title $id",
        seasonNumber = 1.takeIf { mediaType == MediaType.SERIES },
        episodeNumber = 1.takeIf { mediaType == MediaType.SERIES },
        subjectTitle = "Episode title $id"
    )

    private class FakeLibraryRepository : LibraryRepository {
        private val entriesFlow = MutableStateFlow<AppResult<List<LibraryEntry>>>(AppResult.Success(emptyList()))

        fun setEntries(list: List<LibraryEntry>) {
            entriesFlow.value = AppResult.Success(list)
        }

        override fun observeEntries(query: LibraryQuery): Flow<AppResult<List<LibraryEntry>>> = entriesFlow
        override fun observeEntryCount(): Flow<AppResult<Int>> =
            MutableStateFlow(AppResult.Success((entriesFlow.value as? AppResult.Success)?.value?.size ?: 0))
        override fun observeEntry(ref: ExternalMediaRef): Flow<AppResult<LibraryEntry?>> =
            MutableStateFlow(AppResult.Success(null))
        override fun observeMembershipRefs(): Flow<AppResult<Set<ExternalMediaRef>>> =
            MutableStateFlow(AppResult.Success(emptySet()))
        override suspend fun add(result: MediaSearchResult): AppResult<LibraryEntry> = error("Unused")
        override suspend fun add(ref: ExternalMediaRef): AppResult<LibraryEntry> = error("Unused")
        override suspend fun remove(ref: ExternalMediaRef): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun isInLibrary(ref: ExternalMediaRef): AppResult<Boolean> = AppResult.Success(true)
        override suspend fun setFavorite(ref: ExternalMediaRef, isFavorite: Boolean): AppResult<Unit> =
            AppResult.Success(Unit)
        override suspend fun setFavorite(result: MediaSearchResult, isFavorite: Boolean): AppResult<Unit> =
            AppResult.Success(Unit)
        override suspend fun setWatchedDate(ref: ExternalMediaRef, watchedDate: LocalDate?): AppResult<Unit> =
            AppResult.Success(Unit)
    }

    private class FakeReleaseCalendarRepository : ReleaseCalendarRepository {
        private val eventsFlow = MutableStateFlow<AppResult<List<ReleaseEvent>>>(AppResult.Success(emptyList()))
        var cancelOnObserve = false
        var completeObservationAfterFailure = false
        var observedFrom: LocalDate? = null
        var observedThrough: LocalDate? = null

        fun setEvents(list: List<ReleaseEvent>) {
            eventsFlow.value = AppResult.Success(list)
        }

        fun setFailure(error: AppError) {
            eventsFlow.value = AppResult.Failure(error)
        }

        override fun observeEvents(fromDate: LocalDate): Flow<AppResult<List<ReleaseEvent>>> = observe()

        override fun observeEvents(fromDate: LocalDate, throughDate: LocalDate): Flow<AppResult<List<ReleaseEvent>>> {
            observedFrom = fromDate
            observedThrough = throughDate
            return observe()
        }

        private fun observe(): Flow<AppResult<List<ReleaseEvent>>> = when {
            cancelOnObserve -> flow { throw CancellationException("test cancellation") }
            completeObservationAfterFailure -> flow { emit(eventsFlow.value) }
            else -> eventsFlow
        }
        override fun observeLastSuccessfulRefresh(): Flow<AppResult<Instant?>> =
            MutableStateFlow(AppResult.Success(null))
        override suspend fun getEvents(fromDate: LocalDate, throughDate: LocalDate): AppResult<List<ReleaseEvent>> =
            eventsFlow.value
        override suspend fun backfill(): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun markRefreshSuccessful(at: Instant): AppResult<Unit> = AppResult.Success(Unit)
    }

    private class FakeDateSource(initial: LocalDate) : CalendarDateSource {
        private val dates = MutableStateFlow(initial)

        override fun currentDate(): LocalDate = dates.value

        override fun observeDate(): Flow<LocalDate> = dates

        fun advanceTo(date: LocalDate) {
            dates.value = date
        }
    }

    private class FakeCalendarRefreshCoordinator : CalendarRefreshCoordinator {
        var summaryToReturn = CalendarRefreshSummary(CalendarRefreshOutcome.COMPLETE_SUCCESS, 0, 0, 0, 0, null)
        var refreshAction: () -> Unit = {}

        override suspend fun refresh(): CalendarRefreshSummary {
            refreshAction()
            return summaryToReturn
        }
        override suspend fun refresh(
            targets: List<com.cydoniancitizen.bingee.core.model.BackgroundRefreshTarget>
        ): CalendarRefreshSummary = summaryToReturn
    }
}
