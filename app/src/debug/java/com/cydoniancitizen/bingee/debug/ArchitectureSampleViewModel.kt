package com.cydoniancitizen.bingee.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cydoniancitizen.bingee.core.model.MediaSearchResult
import com.cydoniancitizen.bingee.core.result.AppError
import com.cydoniancitizen.bingee.core.result.AppResult
import com.cydoniancitizen.bingee.domain.repository.MediaRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ArchitectureSampleUiState {
    data object Initial : ArchitectureSampleUiState

    data object Loading : ArchitectureSampleUiState

    data class Content(val results: List<MediaSearchResult>) : ArchitectureSampleUiState

    data class Failure(val error: AppError) : ArchitectureSampleUiState
}

class ArchitectureSampleViewModel(
    private val mediaRepository: MediaRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate
) : ViewModel() {
    private val mutableUiState =
        MutableStateFlow<ArchitectureSampleUiState>(ArchitectureSampleUiState.Initial)

    val uiState: StateFlow<ArchitectureSampleUiState> = mutableUiState.asStateFlow()

    fun load(query: String) {
        mutableUiState.value = ArchitectureSampleUiState.Loading
        viewModelScope.launch(dispatcher) {
            mutableUiState.value =
                when (val result = mediaRepository.search(query)) {
                    is AppResult.Success -> ArchitectureSampleUiState.Content(result.value)
                    is AppResult.Failure -> ArchitectureSampleUiState.Failure(result.error)
                }
        }
    }
}
