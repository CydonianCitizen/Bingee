package com.cydoniancitizen.bingee.feature.details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cydoniancitizen.bingee.core.model.CachedMediaDetails
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.navigation.DetailRoute
import com.cydoniancitizen.bingee.core.navigation.DetailRouteArgs
import com.cydoniancitizen.bingee.core.result.AppError
import com.cydoniancitizen.bingee.core.result.AppResult
import com.cydoniancitizen.bingee.domain.repository.LibraryRepository
import com.cydoniancitizen.bingee.domain.repository.MediaDetailsRepository
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

internal data class MediaDetailsUiState(
    val content: DetailContentState = DetailContentState.Resolving,
    val refresh: DetailRefreshState = DetailRefreshState.Idle,
    val isInLibrary: Boolean? = null,
    val libraryAction: DetailLibraryActionState = DetailLibraryActionState.IDLE,
    val libraryError: AppError? = null
)

@HiltViewModel
internal class MediaDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val detailsRepository: MediaDetailsRepository,
    private val libraryRepository: LibraryRepository
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(MediaDetailsUiState())
    val uiState: StateFlow<MediaDetailsUiState> = mutableUiState.asStateFlow()

    private val routeArgs: DetailRouteArgs? = DetailRoute.parse(
        savedStateHandle[DetailRoute.SOURCE_ARG],
        savedStateHandle[DetailRoute.MEDIA_TYPE_ARG],
        savedStateHandle[DetailRoute.EXTERNAL_ID_ARG]
    )
    private var automaticRefreshStarted = false
    private var refreshJob: Job? = null

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
                libraryRepository.add(args.reference).let { addResult ->
                    when (addResult) {
                        is AppResult.Success -> AppResult.Success(Unit)
                        is AppResult.Failure -> addResult
                    }
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

    fun dismissLibraryError() {
        mutableUiState.update { it.copy(libraryError = null) }
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
                            if (cached.freshness == com.cydoniancitizen.bingee.core.model.CacheFreshness.STALE) {
                                startAutomaticRefresh()
                            }
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

    private fun startAutomaticRefresh() {
        if (automaticRefreshStarted) return
        automaticRefreshStarted = true
        startRefresh(force = false)
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
}
