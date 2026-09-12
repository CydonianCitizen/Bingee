package com.cydoniancitizen.bingee.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cydoniancitizen.bingee.core.model.CalendarRefreshOutcome
import com.cydoniancitizen.bingee.core.model.CalendarRefreshSummary
import com.cydoniancitizen.bingee.core.model.ContinueWatchingItem
import com.cydoniancitizen.bingee.core.model.EpisodePosition
import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.core.model.ReleaseCalendarWindow
import com.cydoniancitizen.bingee.core.model.ReleaseDateGroup
import com.cydoniancitizen.bingee.core.model.groupReleaseEvents
import com.cydoniancitizen.bingee.core.result.AppError
import com.cydoniancitizen.bingee.core.result.AppResult
import com.cydoniancitizen.bingee.domain.calendar.CalendarDateSource
import com.cydoniancitizen.bingee.domain.repository.CalendarRefreshCoordinator
import com.cydoniancitizen.bingee.domain.repository.ReleaseCalendarRepository
import com.cydoniancitizen.bingee.domain.repository.WatchProgressRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
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

/** One-shot Home message: an episode marked from Continue Watching, or a Home action that failed. */
internal sealed interface HomeSnackbar {
    data class EpisodeMarked(val title: String, val episode: EpisodePosition, val episodeRef: ExternalMediaRef) :
        HomeSnackbar

    data class Failed(val error: AppError) : HomeSnackbar
}

internal data class HomeUiState(
    val content: HomeContentState = HomeContentState.Loading,
    val refresh: HomeRefreshState = HomeRefreshState.Idle,
    val lastSuccessfulRefreshAt: Instant? = null,
    val today: LocalDate,
    val featuredMovies: List<com.cydoniancitizen.bingee.core.model.MediaSearchResult> = emptyList(),
    val featuredSeries: List<com.cydoniancitizen.bingee.core.model.MediaSearchResult> = emptyList(),
    val continueWatching: List<ContinueWatchingItem> = emptyList(),
    /** Keyed by (reference, type): TMDB reuses one ID for a movie and an unrelated series. */
    val libraryMemberships: Set<Pair<ExternalMediaRef, MediaType>> = emptySet(),
    val addingToWatchlist: Set<Pair<ExternalMediaRef, MediaType>> = emptySet(),
    /** Series whose next episode is being written, so a second tap cannot mark it twice. */
    val markingEpisodes: Set<ExternalMediaRef> = emptySet(),
    val snackbar: HomeSnackbar? = null
)

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
internal class HomeViewModel @Inject constructor(
    private val calendarRepository: ReleaseCalendarRepository,
    private val refreshCoordinator: CalendarRefreshCoordinator,
    private val dateSource: CalendarDateSource,
    private val window: ReleaseCalendarWindow,
    private val featuredRepository: com.cydoniancitizen.bingee.domain.repository.FeaturedReleasesRepository,
    private val libraryRepository: com.cydoniancitizen.bingee.domain.repository.LibraryRepository,
    private val watchProgressRepository: WatchProgressRepository
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(HomeUiState(today = dateSource.currentDate()))
    private val localRetry = MutableStateFlow(0)
    private var refreshJob: Job? = null
    private var refreshGeneration = 0L
    val uiState: StateFlow<HomeUiState> = mutableUiState.asStateFlow()

    init {
        observeLocalCalendar()
        observeLibraryMemberships()
        observeContinueWatching()
        replaceRefreshOperation { generation ->
            coroutineScope {
                launch { backfillLocalCalendar(generation) }
                launch { loadFeaturedReleases(generation) }
            }
        }
    }

    fun refresh() {
        mutableUiState.update { it.copy(refresh = HomeRefreshState.Refreshing) }
        replaceRefreshOperation { generation ->
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
            if (generation == refreshGeneration) {
                mutableUiState.update { it.copy(refresh = summary.toUiState()) }
                loadFeaturedReleases(generation)
            }
        }
    }

    private fun replaceRefreshOperation(block: suspend (Long) -> Unit) {
        refreshJob?.cancel()
        val generation = ++refreshGeneration
        refreshJob = viewModelScope.launch {
            try {
                block(generation)
            } finally {
                if (generation == refreshGeneration) refreshJob = null
            }
        }
    }

    fun addToWatchlist(item: com.cydoniancitizen.bingee.core.model.MediaSearchResult) {
        val ref = item.externalRef to item.mediaType
        if (ref in mutableUiState.value.libraryMemberships || ref in mutableUiState.value.addingToWatchlist) return
        mutableUiState.update { it.copy(addingToWatchlist = it.addingToWatchlist + ref) }
        viewModelScope.launch {
            val result = libraryRepository.add(item)
            mutableUiState.update { state ->
                val nextMemberships = if (result is AppResult.Success) {
                    state.libraryMemberships + ref
                } else {
                    state.libraryMemberships
                }
                state.copy(
                    addingToWatchlist = state.addingToWatchlist - ref,
                    libraryMemberships = nextMemberships,
                    // The + becomes available again, so say why nothing was saved.
                    snackbar = (result as? AppResult.Failure)?.let { HomeSnackbar.Failed(it.error) } ?: state.snackbar
                )
            }
        }
    }

    fun markNextEpisodeWatched(item: ContinueWatchingItem) {
        val episodeRef = item.nextEpisodeRef ?: return
        val episode = item.nextEpisode ?: return
        if (item.mediaRef in mutableUiState.value.markingEpisodes) return
        mutableUiState.update { it.copy(markingEpisodes = it.markingEpisodes + item.mediaRef) }
        viewModelScope.launch {
            val feedback = when (val result = watchProgressRepository.markEpisodeWatched(episodeRef)) {
                is AppResult.Success -> HomeSnackbar.EpisodeMarked(item.title, episode, episodeRef)
                is AppResult.Failure -> HomeSnackbar.Failed(result.error)
            }
            mutableUiState.update {
                it.copy(markingEpisodes = it.markingEpisodes - item.mediaRef, snackbar = feedback)
            }
        }
    }

    fun undoMarkedEpisode() {
        val marked = mutableUiState.value.snackbar as? HomeSnackbar.EpisodeMarked ?: return
        mutableUiState.update { it.copy(snackbar = null) }
        viewModelScope.launch {
            val result = watchProgressRepository.markEpisodeUnwatched(marked.episodeRef)
            if (result is AppResult.Failure) {
                mutableUiState.update { it.copy(snackbar = HomeSnackbar.Failed(result.error)) }
            }
        }
    }

    fun dismissSnackbar() {
        mutableUiState.update { it.copy(snackbar = null) }
    }

    fun retryLocal() {
        localRetry.update { it + 1 }
        replaceRefreshOperation { generation ->
            coroutineScope {
                launch { backfillLocalCalendar(generation) }
                launch { loadFeaturedReleases(generation) }
            }
        }
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

    private suspend fun backfillLocalCalendar(generation: Long) {
        val result = calendarRepository.backfill()
        if (generation == refreshGeneration &&
            result is AppResult.Failure &&
            mutableUiState.value.content !is HomeContentState.Events
        ) {
            mutableUiState.update { it.copy(content = HomeContentState.Error(result.error)) }
        }
    }

    private fun observeLibraryMemberships() {
        viewModelScope.launch {
            libraryRepository.observeMembershipRefs().collect { result ->
                if (result is AppResult.Success) {
                    mutableUiState.update { it.copy(libraryMemberships = result.value) }
                }
            }
        }
    }

    private fun observeContinueWatching() {
        viewModelScope.launch {
            libraryRepository.observeContinueWatching().collect { result ->
                if (result is AppResult.Success) {
                    mutableUiState.update { it.copy(continueWatching = result.value) }
                }
            }
        }
    }

    private suspend fun loadFeaturedReleases(generation: Long) {
        when (val result = featuredRepository.getFeaturedReleases()) {
            is AppResult.Success -> if (generation == refreshGeneration) {
                mutableUiState.update {
                    it.copy(
                        featuredMovies = result.value.movies,
                        featuredSeries = result.value.series
                    )
                }
            }
            is AppResult.Failure -> {
                // Keep existing or empty
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
