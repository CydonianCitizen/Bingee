package com.cydoniancitizen.bingee.feature.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cydoniancitizen.bingee.core.model.CalendarRefreshOutcome
import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.LibraryMediaFilter
import com.cydoniancitizen.bingee.core.model.LibraryQuery
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.core.model.ReleaseEvent
import com.cydoniancitizen.bingee.core.result.AppResult
import com.cydoniancitizen.bingee.domain.policy.SeriesFollowPolicy
import com.cydoniancitizen.bingee.domain.repository.CalendarRefreshCoordinator
import com.cydoniancitizen.bingee.domain.repository.LibraryRepository
import com.cydoniancitizen.bingee.domain.repository.ReleaseCalendarRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
}

enum class NotificationRefreshState {
    Idle,
    Refreshing,
    Failed
}

data class NotificationsUiState(
    val contentState: NotificationsContentState = NotificationsContentState.Loading,
    val refreshState: NotificationRefreshState = NotificationRefreshState.Idle,
    val today: LocalDate = LocalDate.now()
)

@HiltViewModel
internal class NotificationsViewModel @Inject constructor(
    private val calendarRepository: ReleaseCalendarRepository,
    private val libraryRepository: LibraryRepository,
    private val refreshCoordinator: CalendarRefreshCoordinator,
    private val clock: Clock
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotificationsUiState(today = LocalDate.now(clock)))
    val uiState: StateFlow<NotificationsUiState> = _uiState.asStateFlow()

    init {
        observeNotificationCenter()
    }

    private fun observeNotificationCenter() {
        val today = LocalDate.now(clock)
        val fromDate = today.minusDays(RECENT_WINDOW_DAYS)

        viewModelScope.launch {
            combine(
                libraryRepository.observeEntries(LibraryQuery(mediaFilter = LibraryMediaFilter.TV_SERIES)),
                calendarRepository.observeEvents(fromDate)
            ) { libraryResult, eventsResult ->
                val followedSeriesRefs: Set<ExternalMediaRef> = when (libraryResult) {
                    is AppResult.Success ->
                        libraryResult.value
                            .filter(SeriesFollowPolicy::isFollowed)
                            .mapTo(mutableSetOf()) { it.mediaRef }
                    is AppResult.Failure -> emptySet()
                }

                val hasFollowedSeries = followedSeriesRefs.isNotEmpty()

                val events: List<ReleaseEvent> = when (eventsResult) {
                    is AppResult.Success -> eventsResult.value.filter { event ->
                        event.mediaType == MediaType.SERIES && event.mediaRef in followedSeriesRefs
                    }
                    is AppResult.Failure -> emptyList()
                }

                val contentState: NotificationsContentState = when {
                    !hasFollowedSeries -> NotificationsContentState.NoFollowedSeries
                    events.isEmpty() -> NotificationsContentState.NoEvents
                    else -> NotificationsContentState.Content(groupEvents(events, today))
                }

                contentState
            }.collect { contentState ->
                _uiState.update { it.copy(contentState = contentState, today = today) }
            }
        }
    }

    fun refresh() {
        if (_uiState.value.refreshState == NotificationRefreshState.Refreshing) return
        viewModelScope.launch {
            _uiState.update { it.copy(refreshState = NotificationRefreshState.Refreshing) }
            val summary = refreshCoordinator.refresh()
            val nextState = when (summary.outcome) {
                CalendarRefreshOutcome.COMPLETE_FAILURE -> NotificationRefreshState.Failed
                else -> NotificationRefreshState.Idle
            }
            _uiState.update { it.copy(refreshState = nextState) }
        }
    }

    fun dismissRefreshError() {
        _uiState.update { it.copy(refreshState = NotificationRefreshState.Idle) }
    }

    companion object {
        const val RECENT_WINDOW_DAYS = 30L

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
