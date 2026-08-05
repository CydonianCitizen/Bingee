@file:Suppress("ktlint:standard:max-line-length")

package com.cydoniancitizen.bingee.feature.tvtimeimport

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.core.result.AppResult
import com.cydoniancitizen.bingee.data.imports.model.ImportedSourceDocument
import com.cydoniancitizen.bingee.data.imports.tvtime.TmdbImportCandidate
import com.cydoniancitizen.bingee.data.imports.tvtime.TmdbImportEpisodeCandidate
import com.cydoniancitizen.bingee.data.imports.tvtime.TvTimeEpisodeReview
import com.cydoniancitizen.bingee.data.imports.tvtime.TvTimeImportPlan
import com.cydoniancitizen.bingee.data.imports.tvtime.TvTimeImportPlanBuilder
import com.cydoniancitizen.bingee.data.imports.tvtime.TvTimeImportPreview
import com.cydoniancitizen.bingee.data.imports.tvtime.TvTimeImportReport
import com.cydoniancitizen.bingee.data.imports.tvtime.TvTimeImportStore
import com.cydoniancitizen.bingee.data.imports.tvtime.TvTimeMatchConfidence
import com.cydoniancitizen.bingee.data.imports.tvtime.TvTimeMatchReport
import com.cydoniancitizen.bingee.data.imports.tvtime.TvTimeMediaReview
import com.cydoniancitizen.bingee.data.imports.tvtime.TvTimeReviewAction
import com.cydoniancitizen.bingee.data.imports.tvtime.TvTimeSourceParser
import com.cydoniancitizen.bingee.data.imports.tvtime.TvTimeTmdbGateway
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal enum class TvTimeImportStage {
    IDLE,
    READING,
    SOURCE_SUMMARY,
    MATCHING,
    REVIEW,
    PREPARING_PLAN,
    PREVIEW,
    IMPORTING,
    SUCCESS,
    FAILURE
}

internal enum class TvTimeImportUiFailure {
    UNREADABLE_ZIP,
    UNSAFE_ZIP,
    ENCRYPTED_ZIP,
    UNSUPPORTED_ARCHIVE_LAYOUT,
    UNSUPPORTED_PROFILE,
    INVALID_SOURCE,
    MISSING_CREDENTIAL,
    RATE_LIMIT,
    NETWORK,
    PLAN_REQUIRES_REVIEW,
    TRANSACTION,
    UNKNOWN
}

internal data class TvTimeImportUiState(
    val stage: TvTimeImportStage = TvTimeImportStage.IDLE,
    val summary: com.cydoniancitizen.bingee.data.imports.model.ImportedSourceSummary? = null,
    val matchReport: TvTimeMatchReport? = null,
    val preview: TvTimeImportPreview? = null,
    val result: TvTimeImportReport? = null,
    val failure: TvTimeImportUiFailure? = null,
    val manualCandidates: Map<String, List<TmdbImportCandidate>> = emptyMap(),
    val manualSearchFailures: Set<String> = emptySet(),
    val selectedFilter: TvTimeMatchFilter = TvTimeMatchFilter.ALL
)

internal enum class TvTimeMatchFilter { ALL, EXACT, HIGH_CONFIDENCE, NEEDS_REVIEW, UNMATCHED, INVALID, SKIPPED }

@HiltViewModel
internal class TvTimeImportViewModel @Inject constructor(
    private val parser: TvTimeSourceParser,
    private val matcher: com.cydoniancitizen.bingee.data.imports.tvtime.TvTimeMatcher,
    private val gateway: TvTimeTmdbGateway,
    private val planBuilder: TvTimeImportPlanBuilder,
    private val store: TvTimeImportStore,
    private val clock: Clock
) : ViewModel() {
    private val mutableState = MutableStateFlow(TvTimeImportUiState())
    val uiState: StateFlow<TvTimeImportUiState> = mutableState.asStateFlow()
    private var document: ImportedSourceDocument? = null
    private var plan: TvTimeImportPlan? = null
    private var operation: Job? = null
    private val searchJobs = mutableMapOf<String, Job>()
    private val searchGenerations = mutableMapOf<String, Long>()
    private var nextSearchGeneration = 0L

    fun selectArchive(uri: Uri) {
        if (isBusy()) return
        operation?.cancel()
        matcher.clearSession()
        document = null
        plan = null
        mutableState.value = TvTimeImportUiState(stage = TvTimeImportStage.READING)
        operation = viewModelScope.launch {
            when (val parsed = parser.parse(uri)) {
                is com.cydoniancitizen.bingee.data.imports.tvtime.TvTimeParseResult.Success -> {
                    document = parsed.document
                    mutableState.value = TvTimeImportUiState(
                        stage = TvTimeImportStage.SOURCE_SUMMARY,
                        summary = parsed.document.summary
                    )
                }
                is com.cydoniancitizen.bingee.data.imports.tvtime.TvTimeParseResult.Failure -> {
                    mutableState.value = TvTimeImportUiState(
                        stage = TvTimeImportStage.FAILURE,
                        failure = parsed.failure.toUiFailure()
                    )
                }
            }
        }
    }

    fun startMatching() {
        val source = document ?: return
        if (isBusy()) return
        mutableState.update { it.copy(stage = TvTimeImportStage.MATCHING, failure = null) }
        operation = viewModelScope.launch {
            val report = matcher.match(source)
            val failure = when (report.recoverableError) {
                com.cydoniancitizen.bingee.core.result.AppError.Unauthorized -> TvTimeImportUiFailure.MISSING_CREDENTIAL
                com.cydoniancitizen.bingee.core.result.AppError.NetworkUnavailable,
                com.cydoniancitizen.bingee.core.result.AppError.RemoteServiceFailure -> TvTimeImportUiFailure.NETWORK
                com.cydoniancitizen.bingee.core.result.AppError.RateLimited -> TvTimeImportUiFailure.RATE_LIMIT
                else -> null
            }
            mutableState.update { it.copy(stage = TvTimeImportStage.REVIEW, matchReport = report, failure = failure) }
        }
    }

    fun acceptExact() = updateReport { report ->
        report.copy(media = report.media.map(::acceptExactMedia), episodes = report.episodes.map(::acceptExactEpisode))
    }

    fun acceptHighConfidence() = updateReport { report ->
        report.copy(
            media = report.media.map { review ->
                if (review.confidence == TvTimeMatchConfidence.HIGH_CONFIDENCE && review.proposed != null) {
                    review.copy(action = TvTimeReviewAction.ACCEPT_PROPOSED)
                } else {
                    review
                }
            },
            episodes = report.episodes.map { review ->
                if (review.confidence == TvTimeMatchConfidence.HIGH_CONFIDENCE && review.proposed != null) {
                    review.copy(action = TvTimeReviewAction.ACCEPT_PROPOSED)
                } else {
                    review
                }
            }
        )
    }

    fun skip(recordId: String) = updateReport { report ->
        val isMediaRecord = report.media.any { it.source.recordId == recordId }
        report.copy(
            media = report.media.map {
                if (it.source.recordId ==
                    recordId
                ) {
                    it.copy(action = TvTimeReviewAction.SKIP)
                } else {
                    it
                }
            },
            episodes = report.episodes.map {
                if (it.source.recordId == recordId ||
                    (isMediaRecord && it.source.parentRecordId == recordId)
                ) {
                    it.copy(action = TvTimeReviewAction.SKIP)
                } else {
                    it
                }
            }
        )
    }

    fun skipSeason(parentRecordId: String, seasonNumber: Int) = updateReport { report ->
        report.copy(
            episodes = report.episodes.map { review ->
                if (review.source.parentRecordId == parentRecordId &&
                    review.source.seasonNumber == seasonNumber
                ) {
                    review.copy(action = TvTimeReviewAction.SKIP)
                } else {
                    review
                }
            }
        )
    }

    fun selectMediaCandidate(recordId: String, candidate: TmdbImportCandidate) {
        val source = document ?: return
        val current = mutableState.value.matchReport ?: return
        val sourceMedia = current.media.firstOrNull { it.source.recordId == recordId } ?: return
        if (candidate.mediaType != sourceMedia.source.mediaType) return
        val updatedMedia = current.media.map { review ->
            if (review.source.recordId == recordId) {
                review.copy(action = TvTimeReviewAction.SELECT_CANDIDATE, selectedCandidate = candidate)
            } else {
                review
            }
        }
        if (isBusy()) return
        operation?.cancel()
        mutableState.update {
            it.copy(
                stage = TvTimeImportStage.MATCHING,
                failure = null,
                matchReport = current.copy(media = updatedMedia)
            )
        }
        operation = viewModelScope.launch {
            val episodes = matcher.rematchEpisodes(source, updatedMedia)
            mutableState.update { state ->
                state.copy(
                    stage = TvTimeImportStage.REVIEW,
                    matchReport = state.matchReport?.copy(episodes = episodes)
                )
            }
        }
    }

    fun selectEpisodeCandidate(recordId: String, candidate: TmdbImportEpisodeCandidate) = updateReport { report ->
        report.copy(
            episodes = report.episodes.map { review ->
                if (review.source.recordId == recordId) {
                    review.copy(action = TvTimeReviewAction.SELECT_CANDIDATE, selectedCandidate = candidate)
                } else {
                    review
                }
            }
        )
    }

    fun search(recordId: String, query: String, mediaType: MediaType) {
        val source = document?.let { doc ->
            doc.movies.firstOrNull { it.recordId == recordId } ?: doc.series.firstOrNull { it.recordId == recordId }
        } ?: return
        if (source.mediaType != mediaType) return
        searchJobs.remove(recordId)?.cancel()
        val generation = ++nextSearchGeneration
        searchGenerations[recordId] = generation
        if (query.isBlank()) {
            mutableState.update {
                it.copy(
                    manualCandidates = it.manualCandidates - recordId,
                    manualSearchFailures = it.manualSearchFailures - recordId
                )
            }
            return
        }
        mutableState.update { it.copy(manualSearchFailures = it.manualSearchFailures - recordId) }
        searchJobs[recordId] = viewModelScope.launch {
            kotlinx.coroutines.delay(250)
            when (val result = gateway.searchMedia(mediaType, query.trim(), source.year)) {
                is AppResult.Success -> if (searchGenerations[recordId] == generation) {
                    mutableState.update {
                        it.copy(
                            manualCandidates = it.manualCandidates + (recordId to result.value),
                            manualSearchFailures = it.manualSearchFailures - recordId
                        )
                    }
                }
                is AppResult.Failure -> if (searchGenerations[recordId] == generation) {
                    mutableState.update {
                        it.copy(
                            manualCandidates = it.manualCandidates - recordId,
                            manualSearchFailures = it.manualSearchFailures + recordId
                        )
                    }
                }
            }
        }
    }

    fun preparePreview() {
        val source = document ?: return
        val report = mutableState.value.matchReport ?: return
        if (isBusy()) return
        mutableState.update { it.copy(stage = TvTimeImportStage.PREPARING_PLAN, failure = null) }
        operation = viewModelScope.launch {
            when (val result = planBuilder.build(source, report, clock.instant())) {
                is com.cydoniancitizen.bingee.data.imports.tvtime.TvTimeImportPlanResult.Failure -> {
                    mutableState.update {
                        it.copy(
                            stage = TvTimeImportStage.REVIEW,
                            failure = result.failure.toUiFailure()
                        )
                    }
                }
                is com.cydoniancitizen.bingee.data.imports.tvtime.TvTimeImportPlanResult.Success -> {
                    plan = result.plan
                    val preview = store.preview(result.plan)
                    mutableState.update { it.copy(stage = TvTimeImportStage.PREVIEW, preview = preview) }
                }
            }
        }
    }

    fun confirmImport() {
        val pending = plan ?: return
        val expectedPreview = mutableState.value.preview ?: return
        if (isBusy()) return
        mutableState.update { it.copy(stage = TvTimeImportStage.IMPORTING, failure = null) }
        operation = viewModelScope.launch {
            when (val result = store.import(pending, expectedPreview)) {
                is AppResult.Success -> mutableState.update {
                    matcher.clearSession()
                    it.copy(stage = TvTimeImportStage.SUCCESS, result = result.value)
                }
                is AppResult.Failure -> mutableState.update {
                    it.copy(stage = TvTimeImportStage.FAILURE, failure = TvTimeImportUiFailure.TRANSACTION)
                }
            }
        }
    }

    fun cancelReview() {
        operation?.cancel()
        searchJobs.values.forEach(Job::cancel)
        searchJobs.clear()
        searchGenerations.clear()
        document = null
        plan = null
        matcher.clearSession()
        mutableState.value = TvTimeImportUiState()
    }

    fun setFilter(filter: TvTimeMatchFilter) = mutableState.update { it.copy(selectedFilter = filter) }

    private fun updateReport(transform: (TvTimeMatchReport) -> TvTimeMatchReport) {
        mutableState.update { state -> state.matchReport?.let { state.copy(matchReport = transform(it)) } ?: state }
    }

    private fun isBusy(): Boolean = mutableState.value.stage in setOf(
        TvTimeImportStage.READING,
        TvTimeImportStage.MATCHING,
        TvTimeImportStage.PREPARING_PLAN,
        TvTimeImportStage.IMPORTING
    )

    private fun acceptExactMedia(review: TvTimeMediaReview): TvTimeMediaReview =
        if (review.confidence == TvTimeMatchConfidence.EXACT && review.proposed != null) {
            review.copy(action = TvTimeReviewAction.ACCEPT_PROPOSED)
        } else {
            review
        }

    private fun acceptExactEpisode(review: TvTimeEpisodeReview): TvTimeEpisodeReview =
        if (review.confidence == TvTimeMatchConfidence.EXACT && review.proposed != null) {
            review.copy(action = TvTimeReviewAction.ACCEPT_PROPOSED)
        } else {
            review
        }
}

private fun com.cydoniancitizen.bingee.data.imports.tvtime.TvTimeParseFailure.toUiFailure(): TvTimeImportUiFailure =
    when (kind) {
        com.cydoniancitizen.bingee.data.imports.tvtime.TvTimeParseFailureKind.ARCHIVE -> when (archiveFailure) {
            com.cydoniancitizen.bingee.data.imports.tvtime.TvTimeArchiveFailureKind.ENCRYPTED_ENTRY ->
                TvTimeImportUiFailure.ENCRYPTED_ZIP
            com.cydoniancitizen.bingee.data.imports.tvtime.TvTimeArchiveFailureKind.UNSUPPORTED_LAYOUT,
            com.cydoniancitizen.bingee.data.imports.tvtime.TvTimeArchiveFailureKind.UNSUPPORTED_ENTRY ->
                TvTimeImportUiFailure.UNSUPPORTED_ARCHIVE_LAYOUT
            com.cydoniancitizen.bingee.data.imports.tvtime.TvTimeArchiveFailureKind.PATH_TRAVERSAL,
            com.cydoniancitizen.bingee.data.imports.tvtime.TvTimeArchiveFailureKind.ABSOLUTE_PATH,
            com.cydoniancitizen.bingee.data.imports.tvtime.TvTimeArchiveFailureKind.DRIVE_PATH,
            com.cydoniancitizen.bingee.data.imports.tvtime.TvTimeArchiveFailureKind.UNC_PATH,
            com.cydoniancitizen.bingee.data.imports.tvtime.TvTimeArchiveFailureKind.NULL_BYTE_PATH,
            com.cydoniancitizen.bingee.data.imports.tvtime.TvTimeArchiveFailureKind.DUPLICATE_PATH,
            com.cydoniancitizen.bingee.data.imports.tvtime.TvTimeArchiveFailureKind.CASE_COLLISION,
            com.cydoniancitizen.bingee.data.imports.tvtime.TvTimeArchiveFailureKind.NESTED_ARCHIVE,
            com.cydoniancitizen.bingee.data.imports.tvtime.TvTimeArchiveFailureKind.OVERSIZED_INPUT,
            com.cydoniancitizen.bingee.data.imports.tvtime.TvTimeArchiveFailureKind.OVERSIZED_ENTRY,
            com.cydoniancitizen.bingee.data.imports.tvtime.TvTimeArchiveFailureKind.OVERSIZED_TOTAL,
            com.cydoniancitizen.bingee.data.imports.tvtime.TvTimeArchiveFailureKind.SUSPICIOUS_COMPRESSION,
            com.cydoniancitizen.bingee.data.imports.tvtime.TvTimeArchiveFailureKind.TOO_MANY_ENTRIES ->
                TvTimeImportUiFailure.UNSAFE_ZIP
            else -> TvTimeImportUiFailure.UNREADABLE_ZIP
        }
        com.cydoniancitizen.bingee.data.imports.tvtime.TvTimeParseFailureKind.MISSING_ROLE,
        com.cydoniancitizen.bingee.data.imports.tvtime.TvTimeParseFailureKind.DUPLICATE_ROLE,
        com.cydoniancitizen.bingee.data.imports.tvtime.TvTimeParseFailureKind.AMBIGUOUS_ROLE,
        com.cydoniancitizen.bingee.data.imports.tvtime.TvTimeParseFailureKind.UNKNOWN_ROLE,
        com.cydoniancitizen.bingee.data.imports.tvtime.TvTimeParseFailureKind.EMPTY_ARRAY -> TvTimeImportUiFailure.UNSUPPORTED_PROFILE
        else -> TvTimeImportUiFailure.INVALID_SOURCE
    }

private fun com.cydoniancitizen.bingee.data.imports.tvtime.TvTimePlanFailure.toUiFailure(): TvTimeImportUiFailure =
    when (reason) {
        com.cydoniancitizen.bingee.data.imports.tvtime.TvTimePlanFailureReason.REVIEW_REQUIRED ->
            TvTimeImportUiFailure.PLAN_REQUIRES_REVIEW
        com.cydoniancitizen.bingee.data.imports.tvtime.TvTimePlanFailureReason.INVALID_CANONICAL_DATA ->
            TvTimeImportUiFailure.INVALID_SOURCE
        com.cydoniancitizen.bingee.data.imports.tvtime.TvTimePlanFailureReason.PROVIDER_FAILURE -> when (error) {
            com.cydoniancitizen.bingee.core.result.AppError.Unauthorized -> TvTimeImportUiFailure.MISSING_CREDENTIAL
            com.cydoniancitizen.bingee.core.result.AppError.NetworkUnavailable,
            com.cydoniancitizen.bingee.core.result.AppError.RemoteServiceFailure -> TvTimeImportUiFailure.NETWORK
            com.cydoniancitizen.bingee.core.result.AppError.RateLimited -> TvTimeImportUiFailure.RATE_LIMIT
            else -> TvTimeImportUiFailure.UNKNOWN
        }
    }
