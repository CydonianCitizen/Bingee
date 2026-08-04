package com.cydoniancitizen.bingee.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cydoniancitizen.bingee.core.model.CalendarRefreshOutcome
import com.cydoniancitizen.bingee.core.model.CalendarRefreshSummary
import com.cydoniancitizen.bingee.core.model.ReleaseCalendarWindow
import com.cydoniancitizen.bingee.core.model.ReleaseDateGroup
import com.cydoniancitizen.bingee.core.model.groupReleaseEvents
import com.cydoniancitizen.bingee.core.result.AppError
import com.cydoniancitizen.bingee.core.result.AppResult
import com.cydoniancitizen.bingee.domain.calendar.CalendarDateSource
import com.cydoniancitizen.bingee.domain.repository.CalendarRefreshCoordinator
import com.cydoniancitizen.bingee.domain.repository.ReleaseCalendarRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal sealed interface HomeContentState {
    data object Loading : HomeContentState
    data object Empty : HomeContentState
    data class Events(val groups: List<ReleaseDateGroup>) : HomeContentState
    data class Error(val error: AppError) : HomeContentState
}

internal sealed interface HomeRefreshState {
    data object Idle : HomeRefreshState
    data object Refreshing : HomeRefreshState
    data object Complete : HomeRefreshState
    data class Partial(val summary: CalendarRefreshSummary) : HomeRefreshState
    data class Failed(val error: AppError) : HomeRefreshState
    data object NoWork : HomeRefreshState
    data object CredentialRequired : HomeRefreshState
}

internal data class HomeUiState(
    val content: HomeContentState = HomeContentState.Loading,
    val refresh: HomeRefreshState = HomeRefreshState.Idle,
    val lastSuccessfulRefreshAt: Instant? = null,
    val today: LocalDate
)

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
internal class HomeViewModel @Inject constructor(
    private val calendarRepository: ReleaseCalendarRepository,
    private val refreshCoordinator: CalendarRefreshCoordinator,
    private val dateSource: CalendarDateSource,
    private val window: ReleaseCalendarWindow
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(HomeUiState(today = dateSource.currentDate()))
    private val localRetry = MutableStateFlow(0)
    val uiState: StateFlow<HomeUiState> = mutableUiState.asStateFlow()

    init {
        observeLocalCalendar()
        runLocalBackfill()
    }

    fun refresh() {
        while (true) {
            val current = mutableUiState.value
            if (current.refresh == HomeRefreshState.Refreshing) return
            if (mutableUiState.compareAndSet(current, current.copy(refresh = HomeRefreshState.Refreshing))) break
        }
        viewModelScope.launch {
            val summary = try {
                refreshCoordinator.refresh()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                CalendarRefreshSummary(
                    outcome = CalendarRefreshOutcome.COMPLETE_FAILURE,
                    titlesConsidered = 0,
                    operationsSucceeded = 0,
                    operationsFailed = 1,
                    operationsSkipped = 0,
                    representativeError = AppError.Unknown
                )
            }
            mutableUiState.update { it.copy(refresh = summary.toUiState()) }
        }
    }

    fun retryLocal() {
        localRetry.update { it + 1 }
        runLocalBackfill()
    }

    fun dismissRefreshFeedback() {
        if (mutableUiState.value.refresh != HomeRefreshState.Refreshing) {
            mutableUiState.update { it.copy(refresh = HomeRefreshState.Idle) }
        }
    }

    private fun observeLocalCalendar() {
        val datedEvents = combine(dateSource.observeDate(), localRetry) { today, _ -> today }
            .flatMapLatest { today ->
                calendarRepository.observeEvents(window.startDate(today))
                    .combine(calendarRepository.observeLastSuccessfulRefresh()) { events, last ->
                        Triple(today, events, last)
                    }
            }
        viewModelScope.launch {
            datedEvents.collect { (today, events, last) ->
                mutableUiState.update { previous ->
                    val content = when (events) {
                        is AppResult.Success -> {
                            val groups = groupReleaseEvents(events.value, today)
                            if (groups.isEmpty()) HomeContentState.Empty else HomeContentState.Events(groups)
                        }
                        is AppResult.Failure -> {
                            if (previous.content is HomeContentState.Events) {
                                previous.content
                            } else {
                                HomeContentState.Error(events.error)
                            }
                        }
                    }
                    previous.copy(
                        content = content,
                        today = today,
                        lastSuccessfulRefreshAt = (last as? AppResult.Success)?.value
                            ?: previous.lastSuccessfulRefreshAt
                    )
                }
            }
        }
    }

    private fun runLocalBackfill() {
        viewModelScope.launch {
            val result = calendarRepository.backfill()
            if (result is AppResult.Failure && mutableUiState.value.content !is HomeContentState.Events) {
                mutableUiState.update { it.copy(content = HomeContentState.Error(result.error)) }
            }
        }
    }
}

private fun CalendarRefreshSummary.toUiState(): HomeRefreshState = when (outcome) {
    CalendarRefreshOutcome.COMPLETE_SUCCESS -> HomeRefreshState.Complete
    CalendarRefreshOutcome.PARTIAL_SUCCESS -> HomeRefreshState.Partial(this)
    CalendarRefreshOutcome.COMPLETE_FAILURE ->
        HomeRefreshState.Failed(representativeError ?: AppError.Unknown)
    CalendarRefreshOutcome.NO_WORK -> HomeRefreshState.NoWork
    CalendarRefreshOutcome.CREDENTIAL_REQUIRED -> HomeRefreshState.CredentialRequired
}
