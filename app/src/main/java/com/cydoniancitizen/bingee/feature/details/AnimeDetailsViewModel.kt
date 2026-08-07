package com.cydoniancitizen.bingee.feature.details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cydoniancitizen.bingee.core.common.AnimeFeatureAvailability
import com.cydoniancitizen.bingee.core.model.AnimeWatchProgress
import com.cydoniancitizen.bingee.core.model.CacheFreshness
import com.cydoniancitizen.bingee.core.model.CachedAnimeDetails
import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.LinkedMediaIdentity
import com.cydoniancitizen.bingee.core.model.MediaLinkAuditOrigin
import com.cydoniancitizen.bingee.core.model.MediaLinkGroup
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.core.model.PersonalRating
import com.cydoniancitizen.bingee.core.navigation.DetailRoute
import com.cydoniancitizen.bingee.core.navigation.DetailRouteArgs
import com.cydoniancitizen.bingee.core.result.AppError
import com.cydoniancitizen.bingee.core.result.AppResult
import com.cydoniancitizen.bingee.domain.equivalence.MediaEquivalenceCandidate
import com.cydoniancitizen.bingee.domain.repository.AnimeDetailsRepository
import com.cydoniancitizen.bingee.domain.repository.AnimeProgressRepository
import com.cydoniancitizen.bingee.domain.repository.LibraryRepository
import com.cydoniancitizen.bingee.domain.repository.MediaEquivalenceCandidateRepository
import com.cydoniancitizen.bingee.domain.repository.MediaLinkRepository
import com.cydoniancitizen.bingee.domain.repository.RatingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal sealed interface AnimeDetailContentState {
    data object Loading : AnimeDetailContentState
    data class Content(val cached: CachedAnimeDetails) : AnimeDetailContentState
    data class Error(val error: AppError) : AnimeDetailContentState
}

internal data class AnimeDetailsUiState(
    val content: AnimeDetailContentState = AnimeDetailContentState.Loading,
    val refreshing: Boolean = false,
    val refreshError: AppError? = null,
    val isInLibrary: Boolean? = null,
    val isFavorite: Boolean = false,
    val watchedDate: java.time.LocalDate? = null,
    val favoriteUpdating: Boolean = false,
    val watchedDateUpdating: Boolean = false,
    val libraryUpdating: Boolean = false,
    val libraryError: AppError? = null,
    val movieProgress: MovieProgressState = MovieProgressState.NotApplicable,
    val isSeriesWatched: Boolean = false,
    val progress: AnimeWatchProgress? = null,
    val progressUpdating: Boolean = false,
    val progressError: AppError? = null,
    val rating: DetailRatingState = DetailRatingState.Loading,
    val candidate: MediaEquivalenceCandidate? = null,
    val linkGroup: MediaLinkGroup? = null
)

@HiltViewModel
internal class AnimeDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val detailsRepository: AnimeDetailsRepository,
    private val progressRepository: AnimeProgressRepository,
    private val libraryRepository: LibraryRepository,
    private val ratingRepository: RatingRepository,
    private val candidateRepository: MediaEquivalenceCandidateRepository,
    private val linkRepository: MediaLinkRepository,
    private val watchProgressRepository: com.cydoniancitizen.bingee.domain.repository.WatchProgressRepository,
    private val availability: AnimeFeatureAvailability
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(AnimeDetailsUiState())
    val uiState: StateFlow<AnimeDetailsUiState> = mutableUiState.asStateFlow()
    private val routeArgs: DetailRouteArgs? = DetailRoute.parse(
        savedStateHandle[DetailRoute.SOURCE_ARG],
        savedStateHandle[DetailRoute.MEDIA_TYPE_ARG],
        savedStateHandle[DetailRoute.EXTERNAL_ID_ARG]
    )?.takeIf { it.reference.source == MediaSource.JIKAN }
    private val reference: ExternalMediaRef? = routeArgs?.reference
    private var automaticRefreshStarted = false
    private var refreshJob: Job? = null

    init {
        val ref = reference
        if (!availability.isAvailable) {
            mutableUiState.update { it.copy(content = AnimeDetailContentState.Error(AppError.FeatureUnavailable)) }
        } else if (ref == null) {
            mutableUiState.update { it.copy(content = AnimeDetailContentState.Error(AppError.InvalidInput)) }
        } else {
            observeDetails(ref)
            observeMembership(ref)
            observeProgress(ref)
            observeRating(ref)
            observeEquivalence(ref)
            if (routeArgs?.mediaType == MediaType.MOVIE) {
                mutableUiState.update { it.copy(movieProgress = MovieProgressState.Loading) }
                observeMovieProgress(ref)
            }
        }
    }

    fun refresh() = startRefresh(force = true)
    fun retry() = startRefresh(force = true)
    fun increment() = progressAction { progressRepository.increment(it) }
    fun decrement() = progressAction { progressRepository.decrement(it) }
    fun setCount(count: Int) = progressAction { progressRepository.setCount(it, count) }
    fun markComplete() = progressAction { progressRepository.markComplete(it) }
    fun markIncomplete() = progressAction { progressRepository.markIncomplete(it) }

    fun toggleLibrary() {
        val ref = reference ?: return
        val current = mutableUiState.value
        if (current.libraryUpdating || current.isInLibrary == null ||
            current.content !is AnimeDetailContentState.Content
        ) {
            return
        }
        mutableUiState.update { it.copy(libraryUpdating = true, libraryError = null) }
        viewModelScope.launch {
            val result = if (current.isInLibrary) {
                libraryRepository.remove(ref)
            } else {
                when (val add = libraryRepository.add(ref)) {
                    is AppResult.Success -> AppResult.Success(Unit)
                    is AppResult.Failure -> add
                }
            }
            mutableUiState.update {
                when (result) {
                    is AppResult.Success -> it.copy(
                        isInLibrary = !current.isInLibrary,
                        libraryUpdating = false
                    )
                    is AppResult.Failure -> it.copy(libraryUpdating = false, libraryError = result.error)
                }
            }
        }
    }

    fun toggleFavorite() {
        val ref = reference ?: return
        val state = mutableUiState.value
        if (state.favoriteUpdating) return
        mutableUiState.update { it.copy(favoriteUpdating = true) }
        viewModelScope.launch {
            val result = libraryRepository.setFavorite(ref, !state.isFavorite)
            mutableUiState.update {
                it.copy(
                    favoriteUpdating = false,
                    libraryError = (result as? AppResult.Failure)?.error
                )
            }
        }
    }

    fun setWatchedDate(date: java.time.LocalDate?) {
        val ref = reference ?: return
        if (mutableUiState.value.watchedDateUpdating) return
        mutableUiState.update { it.copy(watchedDateUpdating = true) }
        viewModelScope.launch {
            val result = libraryRepository.setWatchedDate(ref, date)
            mutableUiState.update {
                it.copy(
                    watchedDateUpdating = false,
                    watchedDate = if (result is AppResult.Success) date else it.watchedDate,
                    progressError = (result as? AppResult.Failure)?.error
                )
            }
        }
    }

    fun toggleMovieWatched() {
        val ref = reference ?: return
        val current = mutableUiState.value.movieProgress as? MovieProgressState.Ready ?: return
        if (current.updating) return
        mutableUiState.update {
            it.copy(movieProgress = current.copy(updating = true), progressError = null)
        }
        viewModelScope.launch {
            val result = if (current.state is com.cydoniancitizen.bingee.core.model.MovieWatchState.Watched) {
                watchProgressRepository.markMovieUnwatched(ref)
            } else {
                watchProgressRepository.markMovieWatched(ref)
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

    fun toggleSeriesWatched() {
        val ref = reference ?: return
        val state = mutableUiState.value
        if (state.progressUpdating) return
        mutableUiState.update { it.copy(progressUpdating = true, progressError = null) }
        viewModelScope.launch {
            val wasWatched = state.isSeriesWatched || state.watchedDate != null
            val result = if (wasWatched) {
                watchProgressRepository.markSeriesUnwatched(ref)
            } else {
                watchProgressRepository.markSeriesWatched(ref)
            }
            mutableUiState.update {
                it.copy(
                    progressUpdating = false,
                    isSeriesWatched = if (result is AppResult.Success) !wasWatched else state.isSeriesWatched,
                    progressError = (result as? AppResult.Failure)?.error
                )
            }
        }
    }

    fun dismissLibraryError() = mutableUiState.update { it.copy(libraryError = null) }
    fun dismissProgressError() = mutableUiState.update { it.copy(progressError = null) }

    fun selectRating(value: Int) {
        if (value !in PersonalRating.MIN_VALUE..PersonalRating.MAX_VALUE) return
        mutableUiState.update { state ->
            val ready = state.rating as? DetailRatingState.Ready ?: return@update state
            state.copy(rating = ready.copy(selectedValue = value, error = null))
        }
    }

    fun setRating() = updateRating { ref, ready ->
        ratingRepository.setRating(ref, PersonalRating(ready.selectedValue))
    }

    fun removeRating() = updateRating { ref, _ -> ratingRepository.removeRating(ref) }
    fun dismissRatingError() = mutableUiState.update { state ->
        val ready = state.rating as? DetailRatingState.Ready ?: return@update state
        state.copy(rating = ready.copy(error = null))
    }

    private fun observeDetails(ref: ExternalMediaRef) = viewModelScope.launch {
        detailsRepository.observeDetails(ref).collectLatest { result ->
            when (result) {
                is AppResult.Failure -> mutableUiState.update {
                    if (it.content is AnimeDetailContentState.Content) {
                        it.copy(refreshError = result.error, refreshing = false)
                    } else {
                        it.copy(content = AnimeDetailContentState.Error(result.error), refreshing = false)
                    }
                }
                is AppResult.Success -> {
                    val cached = result.value
                    if (cached == null) {
                        startAutomaticRefresh()
                    } else {
                        mutableUiState.update {
                            it.copy(content = AnimeDetailContentState.Content(cached), refreshing = false)
                        }
                        if (cached.freshness == CacheFreshness.STALE) startAutomaticRefresh()
                    }
                }
            }
        }
    }

    private fun observeMembership(ref: ExternalMediaRef) = viewModelScope.launch {
        libraryRepository.observeEntry(ref).collectLatest { result ->
            mutableUiState.update {
                when (result) {
                    is AppResult.Success -> {
                        val entry = result.value
                        val seriesWatched = entry?.watchedDate != null ||
                            (entry?.progress as? com.cydoniancitizen.bingee.core.model.LibraryProgress.Series)?.progress?.isComplete == true
                        it.copy(
                            isInLibrary = entry?.inLibrary == true,
                            isFavorite = entry?.isFavorite ?: false,
                            watchedDate = entry?.watchedDate,
                            isSeriesWatched = seriesWatched
                        )
                    }
                    is AppResult.Failure -> it.copy(libraryError = result.error)
                }
            }
        }
    }

    private fun observeMovieProgress(ref: ExternalMediaRef) = viewModelScope.launch {
        watchProgressRepository.observeMovie(ref).collectLatest { result ->
            mutableUiState.update { state ->
                when (result) {
                    is AppResult.Success -> state.copy(
                        movieProgress = MovieProgressState.Ready(
                            state = result.value,
                            updating = (state.movieProgress as? MovieProgressState.Ready)?.updating == true
                        )
                    )
                    is AppResult.Failure -> state.copy(movieProgress = MovieProgressState.Error(result.error))
                }
            }
        }
    }

    private fun observeProgress(ref: ExternalMediaRef) = viewModelScope.launch {
        progressRepository.observe(ref).collectLatest { result ->
            mutableUiState.update {
                when (result) {
                    is AppResult.Success -> it.copy(progress = result.value, progressUpdating = false)
                    is AppResult.Failure -> it.copy(progressError = result.error, progressUpdating = false)
                }
            }
        }
    }

    private fun observeRating(ref: ExternalMediaRef) = viewModelScope.launch {
        ratingRepository.observeRating(ref).collectLatest { result ->
            mutableUiState.update { state ->
                when (result) {
                    is AppResult.Success -> {
                        val old = state.rating as? DetailRatingState.Ready
                        state.copy(
                            rating = DetailRatingState.Ready(
                                rating = result.value,
                                selectedValue = result.value?.value ?: old?.selectedValue ?: 5,
                                updating = old?.updating == true
                            )
                        )
                    }
                    is AppResult.Failure -> state.copy(rating = DetailRatingState.Error(result.error))
                }
            }
        }
    }

    private fun startAutomaticRefresh() {
        if (automaticRefreshStarted) return
        automaticRefreshStarted = true
        startRefresh(force = false)
    }

    private fun startRefresh(force: Boolean) {
        val ref = reference ?: return
        if (refreshJob?.isActive == true) return
        mutableUiState.update { it.copy(refreshing = true, refreshError = null) }
        refreshJob = viewModelScope.launch {
            when (val result = detailsRepository.refreshDetails(ref, force)) {
                is AppResult.Success -> mutableUiState.update { it.copy(refreshing = false) }
                is AppResult.Failure -> mutableUiState.update {
                    if (it.content is AnimeDetailContentState.Content) {
                        it.copy(refreshing = false, refreshError = result.error)
                    } else {
                        it.copy(refreshing = false, content = AnimeDetailContentState.Error(result.error))
                    }
                }
            }
        }
    }

    private fun progressAction(action: suspend (ExternalMediaRef) -> AppResult<Unit>) {
        val ref = reference ?: return
        if (mutableUiState.value.progressUpdating) return
        mutableUiState.update { it.copy(progressUpdating = true, progressError = null) }
        viewModelScope.launch {
            val result = action(ref)
            if (result is AppResult.Failure) {
                mutableUiState.update { it.copy(progressUpdating = false, progressError = result.error) }
            }
        }
    }

    private fun updateRating(operation: suspend (ExternalMediaRef, DetailRatingState.Ready) -> AppResult<Unit>) {
        val ref = reference ?: return
        val current = mutableUiState.value.rating as? DetailRatingState.Ready ?: return
        if (current.updating) return
        mutableUiState.update { it.copy(rating = current.copy(updating = true, error = null)) }
        viewModelScope.launch {
            val result = operation(ref, current)
            mutableUiState.update { state ->
                val ready = state.rating as? DetailRatingState.Ready ?: return@update state
                state.copy(rating = ready.copy(updating = false, error = (result as? AppResult.Failure)?.error))
            }
        }
    }

    fun changePreferredPresentation(newPreferred: LinkedMediaIdentity) {
        val group = mutableUiState.value.linkGroup ?: return
        viewModelScope.launch {
            linkRepository.changePreferredPresentation(
                group.groupId,
                newPreferred,
                MediaLinkAuditOrigin.MANUAL_USER_ACTION
            )
        }
    }

    fun unlink() {
        val group = mutableUiState.value.linkGroup ?: return
        viewModelScope.launch {
            linkRepository.unlink(group.groupId, MediaLinkAuditOrigin.MANUAL_USER_ACTION)
        }
    }

    private fun observeEquivalence(ref: ExternalMediaRef) {
        val mediaType = routeArgs?.mediaType ?: MediaType.ANIME
        val identity =
            LinkedMediaIdentity(source = ref.source, mediaType = mediaType, externalId = ref.externalId)
        viewModelScope.launch {
            candidateRepository.observeCandidatesForMedia(identity).collectLatest { candidates ->
                mutableUiState.update { it.copy(candidate = candidates.firstOrNull()) }
            }
        }
        viewModelScope.launch {
            linkRepository.observeLinkForMedia(identity).collectLatest { result ->
                mutableUiState.update {
                    it.copy(linkGroup = result)
                }
            }
        }
    }
}
