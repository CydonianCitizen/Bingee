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
import com.cydoniancitizen.bingee.core.model.PersonalRating
import com.cydoniancitizen.bingee.core.model.SeriesProgress
import com.cydoniancitizen.bingee.core.model.TrackedEpisode
import com.cydoniancitizen.bingee.core.model.deriveSeriesProgress
import com.cydoniancitizen.bingee.core.navigation.DetailRoute
import com.cydoniancitizen.bingee.core.navigation.DetailRouteArgs
import com.cydoniancitizen.bingee.core.result.AppError
import com.cydoniancitizen.bingee.core.result.AppResult
import com.cydoniancitizen.bingee.domain.calendar.CalendarDateSource
import com.cydoniancitizen.bingee.domain.repository.LibraryRepository
import com.cydoniancitizen.bingee.domain.repository.MediaDetailsRepository
import com.cydoniancitizen.bingee.domain.repository.RatingRepository
import com.cydoniancitizen.bingee.domain.repository.SeriesRepository
import com.cydoniancitizen.bingee.domain.repository.WatchProgressRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
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

internal sealed interface DetailRatingState {
    data object Loading : DetailRatingState

    data class Ready(
        val rating: PersonalRating?,
        val selectedValue: Int = rating?.value ?: 5,
        val updating: Boolean = false,
        val error: AppError? = null
    ) : DetailRatingState

    data class Error(val error: AppError) : DetailRatingState
}

internal data class SeriesDetailUiState(
    val content: SeriesContentState = SeriesContentState.NotApplicable,
    val expandedSeasons: Set<ExternalMediaRef> = emptySet(),
    val seasonLoads: Map<ExternalMediaRef, SeasonLoadState> = emptyMap(),
    val pendingEpisodes: Set<ExternalMediaRef> = emptySet(),
    val pendingSeasons: Set<ExternalMediaRef> = emptySet()
)

internal data class MediaDetailsUiState(
    val today: LocalDate,
    val content: DetailContentState = DetailContentState.Resolving,
    val refresh: DetailRefreshState = DetailRefreshState.Idle,
    val isInLibrary: Boolean? = null,
    val isFavorite: Boolean = false,
    val isAbandoned: Boolean = false,
    val watchedDate: java.time.LocalDate? = null,
    val favoriteUpdating: Boolean = false,
    val watchedDateUpdating: Boolean = false,
    val libraryAction: DetailLibraryActionState = DetailLibraryActionState.IDLE,
    val libraryError: AppError? = null,
    val movieProgress: MovieProgressState = MovieProgressState.NotApplicable,
    val series: SeriesDetailUiState = SeriesDetailUiState(),
    val progressError: AppError? = null,
    val rating: DetailRatingState = DetailRatingState.Loading
)

@HiltViewModel
internal class MediaDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val detailsRepository: MediaDetailsRepository,
    private val libraryRepository: LibraryRepository,
    private val seriesRepository: SeriesRepository,
    private val progressRepository: WatchProgressRepository,
    private val ratingRepository: RatingRepository,
    private val dateSource: CalendarDateSource
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(MediaDetailsUiState(today = dateSource.currentDate()))
    val uiState: StateFlow<MediaDetailsUiState> = mutableUiState.asStateFlow()

    private val routeArgs: DetailRouteArgs? = DetailRoute.parse(
        savedStateHandle[DetailRoute.MEDIA_TYPE_ARG],
        savedStateHandle[DetailRoute.TMDB_ID_ARG]
    )
    private val routeReference = routeArgs?.let { ExternalMediaRef(MediaSource.TMDB, it.tmdbId.toString()) }
    private var automaticRefreshStarted = false
    private var seasonSummaryBootstrapStarted = false
    private var refreshJob: Job? = null
    private val seasonRefreshJobs = mutableMapOf<ExternalMediaRef, Job>()

    init {
        viewModelScope.launch {
            dateSource.observeDate().collect { today ->
                mutableUiState.update { it.copy(today = today) }
            }
        }
        val args = routeArgs
        when {
            args == null -> mutableUiState.update {
                it.copy(content = DetailContentState.Error(AppError.InvalidInput))
            }
            else -> {
                observeDetails(args)
                observeMembership(args)
                observeRating(requireNotNull(routeReference))
                if (args.mediaType == MediaType.MOVIE) {
                    mutableUiState.update { it.copy(movieProgress = MovieProgressState.Loading) }
                    observeMovieProgress(requireNotNull(routeReference))
                } else {
                    mutableUiState.update {
                        it.copy(series = it.series.copy(content = SeriesContentState.Loading))
                    }
                    observeSeries(args.tmdbId)
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
                libraryRepository.remove(requireNotNull(routeReference))
            } else {
                when (val added = libraryRepository.add(requireNotNull(routeReference))) {
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
                progressRepository.markMovieUnwatched(requireNotNull(routeReference))
            } else {
                progressRepository.markMovieWatched(requireNotNull(routeReference))
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

    fun selectRating(value: Int) {
        if (value !in PersonalRating.MIN_VALUE..PersonalRating.MAX_VALUE) {
            mutableUiState.update { state ->
                val current = state.rating as? DetailRatingState.Ready ?: return@update state
                state.copy(rating = current.copy(error = AppError.InvalidInput))
            }
            return
        }
        mutableUiState.update { state ->
            val current = state.rating as? DetailRatingState.Ready ?: return@update state
            state.copy(rating = current.copy(selectedValue = value, error = null))
        }
    }

    fun setRating() = updateRating { _, current ->
        ratingRepository.setRating(requireNotNull(routeReference), PersonalRating(current.selectedValue))
    }

    fun removeRating() = updateRating { _, _ -> ratingRepository.removeRating(requireNotNull(routeReference)) }

    fun dismissRatingError() {
        mutableUiState.update { state ->
            val current = state.rating as? DetailRatingState.Ready ?: return@update state
            state.copy(rating = current.copy(error = null))
        }
    }

    private fun observeDetails(args: DetailRouteArgs) {
        viewModelScope.launch {
            detailsRepository.observeDetails(args.tmdbId).collectLatest { result ->
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
            libraryRepository.observeEntry(requireNotNull(routeReference)).collectLatest { result ->
                mutableUiState.update {
                    when (result) {
                        is AppResult.Success -> it.copy(
                            // A cached media row is not membership: opening Details stores the
                            // fetched title locally, and favorites live outside the Library too, so
                            // only the entry's own flag decides whether the action reads Add or Remove.
                            isInLibrary = result.value?.inLibrary == true,
                            isFavorite = result.value?.isFavorite ?: false,
                            isAbandoned = result.value?.isAbandoned == true,
                            watchedDate = result.value?.watchedDate
                        )
                        is AppResult.Failure -> it.copy(libraryError = result.error)
                    }
                }
            }
        }
    }

    fun toggleFavorite() {
        val args = routeArgs ?: return
        val state = mutableUiState.value
        if (state.favoriteUpdating) return
        mutableUiState.update { it.copy(favoriteUpdating = true) }
        viewModelScope.launch {
            val result = libraryRepository.setFavorite(requireNotNull(routeReference), !state.isFavorite)
            mutableUiState.update {
                it.copy(
                    favoriteUpdating = false,
                    libraryError = (result as? AppResult.Failure)?.error
                )
            }
        }
    }

    fun setWatchedDate(date: java.time.LocalDate?) {
        val args = routeArgs ?: return
        if (mutableUiState.value.watchedDateUpdating) return
        mutableUiState.update { it.copy(watchedDateUpdating = true) }
        viewModelScope.launch {
            val result = libraryRepository.setWatchedDate(requireNotNull(routeReference), date)
            mutableUiState.update {
                it.copy(
                    watchedDateUpdating = false,
                    progressError = (result as? AppResult.Failure)?.error
                )
            }
        }
    }

    fun toggleSeriesAbandoned() {
        val args = routeArgs ?: return
        if (args.mediaType != MediaType.SERIES) return
        val state = mutableUiState.value
        if (state.isInLibrary != true || state.libraryAction == DetailLibraryActionState.UPDATING) return
        mutableUiState.update { it.copy(libraryAction = DetailLibraryActionState.UPDATING, libraryError = null) }
        viewModelScope.launch {
            val result = libraryRepository.setSeriesAbandoned(requireNotNull(routeReference), !state.isAbandoned)
            mutableUiState.update {
                when (result) {
                    is AppResult.Success -> it.copy(
                        isAbandoned = !state.isAbandoned,
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

    private fun observeRating(reference: ExternalMediaRef) {
        viewModelScope.launch {
            ratingRepository.observeRating(reference).collectLatest { result ->
                mutableUiState.update { state ->
                    when (result) {
                        is AppResult.Success -> {
                            val previous = state.rating as? DetailRatingState.Ready
                            state.copy(
                                rating = DetailRatingState.Ready(
                                    rating = result.value,
                                    selectedValue = result.value?.value ?: previous?.selectedValue ?: 5,
                                    updating = previous?.updating == true
                                )
                            )
                        }
                        is AppResult.Failure -> state.copy(rating = DetailRatingState.Error(result.error))
                    }
                }
            }
        }
    }

    private fun updateRating(operation: suspend (DetailRouteArgs, DetailRatingState.Ready) -> AppResult<Unit>) {
        val args = routeArgs ?: return
        val current = mutableUiState.value.rating as? DetailRatingState.Ready ?: return
        if (current.updating) return
        mutableUiState.update { it.copy(rating = current.copy(updating = true, error = null)) }
        viewModelScope.launch {
            val result = operation(args, current)
            mutableUiState.update { state ->
                val observed = state.rating as? DetailRatingState.Ready ?: return@update state
                state.copy(
                    rating = observed.copy(
                        updating = false,
                        error = (result as? AppResult.Failure)?.error
                    )
                )
            }
        }
    }

    private fun observeSeries(tmdbId: Long) {
        viewModelScope.launch {
            seriesRepository.observeSeasons(tmdbId).collectLatest { result ->
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
        if (refreshJob?.isActive == true) return
        mutableUiState.update { state ->
            if (state.content is DetailContentState.Content) {
                state.copy(refresh = DetailRefreshState.Refreshing)
            } else {
                state.copy(content = DetailContentState.Loading, refresh = DetailRefreshState.Refreshing)
            }
        }
        refreshJob = viewModelScope.launch {
            when (val result = detailsRepository.refreshDetails(args.tmdbId, args.mediaType, force)) {
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
            val result = seriesRepository.refreshSeason(args.tmdbId, season.season.seasonNumber, force)
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
