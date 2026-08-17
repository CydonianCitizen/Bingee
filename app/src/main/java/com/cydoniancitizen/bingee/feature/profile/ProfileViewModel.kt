package com.cydoniancitizen.bingee.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.LibraryEntry
import com.cydoniancitizen.bingee.core.model.LibraryProgress
import com.cydoniancitizen.bingee.core.model.LibraryQuery
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.core.model.MovieWatchState
import com.cydoniancitizen.bingee.core.model.SeriesTrackingState
import com.cydoniancitizen.bingee.core.model.isWatched
import com.cydoniancitizen.bingee.core.result.AppError
import com.cydoniancitizen.bingee.core.result.AppResult
import com.cydoniancitizen.bingee.data.settings.ProfileCategory
import com.cydoniancitizen.bingee.data.settings.ProfileCollection
import com.cydoniancitizen.bingee.data.settings.ProfileDisplayModePreferences
import com.cydoniancitizen.bingee.data.settings.ProfileDisplayModes
import com.cydoniancitizen.bingee.data.settings.ProfileViewMode
import com.cydoniancitizen.bingee.domain.model.WatchedStatistics
import com.cydoniancitizen.bingee.domain.model.calculateWatchedStatistics
import com.cydoniancitizen.bingee.domain.repository.LibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ProfileSortOption {
    RECENTLY_ADDED,
    TITLE,
    RATING,
    PROGRESS
}

internal data class ProfileUiState(
    val collection: ProfileCollection = ProfileCollection.WATCHED,
    val category: ProfileCategory = ProfileCategory.MOVIES,
    val sortOption: ProfileSortOption = ProfileSortOption.RECENTLY_ADDED,
    val displayModes: ProfileDisplayModes = ProfileDisplayModes(),
    val entries: List<LibraryEntry> = emptyList(),
    val statistics: WatchedStatistics = WatchedStatistics(),
    val searchQuery: String = "",
    val isLoading: Boolean = true,
    val pendingRemovals: Set<ExternalMediaRef> = emptySet(),
    val actionError: AppError? = null,
    val loadError: AppError? = null,
    val statisticsError: AppError? = null
) {
    val currentViewMode: ProfileViewMode get() = displayModes.getMode(collection, category)
}

fun LibraryEntry.belongsToCategory(category: ProfileCategory): Boolean = when (category) {
    ProfileCategory.MOVIES -> mediaType == MediaType.MOVIE
    ProfileCategory.TV_SERIES -> mediaType == MediaType.SERIES
}

fun LibraryEntry.progressSortMetric(): Double = when (val p = progress) {
    is LibraryProgress.Series -> if (p.progress.trackableEpisodes > 0) {
        p.progress.watchedEpisodes.toDouble() / p.progress.trackableEpisodes
    } else {
        0.0
    }
    is LibraryProgress.Movie -> if (p.state is MovieWatchState.Watched) 1.0 else 0.0
    LibraryProgress.Unavailable -> 0.0
}

@HiltViewModel
internal class ProfileViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
    private val displayModePreferences: ProfileDisplayModePreferences
) : ViewModel() {

    private val mutableUiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = mutableUiState.asStateFlow()

    private val rawEntries = MutableStateFlow<List<LibraryEntry>>(emptyList())

    init {
        observeDisplayModes()
        observeLibraryEntries()
        observePersonalViewing()
    }

    fun setCollection(collection: ProfileCollection) {
        mutableUiState.update { it.copy(collection = collection) }
        refilter()
    }

    fun setCategory(category: ProfileCategory) {
        mutableUiState.update { it.copy(category = category) }
        refilter()
    }

    fun setSortOption(sortOption: ProfileSortOption) {
        mutableUiState.update { it.copy(sortOption = sortOption) }
        refilter()
    }

    fun setViewMode(mode: ProfileViewMode) {
        val current = mutableUiState.value
        viewModelScope.launch {
            displayModePreferences.setDisplayMode(current.collection, current.category, mode)
        }
    }

    fun onSearchQueryChanged(query: String) {
        mutableUiState.update { it.copy(searchQuery = query) }
        refilter()
    }

    fun clearSearch() {
        onSearchQueryChanged("")
    }

    fun toggleFavorite(entry: LibraryEntry) {
        val ref = entry.mediaRef
        viewModelScope.launch {
            when (val result = libraryRepository.setFavorite(ref, !entry.isFavorite)) {
                is AppResult.Success -> {}
                is AppResult.Failure -> {
                    mutableUiState.update { it.copy(actionError = result.error) }
                }
            }
        }
    }

    fun setWatchedDate(entry: LibraryEntry, watchedDate: LocalDate?) {
        val ref = entry.mediaRef
        viewModelScope.launch {
            when (val result = libraryRepository.setWatchedDate(ref, watchedDate)) {
                is AppResult.Success -> {}
                is AppResult.Failure -> {
                    mutableUiState.update { it.copy(actionError = result.error) }
                }
            }
        }
    }

    fun remove(entry: LibraryEntry) {
        val ref = entry.mediaRef
        if (ref in mutableUiState.value.pendingRemovals) return
        mutableUiState.update {
            it.copy(pendingRemovals = it.pendingRemovals + ref, actionError = null)
        }
        viewModelScope.launch {
            when (val result = libraryRepository.remove(ref)) {
                is AppResult.Success -> {
                    mutableUiState.update { state ->
                        state.copy(pendingRemovals = state.pendingRemovals - ref)
                    }
                }
                is AppResult.Failure -> {
                    mutableUiState.update { state ->
                        state.copy(
                            pendingRemovals = state.pendingRemovals - ref,
                            actionError = result.error
                        )
                    }
                }
            }
        }
    }

    fun clearActionError() {
        mutableUiState.update { it.copy(actionError = null) }
    }

    private fun observeDisplayModes() {
        viewModelScope.launch {
            displayModePreferences.observeDisplayModes().collectLatest { modes ->
                mutableUiState.update { it.copy(displayModes = modes) }
            }
        }
    }

    private fun observeLibraryEntries() {
        viewModelScope.launch {
            libraryRepository.observeEntries(LibraryQuery()).collectLatest { result ->
                when (result) {
                    is AppResult.Success -> {
                        rawEntries.value = result.value
                        mutableUiState.update {
                            it.copy(
                                isLoading = false,
                                loadError = null
                            )
                        }
                        refilter()
                    }
                    is AppResult.Failure -> {
                        mutableUiState.update { it.copy(isLoading = false, loadError = result.error) }
                    }
                }
            }
        }
    }

    private fun observePersonalViewing() {
        viewModelScope.launch {
            libraryRepository.observePersonalViewing().collectLatest { result ->
                when (result) {
                    is AppResult.Success -> mutableUiState.update {
                        it.copy(
                            statistics = calculateWatchedStatistics(result.value),
                            statisticsError = null
                        )
                    }
                    is AppResult.Failure -> mutableUiState.update { it.copy(statisticsError = result.error) }
                }
            }
        }
    }

    private fun refilter() {
        val state = mutableUiState.value
        val allRaw = rawEntries.value

        val filtered = allRaw.filter { entry ->
            val matchesCollection = when (state.collection) {
                ProfileCollection.WATCHED -> entry.inLibrary && entry.isWatched()
                ProfileCollection.WATCH_LATER -> when (entry.mediaType) {
                    MediaType.SERIES -> entry.serialState == SeriesTrackingState.WATCH_LATER
                    MediaType.MOVIE -> !entry.isWatched() && entry.inLibrary
                }
                ProfileCollection.FAVORITES -> entry.isFavorite
            }
            val matchesCategory = entry.belongsToCategory(state.category)
            val matchesSearch = if (state.searchQuery.isBlank()) {
                true
            } else {
                entry.title.contains(state.searchQuery, ignoreCase = true) ||
                    entry.originalTitle?.contains(state.searchQuery, ignoreCase = true) == true
            }

            matchesCollection && matchesCategory && matchesSearch
        }

        val sorted = when (state.sortOption) {
            ProfileSortOption.RECENTLY_ADDED -> filtered.sortedByDescending { it.addedAt }
            ProfileSortOption.TITLE -> filtered.sortedBy { it.title.lowercase() }
            ProfileSortOption.RATING -> filtered.sortedWith(
                compareByDescending<LibraryEntry> { it.personalRating?.value ?: -1 }
                    .thenBy { it.title.lowercase() }
            )
            ProfileSortOption.PROGRESS -> filtered.sortedWith(
                compareByDescending<LibraryEntry> { it.progressSortMetric() }
                    .thenBy { it.title.lowercase() }
            )
        }

        mutableUiState.update { it.copy(entries = sorted) }
    }
}
