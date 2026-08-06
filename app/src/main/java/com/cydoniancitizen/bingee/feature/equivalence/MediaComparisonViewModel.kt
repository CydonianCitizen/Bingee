package com.cydoniancitizen.bingee.feature.equivalence

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cydoniancitizen.bingee.core.model.LinkedMediaIdentity
import com.cydoniancitizen.bingee.core.model.MediaLinkAuditOrigin
import com.cydoniancitizen.bingee.core.result.AppError
import com.cydoniancitizen.bingee.core.result.AppResult
import com.cydoniancitizen.bingee.domain.equivalence.CandidateMediaProjection
import com.cydoniancitizen.bingee.domain.equivalence.MediaEquivalenceClassification
import com.cydoniancitizen.bingee.domain.equivalence.MediaEquivalenceEvaluation
import com.cydoniancitizen.bingee.domain.repository.MediaEquivalenceCandidateRepository
import com.cydoniancitizen.bingee.domain.repository.MediaLinkRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MediaComparisonUiState(
    val firstIdentity: LinkedMediaIdentity? = null,
    val secondIdentity: LinkedMediaIdentity? = null,
    val evaluation: MediaEquivalenceEvaluation? = null,
    val selectedPreferred: LinkedMediaIdentity? = null,
    val isLoading: Boolean = false,
    val isLinking: Boolean = false,
    val linkError: AppError? = null,
    val linkSuccess: Boolean = false,
    val isStale: Boolean = false
)

@HiltViewModel
class MediaComparisonViewModel @Inject constructor(
    private val candidateRepository: MediaEquivalenceCandidateRepository,
    private val linkRepository: MediaLinkRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MediaComparisonUiState())
    val uiState: StateFlow<MediaComparisonUiState> = _uiState.asStateFlow()

    fun loadComparison(first: LinkedMediaIdentity, second: LinkedMediaIdentity) {
        if (_uiState.value.firstIdentity == first && _uiState.value.secondIdentity == second &&
            _uiState.value.evaluation != null
        ) {
            return
        }

        _uiState.update {
            it.copy(
                firstIdentity = first,
                secondIdentity = second,
                isLoading = true,
                linkError = null,
                linkSuccess = false,
                isStale = false
            )
        }

        viewModelScope.launch {
            when (val evalResult = candidateRepository.evaluatePair(first, second)) {
                is AppResult.Success -> {
                    val eval = evalResult.value
                    val isEligible = eval.classification == MediaEquivalenceClassification.EXACT_IDENTITY ||
                        eval.classification == MediaEquivalenceClassification.STRONG_POSSIBLE_SAME_WORK

                    _uiState.update {
                        it.copy(
                            evaluation = eval,
                            selectedPreferred = first, // Default preference to first member
                            isLoading = false,
                            isStale = !isEligible
                        )
                    }
                }
                is AppResult.Failure -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            linkError = evalResult.error,
                            isStale = true
                        )
                    }
                }
            }
        }
    }

    fun selectPreferred(identity: LinkedMediaIdentity) {
        val state = _uiState.value
        if (identity != state.firstIdentity && identity != state.secondIdentity) return
        _uiState.update { it.copy(selectedPreferred = identity) }
    }

    fun confirmLink() {
        val state = _uiState.value
        val first = state.firstIdentity ?: return
        val second = state.secondIdentity ?: return
        val preferred = state.selectedPreferred ?: return

        if (state.isLinking || state.isStale) return

        _uiState.update { it.copy(isLinking = true, linkError = null) }

        viewModelScope.launch {
            // Revalidate candidate status before linking
            when (val evalResult = candidateRepository.evaluatePair(first, second)) {
                is AppResult.Failure -> {
                    _uiState.update { it.copy(isLinking = false, isStale = true, linkError = evalResult.error) }
                    return@launch
                }
                is AppResult.Success -> {
                    val eval = evalResult.value
                    if (eval.classification != MediaEquivalenceClassification.EXACT_IDENTITY &&
                        eval.classification != MediaEquivalenceClassification.STRONG_POSSIBLE_SAME_WORK
                    ) {
                        _uiState.update {
                            it.copy(isLinking = false, isStale = true, linkError = AppError.LinkError.AlreadyLinked)
                        }
                        return@launch
                    }
                }
            }

            when (
                val linkResult = linkRepository.createLink(
                    first,
                    second,
                    preferred,
                    MediaLinkAuditOrigin.MANUAL_USER_ACTION
                )
            ) {
                is AppResult.Success -> {
                    _uiState.update { it.copy(isLinking = false, linkSuccess = true) }
                }
                is AppResult.Failure -> {
                    _uiState.update { it.copy(isLinking = false, linkError = linkResult.error) }
                }
            }
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(linkError = null) }
    }
}
