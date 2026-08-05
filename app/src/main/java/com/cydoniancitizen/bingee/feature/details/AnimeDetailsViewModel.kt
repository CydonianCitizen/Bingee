package com.cydoniancitizen.bingee.feature.details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cydoniancitizen.bingee.core.model.AnimeWatchProgress
import com.cydoniancitizen.bingee.core.model.CacheFreshness
import com.cydoniancitizen.bingee.core.model.CachedAnimeDetails
import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.core.model.PersonalRating
import com.cydoniancitizen.bingee.core.navigation.DetailRoute
import com.cydoniancitizen.bingee.core.result.AppError
import com.cydoniancitizen.bingee.core.result.AppResult
import com.cydoniancitizen.bingee.domain.repository.AnimeDetailsRepository
import com.cydoniancitizen.bingee.domain.repository.AnimeProgressRepository
import com.cydoniancitizen.bingee.domain.repository.LibraryRepository
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
    val libraryUpdating: Boolean = false,
    val libraryError: AppError? = null,
    val progress: AnimeWatchProgress? = null,
    val progressUpdating: Boolean = false,
    val progressError: AppError? = null,
    val rating: DetailRatingState = DetailRatingState.Loading
)

@HiltViewModel
internal class AnimeDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val detailsRepository: AnimeDetailsRepository,
    private val progressRepository: AnimeProgressRepository,
    private val libraryRepository: LibraryRepository,
    private val ratingRepository: RatingRepository
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(AnimeDetailsUiState())
    val uiState: StateFlow<AnimeDetailsUiState> = mutableUiState.asStateFlow()
    private val reference: ExternalMediaRef? = DetailRoute.parse(
        savedStateHandle[DetailRoute.SOURCE_ARG],
        savedStateHandle[DetailRoute.MEDIA_TYPE_ARG],
        savedStateHandle[DetailRoute.EXTERNAL_ID_ARG]
    )?.takeIf { it.reference.source == MediaSource.JIKAN && it.mediaType == MediaType.ANIME }?.reference
    private var automaticRefreshStarted = false
    private var refreshJob: Job? = null

    init {
        val ref = reference
        if (ref == null) {
            mutableUiState.update { it.copy(content = AnimeDetailContentState.Error(AppError.InvalidInput)) }
        } else {
            observeDetails(ref)
            observeMembership(ref)
            observeProgress(ref)
            observeRating(ref)
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
                    is AppResult.Success -> it.copy(isInLibrary = result.value != null)
                    is AppResult.Failure -> it.copy(libraryError = result.error)
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
}
