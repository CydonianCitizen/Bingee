@file:Suppress("ktlint:standard:max-line-length")

package com.cydoniancitizen.bingee.feature.tvtimeimport

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.core.result.AppError
import com.cydoniancitizen.bingee.core.result.AppResult
import com.cydoniancitizen.bingee.data.imports.model.ImportedSourceDocument
import com.cydoniancitizen.bingee.data.imports.model.ImportedSourceSummary
import com.cydoniancitizen.bingee.data.imports.tvtime.TmdbImportCandidate
import com.cydoniancitizen.bingee.data.imports.tvtime.TmdbImportEpisodeCandidate
import com.cydoniancitizen.bingee.data.imports.tvtime.TvTimeArchiveFailureKind
import com.cydoniancitizen.bingee.data.imports.tvtime.TvTimeEpisodeReview
import com.cydoniancitizen.bingee.data.imports.tvtime.TvTimeImportPlan
import com.cydoniancitizen.bingee.data.imports.tvtime.TvTimeImportPlanBuilder
import com.cydoniancitizen.bingee.data.imports.tvtime.TvTimeImportPlanResult
import com.cydoniancitizen.bingee.data.imports.tvtime.TvTimeImportPreview
import com.cydoniancitizen.bingee.data.imports.tvtime.TvTimeImportReport
import com.cydoniancitizen.bingee.data.imports.tvtime.TvTimeImportStore
import com.cydoniancitizen.bingee.data.imports.tvtime.TvTimeMatchConfidence
import com.cydoniancitizen.bingee.data.imports.tvtime.TvTimeMatchReport
import com.cydoniancitizen.bingee.data.imports.tvtime.TvTimeMatcher
import com.cydoniancitizen.bingee.data.imports.tvtime.TvTimeMediaReview
import com.cydoniancitizen.bingee.data.imports.tvtime.TvTimeParseFailure
import com.cydoniancitizen.bingee.data.imports.tvtime.TvTimeParseFailureKind
import com.cydoniancitizen.bingee.data.imports.tvtime.TvTimeParseResult
import com.cydoniancitizen.bingee.data.imports.tvtime.TvTimePlanFailure
import com.cydoniancitizen.bingee.data.imports.tvtime.TvTimePlanFailureReason
import com.cydoniancitizen.bingee.data.imports.tvtime.TvTimeReviewAction
import com.cydoniancitizen.bingee.data.imports.tvtime.TvTimeSourceParser
import com.cydoniancitizen.bingee.data.imports.tvtime.TvTimeTmdbGateway
import com.cydoniancitizen.bingee.di.DefaultDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

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

internal enum class TvTimeMatchFilter { ALL, EXACT, HIGH_CONFIDENCE, NEEDS_REVIEW, UNMATCHED, INVALID, SKIPPED }

internal data class TvTimeEpisodeReviewGroup(
    val parentRecordId: String,
    val seasonNumber: Int,
    val seriesTitle: String,
    val reviews: List<TvTimeEpisodeReview>
)

internal data class TvTimeReviewUiState(
    val visibleMedia: List<TvTimeMediaReview>,
    val visibleEpisodeGroups: List<TvTimeEpisodeReviewGroup>,
    val filterCounts: Map<TvTimeMatchFilter, Int>,
    val showInvalidRecordSummary: Boolean
)

internal data class TvTimeImportUiState(
    val stage: TvTimeImportStage = TvTimeImportStage.IDLE,
    val summary: ImportedSourceSummary? = null,
    val matchReport: TvTimeMatchReport? = null,
    val preview: TvTimeImportPreview? = null,
    val result: TvTimeImportReport? = null,
    val failure: TvTimeImportUiFailure? = null,
    val manualCandidates: Map<String, List<TmdbImportCandidate>> = emptyMap(),
    val manualSearchFailures: Set<String> = emptySet(),
    val selectedFilter: TvTimeMatchFilter = TvTimeMatchFilter.ALL,
    val reviewState: TvTimeReviewUiState? = null
)

// Projection and filter travel together; superseded calculations cannot publish stale rows.
@OptIn(ExperimentalCoroutinesApi::class)
internal fun Flow<TvTimeImportUiState>.projectReview(dispatcher: CoroutineDispatcher): Flow<TvTimeImportUiState> =
    mapLatest { state ->
        withContext(dispatcher) {
            state.copy(
                reviewState = state.matchReport?.let {
                    deriveTvTimeReviewUiState(it, state.summary?.invalidRecordCount ?: 0, state.selectedFilter)
                }
            )
        }
    }

internal suspend fun MutableStateFlow<TvTimeImportUiState>.updateReviewReport(
    dispatcher: CoroutineDispatcher,
    transform: (TvTimeMatchReport) -> TvTimeMatchReport
) {
    val report = value.matchReport ?: return
    withContext(dispatcher) {
        val updated = transform(report)
        currentCoroutineContext().ensureActive()
        update { state ->
            // A cancelled/replaced archive must not be resurrected by an in-flight calculation.
            if (state.matchReport === report) state.copy(matchReport = updated) else state
        }
    }
}

internal fun deriveTvTimeReviewUiState(
    report: TvTimeMatchReport,
    invalidRecordCount: Int,
    filter: TvTimeMatchFilter
): TvTimeReviewUiState {
    val counts = TvTimeMatchFilter.entries.associateWith { 0 }.toMutableMap()
    val parentTitles = report.media.associate { it.source.recordId to it.source.title }
    val visibleMedia = if (filter == TvTimeMatchFilter.ALL) {
        null
    } else {
        ArrayList<TvTimeMediaReview>()
    }
    val visibleEpisodeGroups = linkedMapOf<Pair<String, Int>, MutableList<TvTimeEpisodeReview>>()

    fun addCounts(confidence: TvTimeMatchConfidence, action: TvTimeReviewAction) {
        when (confidence) {
            TvTimeMatchConfidence.EXACT -> counts[TvTimeMatchFilter.EXACT] =
                counts.getValue(TvTimeMatchFilter.EXACT) + 1
            TvTimeMatchConfidence.HIGH_CONFIDENCE -> counts[TvTimeMatchFilter.HIGH_CONFIDENCE] =
                counts.getValue(TvTimeMatchFilter.HIGH_CONFIDENCE) + 1
            TvTimeMatchConfidence.AMBIGUOUS -> counts[TvTimeMatchFilter.NEEDS_REVIEW] =
                counts.getValue(TvTimeMatchFilter.NEEDS_REVIEW) + 1
            TvTimeMatchConfidence.UNMATCHED -> counts[TvTimeMatchFilter.UNMATCHED] =
                counts.getValue(TvTimeMatchFilter.UNMATCHED) + 1
            TvTimeMatchConfidence.INVALID,
            TvTimeMatchConfidence.SKIPPED -> Unit
        }
        if (action == TvTimeReviewAction.SKIP) {
            counts[TvTimeMatchFilter.SKIPPED] = counts.getValue(TvTimeMatchFilter.SKIPPED) + 1
        }
    }

    report.media.forEach { review ->
        addCounts(review.confidence, review.action)
        if (visibleMedia != null && review.matches(filter)) visibleMedia += review
    }
    report.episodes.forEach { review ->
        addCounts(review.confidence, review.action)
        if (review.matches(filter)) {
            visibleEpisodeGroups.getOrPut(review.source.parentRecordId to review.source.seasonNumber) {
                mutableListOf()
            }
                .add(review)
        }
    }

    counts[TvTimeMatchFilter.ALL] = report.media.size + report.episodes.size + invalidRecordCount
    counts[TvTimeMatchFilter.INVALID] = invalidRecordCount

    val groups = visibleEpisodeGroups.entries
        .sortedWith(
            compareBy<Map.Entry<Pair<String, Int>, MutableList<TvTimeEpisodeReview>>> {
                parentTitles[it.key.first].orEmpty().lowercase()
            }.thenBy { it.key.second }
        )
        .map { (key, reviews) ->
            TvTimeEpisodeReviewGroup(
                parentRecordId = key.first,
                seasonNumber = key.second,
                seriesTitle = parentTitles[key.first].orEmpty(),
                reviews = reviews.toList()
            )
        }

    return TvTimeReviewUiState(
        visibleMedia = visibleMedia ?: report.media,
        visibleEpisodeGroups = groups,
        filterCounts = counts.toMap(),
        showInvalidRecordSummary = filter in setOf(TvTimeMatchFilter.ALL, TvTimeMatchFilter.INVALID) &&
            invalidRecordCount > 0
    )
}

private fun TvTimeMediaReview.matches(filter: TvTimeMatchFilter): Boolean = when (filter) {
    TvTimeMatchFilter.ALL -> true
    TvTimeMatchFilter.EXACT -> confidence == TvTimeMatchConfidence.EXACT
    TvTimeMatchFilter.HIGH_CONFIDENCE -> confidence == TvTimeMatchConfidence.HIGH_CONFIDENCE
    TvTimeMatchFilter.NEEDS_REVIEW -> confidence == TvTimeMatchConfidence.AMBIGUOUS
    TvTimeMatchFilter.UNMATCHED -> confidence == TvTimeMatchConfidence.UNMATCHED
    TvTimeMatchFilter.INVALID -> confidence == TvTimeMatchConfidence.INVALID
    TvTimeMatchFilter.SKIPPED -> action == TvTimeReviewAction.SKIP
}

private fun TvTimeEpisodeReview.matches(filter: TvTimeMatchFilter): Boolean = when (filter) {
    TvTimeMatchFilter.ALL -> true
    TvTimeMatchFilter.EXACT -> confidence == TvTimeMatchConfidence.EXACT
    TvTimeMatchFilter.HIGH_CONFIDENCE -> confidence == TvTimeMatchConfidence.HIGH_CONFIDENCE
    TvTimeMatchFilter.NEEDS_REVIEW -> confidence == TvTimeMatchConfidence.AMBIGUOUS
    TvTimeMatchFilter.UNMATCHED -> confidence == TvTimeMatchConfidence.UNMATCHED
    TvTimeMatchFilter.INVALID -> confidence == TvTimeMatchConfidence.INVALID
    TvTimeMatchFilter.SKIPPED -> action == TvTimeReviewAction.SKIP
}

@HiltViewModel
internal class TvTimeImportViewModel @Inject constructor(
    private val parser: TvTimeSourceParser,
    private val matcher: TvTimeMatcher,
    private val gateway: TvTimeTmdbGateway,
    private val planBuilder: TvTimeImportPlanBuilder,
    private val store: TvTimeImportStore,
    private val clock: Clock,
    @param:DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher
) : ViewModel() {
    private val mutableState = MutableStateFlow(TvTimeImportUiState())
    val uiState: StateFlow<TvTimeImportUiState> = mutableState.projectReview(defaultDispatcher)
        .stateIn(
            CoroutineScope(viewModelScope.coroutineContext + defaultDispatcher),
            SharingStarted.Eagerly,
            TvTimeImportUiState()
        )
    private val reviewMutex = Mutex()
    private var reviewGeneration = 0L
    private var document: ImportedSourceDocument? = null
    private var plan: TvTimeImportPlan? = null
    private var operation: Job? = null
    private val searchJobs = mutableMapOf<String, Job>()
    private val searchGenerations = mutableMapOf<String, Long>()
    private var nextSearchGeneration = 0L

    fun selectArchive(uri: Uri) {
        if (isBusy()) return
        operation?.cancel()
        reviewGeneration++
        matcher.clearSession()
        document = null
        plan = null
        mutableState.value = TvTimeImportUiState(stage = TvTimeImportStage.READING)
        operation = viewModelScope.launch {
            when (val parsed = parser.parse(uri)) {
                is TvTimeParseResult.Success -> {
                    document = parsed.document
                    mutableState.value = TvTimeImportUiState(
                        stage = TvTimeImportStage.SOURCE_SUMMARY,
                        summary = parsed.document.summary
                    )
                }
                is TvTimeParseResult.Failure -> {
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
                AppError.Unauthorized -> TvTimeImportUiFailure.MISSING_CREDENTIAL
                AppError.NetworkUnavailable,
                AppError.RemoteServiceFailure -> TvTimeImportUiFailure.NETWORK
                AppError.RateLimited -> TvTimeImportUiFailure.RATE_LIMIT
                else -> null
            }
            mutableState.update { state ->
                state.copy(
                    stage = TvTimeImportStage.REVIEW,
                    matchReport = report,
                    failure = failure
                )
            }
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
        if (isBusy() || mutableState.value.matchReport == null) return
        mutableState.update { it.copy(stage = TvTimeImportStage.MATCHING, failure = null) }
        operation = viewModelScope.launch {
            reviewMutex.withLock {
                val current = mutableState.value.matchReport ?: return@withLock
                val nextReport = withContext(defaultDispatcher) {
                    val sourceMedia = current.media.firstOrNull { it.source.recordId == recordId }
                    if (sourceMedia == null || candidate.mediaType != sourceMedia.source.mediaType) {
                        null
                    } else {
                        current.copy(
                            media = current.media.map { review ->
                                if (review.source.recordId == recordId) {
                                    review.copy(
                                        action = TvTimeReviewAction.SELECT_CANDIDATE,
                                        selectedCandidate = candidate
                                    )
                                } else {
                                    review
                                }
                            }
                        )
                    }
                }
                if (nextReport != null) {
                    mutableState.update { it.copy(matchReport = nextReport) }
                    val episodes = matcher.rematchEpisodes(source, nextReport.media)
                    mutableState.update { it.copy(matchReport = nextReport.copy(episodes = episodes)) }
                }
                mutableState.update { it.copy(stage = TvTimeImportStage.REVIEW) }
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
            delay(250)
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
        if (mutableState.value.matchReport == null || isBusy()) return
        mutableState.update { it.copy(stage = TvTimeImportStage.PREPARING_PLAN, failure = null) }
        operation = viewModelScope.launch {
            // Include edits queued before the user requested a preview.
            val report = reviewMutex.withLock { mutableState.value.matchReport } ?: return@launch
            when (val result = planBuilder.build(source, report, clock.instant())) {
                is TvTimeImportPlanResult.Failure -> {
                    mutableState.update {
                        it.copy(
                            stage = TvTimeImportStage.REVIEW,
                            failure = result.failure.toUiFailure()
                        )
                    }
                }
                is TvTimeImportPlanResult.Success -> {
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
        reviewGeneration++
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
        if (isBusy()) return
        val generation = reviewGeneration
        viewModelScope.launch {
            reviewMutex.withLock {
                if (generation != reviewGeneration) return@withLock
                mutableState.updateReviewReport(defaultDispatcher, transform)
            }
        }
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

private fun TvTimeParseFailure.toUiFailure(): TvTimeImportUiFailure = when (kind) {
    TvTimeParseFailureKind.ARCHIVE -> when (archiveFailure) {
        TvTimeArchiveFailureKind.ENCRYPTED_ENTRY ->
            TvTimeImportUiFailure.ENCRYPTED_ZIP
        TvTimeArchiveFailureKind.UNSUPPORTED_LAYOUT,
        TvTimeArchiveFailureKind.UNSUPPORTED_ENTRY ->
            TvTimeImportUiFailure.UNSUPPORTED_ARCHIVE_LAYOUT
        TvTimeArchiveFailureKind.PATH_TRAVERSAL,
        TvTimeArchiveFailureKind.ABSOLUTE_PATH,
        TvTimeArchiveFailureKind.DRIVE_PATH,
        TvTimeArchiveFailureKind.UNC_PATH,
        TvTimeArchiveFailureKind.NULL_BYTE_PATH,
        TvTimeArchiveFailureKind.DUPLICATE_PATH,
        TvTimeArchiveFailureKind.CASE_COLLISION,
        TvTimeArchiveFailureKind.NESTED_ARCHIVE,
        TvTimeArchiveFailureKind.OVERSIZED_INPUT,
        TvTimeArchiveFailureKind.OVERSIZED_ENTRY,
        TvTimeArchiveFailureKind.OVERSIZED_TOTAL,
        TvTimeArchiveFailureKind.SUSPICIOUS_COMPRESSION,
        TvTimeArchiveFailureKind.TOO_MANY_ENTRIES ->
            TvTimeImportUiFailure.UNSAFE_ZIP
        else -> TvTimeImportUiFailure.UNREADABLE_ZIP
    }
    TvTimeParseFailureKind.MISSING_ROLE,
    TvTimeParseFailureKind.DUPLICATE_ROLE,
    TvTimeParseFailureKind.AMBIGUOUS_ROLE,
    TvTimeParseFailureKind.UNKNOWN_ROLE,
    TvTimeParseFailureKind.EMPTY_ARRAY -> TvTimeImportUiFailure.UNSUPPORTED_PROFILE
    else -> TvTimeImportUiFailure.INVALID_SOURCE
}

private fun TvTimePlanFailure.toUiFailure(): TvTimeImportUiFailure = when (reason) {
    TvTimePlanFailureReason.REVIEW_REQUIRED ->
        TvTimeImportUiFailure.PLAN_REQUIRES_REVIEW
    TvTimePlanFailureReason.INVALID_CANONICAL_DATA ->
        TvTimeImportUiFailure.INVALID_SOURCE
    TvTimePlanFailureReason.PROVIDER_FAILURE -> when (error) {
        AppError.Unauthorized -> TvTimeImportUiFailure.MISSING_CREDENTIAL
        AppError.NetworkUnavailable,
        AppError.RemoteServiceFailure -> TvTimeImportUiFailure.NETWORK
        AppError.RateLimited -> TvTimeImportUiFailure.RATE_LIMIT
        else -> TvTimeImportUiFailure.UNKNOWN
    }
}
