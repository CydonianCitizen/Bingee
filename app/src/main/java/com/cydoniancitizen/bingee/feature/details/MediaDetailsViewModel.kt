package com.cydoniancitizen.bingee.feature.details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cydoniancitizen.bingee.core.model.CacheFreshness
import com.cydoniancitizen.bingee.core.model.CachedMediaDetails
import com.cydoniancitizen.bingee.core.model.CachedSeason
import com.cydoniancitizen.bingee.core.model.EpisodeWatchState
import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.core.model.MovieWatchState
import com.cydoniancitizen.bingee.core.model.SeriesProgress
import com.cydoniancitizen.bingee.core.model.TrackedEpisode
import com.cydoniancitizen.bingee.core.model.deriveSeriesProgress
import com.cydoniancitizen.bingee.core.navigation.DetailRoute
import com.cydoniancitizen.bingee.core.navigation.DetailRouteArgs
import com.cydoniancitizen.bingee.core.result.AppError
import com.cydoniancitizen.bingee.core.result.AppResult
import com.cydoniancitizen.bingee.domain.repository.LibraryRepository
import com.cydoniancitizen.bingee.domain.repository.MediaDetailsRepository
import com.cydoniancitizen.bingee.domain.repository.SeriesRepository
import com.cydoniancitizen.bingee.domain.repository.WatchProgressRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal sealed interface DetailContentState {
    data object Resolving : DetailContentState
    data object Loading : DetailContentState
    data class Content(val cached: CachedMediaDetails) : DetailContentState
    data class Error(val error: AppError) : DetailContentState
}

internal sealed interface DetailRefreshState {
    data object Idle : DetailRefreshState
    data object Refreshing : DetailRefreshState
    data class Error(val error: AppError) : DetailRefreshState
}

internal enum class DetailLibraryActionState {
    IDLE,
    UPDATING
}

internal sealed interface MovieProgressState {
    data object NotApplicable : MovieProgressState
    data object Loading : MovieProgressState
    data class Ready(val state: MovieWatchState, val updating: Boolean = false) : MovieProgressState
    data class Error(val error: AppError) : MovieProgressState
}

internal sealed interface SeriesContentState {
    data object NotApplicable : SeriesContentState
    data object Loading : SeriesContentState
    data class Ready(val seasons: List<CachedSeason>, val progress: SeriesProgress) : SeriesContentState
    data class Error(val error: AppError) : SeriesContentState
}

internal sealed interface SeasonLoadState {
    data object Idle : SeasonLoadState
    data object Loading : SeasonLoadState
    data object Refreshing : SeasonLoadState
    data class Error(val error: AppError) : SeasonLoadState
}

internal data class SeriesDetailUiState(
    val content: SeriesContentState = SeriesContentState.NotApplicable,
    val expandedSeasons: Set<ExternalMediaRef> = emptySet(),
    val seasonLoads: Map<ExternalMediaRef, SeasonLoadState> = emptyMap(),
    val pendingEpisodes: Set<ExternalMediaRef> = emptySet(),
    val pendingSeasons: Set<ExternalMediaRef> = emptySet()
)

internal data class MediaDetailsUiState(
    val content: DetailContentState = DetailContentState.Resolving,
    val refresh: DetailRefreshState = DetailRefreshState.Idle,
    val isInLibrary: Boolean? = null,
    val libraryAction: DetailLibraryActionState = DetailLibraryActionState.IDLE,
    val libraryError: AppError? = null,
    val movieProgress: MovieProgressState = MovieProgressState.NotApplicable,
    val series: SeriesDetailUiState = SeriesDetailUiState(),
    val progressError: AppError? = null
)

@HiltViewModel
internal class MediaDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val detailsRepository: MediaDetailsRepository,
    private val libraryRepository: LibraryRepository,
    private val seriesRepository: SeriesRepository,
    private val progressRepository: WatchProgressRepository
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(MediaDetailsUiState())
    val uiState: StateFlow<MediaDetailsUiState> = mutableUiState.asStateFlow()

    private val routeArgs: DetailRouteArgs? = DetailRoute.parse(
        savedStateHandle[DetailRoute.SOURCE_ARG],
        savedStateHandle[DetailRoute.MEDIA_TYPE_ARG],
        savedStateHandle[DetailRoute.EXTERNAL_ID_ARG]
    )
    private var automaticRefreshStarted = false
    private var seasonSummaryBootstrapStarted = false
    private var refreshJob: Job? = null
    private val seasonRefreshJobs = mutableMapOf<ExternalMediaRef, Job>()

    init {
        val args = routeArgs
        when {
            args == null -> mutableUiState.update {
                it.copy(content = DetailContentState.Error(AppError.InvalidInput))
            }
            args.reference.source != MediaSource.TMDB -> mutableUiState.update {
                it.copy(content = DetailContentState.Error(AppError.UnsupportedData))
            }
            else -> {
                observeDetails(args)
                observeMembership(args)
                if (args.mediaType == MediaType.MOVIE) {
                    mutableUiState.update { it.copy(movieProgress = MovieProgressState.Loading) }
                    observeMovieProgress(args.reference)
                } else {
                    mutableUiState.update {
                        it.copy(series = it.series.copy(content = SeriesContentState.Loading))
                    }
                    observeSeries(args.reference)
                }
            }
        }
    }

    fun refresh() = startRefresh(force = true)

    fun retry() = startRefresh(force = true)

    fun toggleLibrary() {
        val args = routeArgs ?: return
        val state = mutableUiState.value
        if (state.libraryAction == DetailLibraryActionState.UPDATING || state.isInLibrary == null) return
        if (state.content !is DetailContentState.Content) return
        mutableUiState.update {
            it.copy(libraryAction = DetailLibraryActionState.UPDATING, libraryError = null)
        }
        viewModelScope.launch {
            val result = if (state.isInLibrary) {
                libraryRepository.remove(args.reference)
            } else {
                when (val added = libraryRepository.add(args.reference)) {
                    is AppResult.Success -> AppResult.Success(Unit)
                    is AppResult.Failure -> added
                }
            }
            mutableUiState.update {
                when (result) {
                    is AppResult.Success -> it.copy(
                        isInLibrary = !state.isInLibrary,
                        libraryAction = DetailLibraryActionState.IDLE
                    )
                    is AppResult.Failure -> it.copy(
                        libraryAction = DetailLibraryActionState.IDLE,
                        libraryError = result.error
                    )
                }
            }
        }
    }

    fun toggleMovieWatched() {
        val args = routeArgs ?: return
        val current = mutableUiState.value.movieProgress as? MovieProgressState.Ready ?: return
        if (current.updating) return
        mutableUiState.update {
            it.copy(movieProgress = current.copy(updating = true), progressError = null)
        }
        viewModelScope.launch {
            val result = if (current.state is MovieWatchState.Watched) {
                progressRepository.markMovieUnwatched(args.reference)
            } else {
                progressRepository.markMovieWatched(args.reference)
            }
            mutableUiState.update { state ->
                val observed = state.movieProgress as? MovieProgressState.Ready
                state.copy(
                    movieProgress = observed?.copy(updating = false) ?: state.movieProgress,
                    progressError = (result as? AppResult.Failure)?.error
                )
            }
        }
    }

    fun toggleSeasonExpanded(season: CachedSeason) {
        val ref = season.season.externalRef
        val expanded = ref in mutableUiState.value.series.expandedSeasons
        mutableUiState.update {
            it.copy(
                series = it.series.copy(
                    expandedSeasons = if (expanded) {
                        it.series.expandedSeasons - ref
                    } else {
                        it.series.expandedSeasons + ref
                    }
                )
            )
        }
        if (!expanded) startSeasonRefresh(season, force = false)
    }

    fun retrySeason(season: CachedSeason) = startSeasonRefresh(season, force = true)

    fun toggleEpisode(episode: TrackedEpisode) {
        val ref = episode.episode.externalRef
        if (episode.watchState == EpisodeWatchState.Unavailable) return
        val seriesState = mutableUiState.value.series
        if (ref in seriesState.pendingEpisodes) return
        mutableUiState.update {
            it.copy(
                series = it.series.copy(pendingEpisodes = it.series.pendingEpisodes + ref),
                progressError = null
            )
        }
        viewModelScope.launch {
            val result = if (episode.watchState is EpisodeWatchState.Watched) {
                progressRepository.markEpisodeUnwatched(ref)
            } else {
                progressRepository.markEpisodeWatched(ref)
            }
            mutableUiState.update {
                it.copy(
                    series = it.series.copy(pendingEpisodes = it.series.pendingEpisodes - ref),
                    progressError = (result as? AppResult.Failure)?.error
                )
            }
        }
    }

    fun toggleSeasonWatched(season: CachedSeason) {
        val ref = season.season.externalRef
        if (ref in mutableUiState.value.series.pendingSeasons) return
        mutableUiState.update {
            it.copy(
                series = it.series.copy(pendingSeasons = it.series.pendingSeasons + ref),
                progressError = null
            )
        }
        viewModelScope.launch {
            val result = if (season.progress.isComplete) {
                progressRepository.markSeasonUnwatched(ref)
            } else {
                progressRepository.markSeasonWatched(ref)
            }
            mutableUiState.update {
                it.copy(
                    series = it.series.copy(pendingSeasons = it.series.pendingSeasons - ref),
                    progressError = (result as? AppResult.Failure)?.error
                )
            }
        }
    }

    fun dismissLibraryError() {
        mutableUiState.update { it.copy(libraryError = null) }
    }

    fun dismissProgressError() {
        mutableUiState.update { it.copy(progressError = null) }
    }

    private fun observeDetails(args: DetailRouteArgs) {
        viewModelScope.launch {
            detailsRepository.observeDetails(args.reference).collectLatest { result ->
                when (result) {
                    is AppResult.Failure -> mutableUiState.update { state ->
                        if (state.content is DetailContentState.Content) {
                            state.copy(refresh = DetailRefreshState.Error(result.error))
                        } else {
                            state.copy(content = DetailContentState.Error(result.error))
                        }
                    }
                    is AppResult.Success -> {
                        val cached = result.value
                        if (cached == null) {
                            mutableUiState.update { state ->
                                if (state.content is DetailContentState.Content) {
                                    state
                                } else {
                                    state.copy(content = DetailContentState.Loading)
                                }
                            }
                            startAutomaticRefresh()
                        } else if (cached.details.mediaType != args.mediaType) {
                            mutableUiState.update {
                                it.copy(content = DetailContentState.Error(AppError.CorruptedData))
                            }
                        } else {
                            mutableUiState.update { it.copy(content = DetailContentState.Content(cached)) }
                            if (cached.freshness == CacheFreshness.STALE) startAutomaticRefresh()
                        }
                    }
                }
            }
        }
    }

    private fun observeMembership(args: DetailRouteArgs) {
        viewModelScope.launch {
            libraryRepository.observeEntry(args.reference).collectLatest { result ->
                mutableUiState.update {
                    when (result) {
                        is AppResult.Success -> it.copy(isInLibrary = result.value != null)
                        is AppResult.Failure -> it.copy(libraryError = result.error)
                    }
                }
            }
        }
    }

    private fun observeMovieProgress(reference: ExternalMediaRef) {
        viewModelScope.launch {
            progressRepository.observeMovie(reference).collectLatest { result ->
                mutableUiState.update {
                    when (result) {
                        is AppResult.Success -> it.copy(
                            movieProgress = MovieProgressState.Ready(
                                state = result.value,
                                updating = (it.movieProgress as? MovieProgressState.Ready)?.updating == true
                            )
                        )
                        is AppResult.Failure -> it.copy(movieProgress = MovieProgressState.Error(result.error))
                    }
                }
            }
        }
    }

    private fun observeSeries(reference: ExternalMediaRef) {
        viewModelScope.launch {
            seriesRepository.observeSeasons(reference).collectLatest { result ->
                mutableUiState.update {
                    when (result) {
                        is AppResult.Success -> it.copy(
                            series = it.series.copy(
                                content = SeriesContentState.Ready(
                                    seasons = result.value,
                                    progress = deriveSeriesProgress(result.value)
                                )
                            )
                        )
                        is AppResult.Failure -> it.copy(
                            series = it.series.copy(content = SeriesContentState.Error(result.error))
                        )
                    }
                }
                if (result is AppResult.Success && result.value.isEmpty()) {
                    startSeasonSummaryBootstrap()
                }
            }
        }
    }

    private fun startAutomaticRefresh() {
        if (automaticRefreshStarted) return
        automaticRefreshStarted = true
        startRefresh(force = false)
    }

    private fun startSeasonSummaryBootstrap() {
        if (seasonSummaryBootstrapStarted) return
        seasonSummaryBootstrapStarted = true
        startRefresh(force = true)
    }

    private fun startRefresh(force: Boolean) {
        val args = routeArgs ?: return
        if (args.reference.source != MediaSource.TMDB || refreshJob?.isActive == true) return
        mutableUiState.update { state ->
            if (state.content is DetailContentState.Content) {
                state.copy(refresh = DetailRefreshState.Refreshing)
            } else {
                state.copy(content = DetailContentState.Loading, refresh = DetailRefreshState.Refreshing)
            }
        }
        refreshJob = viewModelScope.launch {
            when (val result = detailsRepository.refreshDetails(args.reference, args.mediaType, force)) {
                is AppResult.Success -> mutableUiState.update { it.copy(refresh = DetailRefreshState.Idle) }
                is AppResult.Failure -> mutableUiState.update { state ->
                    if (state.content is DetailContentState.Content) {
                        state.copy(refresh = DetailRefreshState.Error(result.error))
                    } else {
                        state.copy(
                            content = DetailContentState.Error(result.error),
                            refresh = DetailRefreshState.Idle
                        )
                    }
                }
            }
        }
    }

    private fun startSeasonRefresh(season: CachedSeason, force: Boolean) {
        val args = routeArgs ?: return
        val ref = season.season.externalRef
        if (seasonRefreshJobs[ref]?.isActive == true) return
        val loading = if (season.episodesFetchedAt == null) SeasonLoadState.Loading else SeasonLoadState.Refreshing
        mutableUiState.update {
            it.copy(series = it.series.copy(seasonLoads = it.series.seasonLoads + (ref to loading)))
        }
        seasonRefreshJobs[ref] = viewModelScope.launch {
            val result = seriesRepository.refreshSeason(args.reference, season.season.seasonNumber, force)
            mutableUiState.update {
                it.copy(
                    series = it.series.copy(
                        seasonLoads = it.series.seasonLoads + (
                            ref to when (result) {
                                is AppResult.Success -> SeasonLoadState.Idle
                                is AppResult.Failure -> SeasonLoadState.Error(result.error)
                            }
                            )
                    )
                )
            }
        }
    }
}
