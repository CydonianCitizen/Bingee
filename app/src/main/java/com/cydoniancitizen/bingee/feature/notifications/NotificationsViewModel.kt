package com.cydoniancitizen.bingee.feature.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cydoniancitizen.bingee.core.model.CalendarRefreshOutcome
import com.cydoniancitizen.bingee.core.model.LibraryMediaFilter
import com.cydoniancitizen.bingee.core.model.LibraryQuery
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.core.model.ReleaseEvent
import com.cydoniancitizen.bingee.core.result.AppError
import com.cydoniancitizen.bingee.core.result.AppResult
import com.cydoniancitizen.bingee.core.result.errorOrNull
import com.cydoniancitizen.bingee.domain.calendar.CalendarDateSource
import com.cydoniancitizen.bingee.domain.policy.SeriesFollowPolicy
import com.cydoniancitizen.bingee.domain.repository.CalendarRefreshCoordinator
import com.cydoniancitizen.bingee.domain.repository.LibraryRepository
import com.cydoniancitizen.bingee.domain.repository.ReleaseCalendarRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class NotificationGroupCategory {
    UPCOMING,
    TODAY,
    THIS_WEEK,
    EARLIER
}

data class NotificationGroup(val category: NotificationGroupCategory, val items: List<ReleaseEvent>)

sealed interface NotificationsContentState {
    data object Loading : NotificationsContentState
    data object NoFollowedSeries : NotificationsContentState
    data object NoEvents : NotificationsContentState
    data class Content(val groups: List<NotificationGroup>) : NotificationsContentState
    data class Error(val error: AppError) : NotificationsContentState
}

enum class NotificationRefreshState {
    Idle,
    Refreshing,
    Failed
}

data class NotificationsUiState(
    val contentState: NotificationsContentState = NotificationsContentState.Loading,
    val refreshState: NotificationRefreshState = NotificationRefreshState.Idle,
    val today: LocalDate = LocalDate.now(),
    val error: AppError? = null
)

@HiltViewModel
internal class NotificationsViewModel @Inject constructor(
    private val calendarRepository: ReleaseCalendarRepository,
    private val libraryRepository: LibraryRepository,
    private val refreshCoordinator: CalendarRefreshCoordinator,
    private val dateSource: CalendarDateSource
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotificationsUiState(today = dateSource.currentDate()))
    val uiState: StateFlow<NotificationsUiState> = _uiState.asStateFlow()
    private var refreshJob: Job? = null
    private var notificationObservationJob: Job? = null
    private var cachedContentState: NotificationsContentState? = null

    init {
        observeNotificationCenter()
    }

    private fun observeNotificationCenter() {
        notificationObservationJob?.cancel()
        notificationObservationJob = viewModelScope.launch {
            dateSource.observeDate().collectLatest { today ->
                val fromDate = today.minusDays(RECENT_WINDOW_DAYS)
                val throughDate = today.plusDays(UPCOMING_WINDOW_DAYS)
                combine(
                    libraryRepository.observeEntries(LibraryQuery(mediaFilter = LibraryMediaFilter.TV_SERIES)),
                    calendarRepository.observeEvents(fromDate, throughDate)
                ) { libraryResult, eventsResult ->
                    val error = libraryResult.errorOrNull() ?: eventsResult.errorOrNull()
                    if (error != null) {
                        AppResult.Failure(error)
                    } else {
                        val followedSeriesRefs = (libraryResult as AppResult.Success).value
                            .filter(SeriesFollowPolicy::isFollowed)
                            .mapTo(mutableSetOf()) { it.mediaRef }
                        val events = (eventsResult as AppResult.Success).value.filter { event ->
                            event.mediaType == MediaType.SERIES && event.mediaRef in followedSeriesRefs
                        }
                        val contentState = when {
                            followedSeriesRefs.isEmpty() -> NotificationsContentState.NoFollowedSeries
                            events.isEmpty() -> NotificationsContentState.NoEvents
                            else -> NotificationsContentState.Content(groupEvents(events, today))
                        }
                        AppResult.Success(contentState)
                    }
                }.catch { throwable ->
                    if (throwable is CancellationException) throw throwable
                    emit(AppResult.Failure(AppError.Unknown))
                }.collect { result ->
                    when (result) {
                        is AppResult.Success -> {
                            cachedContentState = result.value
                            _uiState.update { state ->
                                state.copy(
                                    contentState = result.value,
                                    today = today,
                                    refreshState = if (state.error != null) {
                                        NotificationRefreshState.Idle
                                    } else {
                                        state.refreshState
                                    },
                                    error = null
                                )
                            }
                        }
                        is AppResult.Failure -> {
                            _uiState.update { state ->
                                state.copy(
                                    contentState = cachedContentState
                                        ?: NotificationsContentState.Error(result.error),
                                    today = today,
                                    refreshState = NotificationRefreshState.Failed,
                                    error = result.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    fun refresh() {
        if (refreshJob?.isActive == true) return
        _uiState.update { it.copy(refreshState = NotificationRefreshState.Refreshing) }
        refreshJob = viewModelScope.launch {
            try {
                val summary = refreshCoordinator.refresh()
                _uiState.update { state ->
                    if (summary.outcome == CalendarRefreshOutcome.COMPLETE_FAILURE) {
                        val error = summary.representativeError ?: AppError.Unknown
                        state.copy(
                            contentState = if (state.contentState == NotificationsContentState.Loading) {
                                NotificationsContentState.Error(error)
                            } else {
                                state.contentState
                            },
                            refreshState = NotificationRefreshState.Failed,
                            error = error
                        )
                    } else {
                        state.copy(refreshState = NotificationRefreshState.Idle)
                    }
                }
                if (summary.outcome != CalendarRefreshOutcome.COMPLETE_FAILURE) {
                    observeNotificationCenter()
                }
            } finally {
                refreshJob = null
            }
        }
    }

    fun dismissRefreshError() {
        _uiState.update { it.copy(refreshState = NotificationRefreshState.Idle, error = null) }
    }

    companion object {
        const val RECENT_WINDOW_DAYS = 30L
        const val UPCOMING_WINDOW_DAYS = 30L

        internal fun groupEvents(events: List<ReleaseEvent>, today: LocalDate): List<NotificationGroup> {
            val upcoming = mutableListOf<ReleaseEvent>()
            val todayEvents = mutableListOf<ReleaseEvent>()
            val thisWeek = mutableListOf<ReleaseEvent>()
            val earlier = mutableListOf<ReleaseEvent>()

            val sevenDaysAgo = today.minusDays(7)
            val thirtyDaysAgo = today.minusDays(RECENT_WINDOW_DAYS)

            for (event in events) {
                when {
                    event.eventDate > today -> upcoming.add(event)
                    event.eventDate == today -> todayEvents.add(event)
                    event.eventDate >= sevenDaysAgo -> thisWeek.add(event)
                    event.eventDate >= thirtyDaysAgo -> earlier.add(event)
                }
            }

            // Sorting:
            // Upcoming: soonest first (ascending by eventDate), then title
            upcoming.sortWith(compareBy<ReleaseEvent> { it.eventDate }.thenBy { it.title.lowercase() })

            // Past events (Today, This week, Earlier): newest first (descending by eventDate), then title
            val pastComparator = compareByDescending<ReleaseEvent> { it.eventDate }.thenBy { it.title.lowercase() }
            todayEvents.sortWith(pastComparator)
            thisWeek.sortWith(pastComparator)
            earlier.sortWith(pastComparator)

            val result = mutableListOf<NotificationGroup>()
            if (upcoming.isNotEmpty()) {
                result.add(NotificationGroup(NotificationGroupCategory.UPCOMING, upcoming))
            }
            if (todayEvents.isNotEmpty()) {
                result.add(NotificationGroup(NotificationGroupCategory.TODAY, todayEvents))
            }
            if (thisWeek.isNotEmpty()) {
                result.add(NotificationGroup(NotificationGroupCategory.THIS_WEEK, thisWeek))
            }
            if (earlier.isNotEmpty()) {
                result.add(NotificationGroup(NotificationGroupCategory.EARLIER, earlier))
            }
            return result
        }
    }
}
