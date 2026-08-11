@file:Suppress("ktlint:standard:max-line-length")

package com.cydoniancitizen.bingee.feature.tvtimeimport

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cydoniancitizen.bingee.R
import com.cydoniancitizen.bingee.core.designsystem.theme.BingeeDimensions
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.data.imports.tvtime.TmdbImportCandidate
import com.cydoniancitizen.bingee.data.imports.tvtime.TmdbImportEpisodeCandidate
import com.cydoniancitizen.bingee.data.imports.tvtime.TvTimeEpisodeReview
import com.cydoniancitizen.bingee.data.imports.tvtime.TvTimeMatchConfidence
import com.cydoniancitizen.bingee.data.imports.tvtime.TvTimeMediaReview
import com.cydoniancitizen.bingee.data.imports.tvtime.TvTimeReviewAction

@Composable
internal fun TvTimeImportScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TvTimeImportViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::selectArchive)
    }
    TvTimeImportContent(
        state = state,
        onBack = onBack,
        onSelectArchive = {
            launcher.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
        },
        onStartMatching = viewModel::startMatching,
        onAcceptExact = viewModel::acceptExact,
        onAcceptHigh = viewModel::acceptHighConfidence,
        onSkip = viewModel::skip,
        onSkipSeason = viewModel::skipSeason,
        onSelectMediaCandidate = viewModel::selectMediaCandidate,
        onSelectEpisodeCandidate = viewModel::selectEpisodeCandidate,
        onSearch = viewModel::search,
        onPreparePreview = viewModel::preparePreview,
        onConfirm = viewModel::confirmImport,
        onCancel = viewModel::cancelReview,
        onSetFilter = viewModel::setFilter,
        modifier = modifier
    )
}

@Composable
internal fun TvTimeImportContent(
    state: TvTimeImportUiState,
    onBack: () -> Unit,
    onSelectArchive: () -> Unit,
    onStartMatching: () -> Unit,
    onAcceptExact: () -> Unit,
    onAcceptHigh: () -> Unit,
    onSkip: (String) -> Unit,
    onSelectMediaCandidate: (String, TmdbImportCandidate) -> Unit,
    onSelectEpisodeCandidate: (String, TmdbImportEpisodeCandidate) -> Unit,
    onSearch: (String, String, MediaType) -> Unit,
    onPreparePreview: () -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    onSetFilter: (TvTimeMatchFilter) -> Unit,
    modifier: Modifier = Modifier,
    onSkipSeason: (String, Int) -> Unit = { _, _ -> }
) {
    Column(
        modifier = modifier.fillMaxSize().padding(BingeeDimensions.screenPadding),
        verticalArrangement = Arrangement.spacedBy(BingeeDimensions.contentSpacing)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onBack, enabled = state.stage != TvTimeImportStage.IMPORTING) {
                Text(stringResource(R.string.action_cancel))
            }
            Text(
                text = stringResource(R.string.tvtime_import_title),
                modifier = Modifier.padding(top = 12.dp).semantics { heading() },
                style = MaterialTheme.typography.headlineMedium
            )
        }
        if (state.stage == TvTimeImportStage.REVIEW) {
            ReviewContent(
                state = state,
                onAcceptExact = onAcceptExact,
                onAcceptHigh = onAcceptHigh,
                onRetryMatching = onStartMatching,
                onSkip = onSkip,
                onSkipSeason = onSkipSeason,
                onSelectMediaCandidate = onSelectMediaCandidate,
                onSelectEpisodeCandidate = onSelectEpisodeCandidate,
                onSearch = onSearch,
                onPreparePreview = onPreparePreview,
                onCancel = onCancel,
                onSetFilter = onSetFilter,
                modifier = Modifier.weight(1f)
            )
        } else {
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(BingeeDimensions.contentSpacing)
            ) {
                if (state.stage == TvTimeImportStage.IDLE || state.stage == TvTimeImportStage.SOURCE_SUMMARY) {
                    Text(stringResource(R.string.tvtime_import_experimental))
                    Text(stringResource(R.string.tvtime_import_privacy), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.tvtime_import_limitations), style = MaterialTheme.typography.bodySmall)
                }
                when (state.stage) {
                    TvTimeImportStage.IDLE -> Button(onClick = onSelectArchive) {
                        Text(stringResource(R.string.tvtime_import_select_archive))
                    }
                    TvTimeImportStage.READING -> ProgressMessage(R.string.tvtime_import_reading)
                    TvTimeImportStage.SOURCE_SUMMARY -> SourceSummary(state, onStartMatching, onCancel)
                    TvTimeImportStage.MATCHING -> ProgressMessage(R.string.tvtime_import_matching)
                    TvTimeImportStage.PREPARING_PLAN -> ProgressMessage(R.string.tvtime_import_preparing)
                    TvTimeImportStage.IMPORTING -> ProgressMessage(R.string.tvtime_import_importing)
                    TvTimeImportStage.PREVIEW -> PreviewContent(state, onConfirm, onCancel)
                    TvTimeImportStage.SUCCESS -> SuccessContent(state, onBack)
                    TvTimeImportStage.FAILURE -> FailureContent(state, onSelectArchive, onBack)
                    TvTimeImportStage.REVIEW -> Unit
                }
            }
        }
    }
}

@Composable
private fun ProgressMessage(message: Int) {
    val messageDescription = stringResource(message)
    LinearProgressIndicator(
        modifier = Modifier.fillMaxWidth().semantics {
            stateDescription = messageDescription
            liveRegion = LiveRegionMode.Polite
        }
    )
    Text(stringResource(message), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
}

@Composable
private fun SourceSummary(state: TvTimeImportUiState, onStart: () -> Unit, onCancel: () -> Unit) {
    val summary = state.summary ?: return
    Text(stringResource(R.string.tvtime_import_source_summary), style = MaterialTheme.typography.titleLarge)
    Text(
        stringResource(
            R.string.tvtime_import_summary_counts,
            summary.movieRecordCount,
            summary.seriesCount,
            summary.seasonCount,
            summary.episodeCount
        )
    )
    Text(
        stringResource(
            R.string.tvtime_import_summary_watched,
            summary.watchedMovieCount,
            summary.watchedEpisodeCount,
            summary.specialsCount
        )
    )
    Text(
        stringResource(
            R.string.tvtime_import_summary_unsupported,
            summary.unsupported.favoriteRecords,
            summary.unsupported.customLists,
            summary.unsupported.rewatchRecords
        )
    )
    Text(stringResource(R.string.tvtime_import_summary_warning, summary.warningCount, summary.invalidRecordCount))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onStart) { Text(stringResource(R.string.tvtime_import_start_matching)) }
        TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
    }
}

@Composable
private fun ReviewContent(
    state: TvTimeImportUiState,
    onAcceptExact: () -> Unit,
    onAcceptHigh: () -> Unit,
    onRetryMatching: () -> Unit,
    onSkip: (String) -> Unit,
    onSkipSeason: (String, Int) -> Unit,
    onSelectMediaCandidate: (String, TmdbImportCandidate) -> Unit,
    onSelectEpisodeCandidate: (String, TmdbImportEpisodeCandidate) -> Unit,
    onSearch: (String, String, MediaType) -> Unit,
    onPreparePreview: () -> Unit,
    onCancel: () -> Unit,
    onSetFilter: (TvTimeMatchFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    val reviewState = state.reviewState ?: return
    val filterScrollState = rememberScrollState()
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(BingeeDimensions.elementSpacing)
    ) {
        Text(stringResource(R.string.tvtime_import_review_title), style = MaterialTheme.typography.titleLarge)
        when (state.failure) {
            TvTimeImportUiFailure.MISSING_CREDENTIAL -> Text(
                stringResource(R.string.tvtime_import_missing_credential),
                color = MaterialTheme.colorScheme.error
            )
            TvTimeImportUiFailure.NETWORK -> Text(
                stringResource(R.string.tvtime_import_network_failure),
                color = MaterialTheme.colorScheme.error
            )
            TvTimeImportUiFailure.RATE_LIMIT -> Text(
                stringResource(R.string.tvtime_import_rate_limit),
                color = MaterialTheme.colorScheme.error
            )
            else -> Unit
        }
        if (state.failure == TvTimeImportUiFailure.NETWORK ||
            state.failure == TvTimeImportUiFailure.RATE_LIMIT ||
            state.failure == TvTimeImportUiFailure.MISSING_CREDENTIAL
        ) {
            TextButton(onClick = onRetryMatching) { Text(stringResource(R.string.tvtime_import_retry_matching)) }
        }
        Text(
            stringResource(
                R.string.tvtime_import_review_counts,
                reviewState.filterCounts[TvTimeMatchFilter.EXACT] ?: 0,
                reviewState.filterCounts[TvTimeMatchFilter.HIGH_CONFIDENCE] ?: 0,
                reviewState.filterCounts[TvTimeMatchFilter.NEEDS_REVIEW] ?: 0,
                reviewState.filterCounts[TvTimeMatchFilter.UNMATCHED] ?: 0
            )
        )
        Row(modifier = Modifier.horizontalScroll(filterScrollState)) {
            TvTimeMatchFilter.entries.forEach { filter ->
                val count = reviewState.filterCounts[filter] ?: 0
                FilterChip(
                    selected = state.selectedFilter == filter,
                    onClick = { onSetFilter(filter) },
                    label = {
                        Text(
                            stringResource(
                                R.string.tvtime_import_filter_with_count,
                                stringResource(filter.labelRes()),
                                count
                            )
                        )
                    },
                    modifier = Modifier.padding(end = 6.dp)
                )
            }
        }
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextButton(onClick = onAcceptExact) { Text(stringResource(R.string.tvtime_import_accept_exact)) }
            TextButton(onClick = onAcceptHigh) { Text(stringResource(R.string.tvtime_import_accept_high)) }
        }
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(BingeeDimensions.elementSpacing)
        ) {
            items(reviewState.visibleMedia, key = { it.source.recordId }) { review ->
                MediaReviewCard(
                    review,
                    state.manualCandidates[review.source.recordId].orEmpty(),
                    review.source.recordId in state.manualSearchFailures,
                    onSkip,
                    onSelectMediaCandidate,
                    onSearch
                )
            }
            reviewState.visibleEpisodeGroups.forEach { group ->
                item(key = "episode-group:${group.parentRecordId}:${group.seasonNumber}") {
                    EpisodeGroupHeader(
                        seriesTitle = group.seriesTitle,
                        seasonNumber = group.seasonNumber,
                        count = group.reviews.size,
                        onSkip = { onSkipSeason(group.parentRecordId, group.seasonNumber) }
                    )
                }
                items(group.reviews, key = { it.source.recordId }) { review ->
                    EpisodeReviewCard(review, onSkip, onSelectEpisodeCandidate)
                }
            }
            if (reviewState.showInvalidRecordSummary) {
                item(key = "invalid-source-summary") {
                    Text(
                        stringResource(
                            R.string.tvtime_import_invalid_records_summary,
                            state.summary?.invalidRecordCount ?: 0
                        )
                    )
                }
            }
        }
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(onClick = onPreparePreview) { Text(stringResource(R.string.tvtime_import_prepare_preview)) }
            TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
        }
    }
}

@Composable
private fun MediaReviewCard(
    review: TvTimeMediaReview,
    manualCandidates: List<TmdbImportCandidate>,
    manualSearchFailed: Boolean,
    onSkip: (String) -> Unit,
    onSelect: (String, TmdbImportCandidate) -> Unit,
    onSearch: (String, String, MediaType) -> Unit
) {
    var query by remember(review.source.recordId) { mutableStateOf("") }
    val displayedConfidence = if (review.action == TvTimeReviewAction.SKIP) {
        TvTimeMatchConfidence.SKIPPED
    } else {
        review.confidence
    }
    val confidenceLabel = stringResource(displayedConfidence.labelRes())
    val reasonLabel = stringResource(review.reason.labelRes())
    val mediaLabel = stringResource(
        if (review.source.mediaType == MediaType.MOVIE) {
            R.string.tvtime_import_media_movie
        } else {
            R.string.tvtime_import_media_series
        }
    )
    Column(
        modifier = Modifier.fillMaxWidth().padding(8.dp).semantics {
            stateDescription = "$confidenceLabel. $reasonLabel"
        }
    ) {
        if (review.source.year != null) {
            Text(
                stringResource(R.string.tvtime_import_source_title_year, review.source.title, review.source.year),
                style = MaterialTheme.typography.titleMedium
            )
        } else {
            Text(review.source.title, style = MaterialTheme.typography.titleMedium)
        }
        Text("$mediaLabel · $confidenceLabel")
        Text(reasonLabel, style = MaterialTheme.typography.bodySmall)
        if (review.source.warnings.isNotEmpty()) {
            Text(
                stringResource(R.string.tvtime_import_record_warnings, review.source.warnings.size),
                style = MaterialTheme.typography.bodySmall
            )
        }
        review.source.identities.forEach { identity ->
            Text(
                stringResource(R.string.tvtime_import_identity, "${identity.namespace}: ${identity.value}"),
                style = MaterialTheme.typography.bodySmall
            )
        }
        review.effectiveCandidate()?.let { candidate ->
            Text(stringResource(R.string.tvtime_import_candidate, candidate.title, candidate.year ?: "?"))
        }
        review.alternatives.forEach { candidate ->
            TextButton(onClick = { onSelect(review.source.recordId, candidate) }) {
                Text(
                    stringResource(
                        R.string.tvtime_import_candidate_action,
                        candidate.title,
                        candidate.year ?: "?"
                    )
                )
            }
        }
        manualCandidates.forEach { candidate ->
            TextButton(onClick = { onSelect(review.source.recordId, candidate) }) {
                Text(stringResource(R.string.tvtime_import_candidate_action, candidate.title, candidate.year ?: "?"))
            }
        }
        if (manualSearchFailed) {
            Text(
                stringResource(R.string.tvtime_import_manual_search_error),
                color = MaterialTheme.colorScheme.error
            )
        }
        if (review.confidence == TvTimeMatchConfidence.AMBIGUOUS ||
            review.confidence == TvTimeMatchConfidence.UNMATCHED
        ) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                OutlinedTextField(query, {
                    query = it
                }, label = {
                    Text(stringResource(R.string.tvtime_import_search_label))
                }, modifier = Modifier.weight(1f))
                TextButton(onClick = {
                    onSearch(review.source.recordId, query, review.source.mediaType)
                }) { Text(stringResource(R.string.tvtime_import_search)) }
            }
        }
        TextButton(onClick = { onSkip(review.source.recordId) }) {
            Text(
                stringResource(
                    if (review.action == TvTimeReviewAction.SKIP) {
                        R.string.tvtime_import_confidence_skipped
                    } else {
                        R.string.tvtime_import_skip
                    }
                )
            )
        }
    }
}

@Composable
private fun EpisodeGroupHeader(seriesTitle: String, seasonNumber: Int, count: Int, onSkip: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().semantics { heading() }) {
        Text(
            stringResource(R.string.tvtime_import_episode_group, seriesTitle, seasonNumber, count),
            style = MaterialTheme.typography.titleMedium
        )
        TextButton(onClick = onSkip) { Text(stringResource(R.string.tvtime_import_skip_season)) }
    }
}

@Composable
private fun EpisodeReviewCard(
    review: TvTimeEpisodeReview,
    onSkip: (String) -> Unit,
    onSelect: (String, TmdbImportEpisodeCandidate) -> Unit
) {
    val displayedConfidence = if (review.action == TvTimeReviewAction.SKIP) {
        TvTimeMatchConfidence.SKIPPED
    } else {
        review.confidence
    }
    val confidenceLabel = stringResource(displayedConfidence.labelRes())
    val reasonLabel = stringResource(review.reason.labelRes())
    Column(
        modifier = Modifier.fillMaxWidth().padding(8.dp).semantics {
            stateDescription = "$confidenceLabel. $reasonLabel"
        }
    ) {
        Text(
            stringResource(
                R.string.tvtime_import_episode,
                review.source.seasonNumber,
                review.source.episodeNumber,
                review.source.title
            ),
            style = MaterialTheme.typography.titleMedium
        )
        Text(confidenceLabel)
        Text(reasonLabel, style = MaterialTheme.typography.bodySmall)
        if (review.source.warnings.isNotEmpty()) {
            Text(
                stringResource(R.string.tvtime_import_record_warnings, review.source.warnings.size),
                style = MaterialTheme.typography.bodySmall
            )
        }
        review.alternatives.forEach { candidate ->
            TextButton(onClick = { onSelect(review.source.recordId, candidate) }) { Text(candidate.title) }
        }
        TextButton(onClick = { onSkip(review.source.recordId) }) {
            Text(
                stringResource(
                    if (review.action == TvTimeReviewAction.SKIP) {
                        R.string.tvtime_import_confidence_skipped
                    } else {
                        R.string.tvtime_import_skip
                    }
                )
            )
        }
    }
}

@Composable
private fun PreviewContent(state: TvTimeImportUiState, onConfirm: () -> Unit, onCancel: () -> Unit) {
    val preview = state.preview ?: return
    Text(stringResource(R.string.tvtime_import_preview_title), style = MaterialTheme.typography.titleLarge)
    Text(
        stringResource(
            R.string.tvtime_import_preview_counts,
            preview.newLibraryCount,
            preview.existingLibraryCount,
            preview.movieProgressToAdd,
            preview.episodeProgressToAdd
        )
    )
    Text(stringResource(R.string.tvtime_import_preview_conflicts, preview.timestampConflictCount))
    Text(stringResource(R.string.tvtime_import_preview_skipped, preview.skippedCount, preview.invalidRecordCount))
    Text(stringResource(R.string.tvtime_import_preview_approximate, preview.approximatedMembershipCount))
    Text(stringResource(R.string.tvtime_import_limitations))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onConfirm) { Text(stringResource(R.string.tvtime_import_confirm)) }
        TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
    }
}

@Composable
private fun SuccessContent(state: TvTimeImportUiState, onBack: () -> Unit) {
    Text(
        stringResource(R.string.tvtime_import_success),
        modifier = Modifier.semantics {
            liveRegion =
                LiveRegionMode.Polite
        }
    )
    state.result?.let { result ->
        Text(
            stringResource(
                R.string.tvtime_import_success_library,
                result.newLibraryTitles.size,
                result.alreadyPresentTitles.size
            )
        )
        Text(
            stringResource(
                R.string.tvtime_import_success_progress,
                result.movieProgressAdded,
                result.episodeProgressAdded
            )
        )
        Text(
            stringResource(
                R.string.tvtime_import_success_preserved,
                result.movieProgressPreserved + result.episodeProgressPreserved,
                result.timestampConflicts.size
            )
        )
        Text(
            stringResource(
                R.string.tvtime_import_success_skipped,
                result.skippedRecordIds.size,
                result.invalidRecordCount
            )
        )
        Text(stringResource(R.string.tvtime_import_success_unmatched, result.unmatchedRecordIds.size))
        Text(stringResource(R.string.tvtime_import_success_warnings, result.warnings.size))
        Text(stringResource(R.string.tvtime_import_success_approximate, result.approximatedMembershipTitles.size))
    }
    Button(onClick = onBack) { Text(stringResource(R.string.action_dismiss)) }
}

@Composable
private fun FailureContent(state: TvTimeImportUiState, onSelectArchive: () -> Unit, onBack: () -> Unit) {
    Text(
        text = when (state.failure) {
            TvTimeImportUiFailure.UNREADABLE_ZIP -> stringResource(R.string.tvtime_import_archive_error)
            TvTimeImportUiFailure.UNSAFE_ZIP -> stringResource(R.string.tvtime_import_unsafe_archive_error)
            TvTimeImportUiFailure.ENCRYPTED_ZIP -> stringResource(R.string.tvtime_import_encrypted_archive_error)
            TvTimeImportUiFailure.UNSUPPORTED_ARCHIVE_LAYOUT ->
                stringResource(R.string.tvtime_import_archive_layout_error)
            TvTimeImportUiFailure.UNSUPPORTED_PROFILE -> stringResource(R.string.tvtime_import_profile_error)
            TvTimeImportUiFailure.INVALID_SOURCE -> stringResource(R.string.tvtime_import_source_error)
            TvTimeImportUiFailure.TRANSACTION -> stringResource(R.string.tvtime_import_transaction_failure)
            else -> stringResource(R.string.tvtime_import_invalid)
        },
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onSelectArchive) { Text(stringResource(R.string.tvtime_import_select_archive)) }
        TextButton(onClick = onBack) { Text(stringResource(R.string.action_cancel)) }
    }
}

private fun TvTimeMatchFilter.labelRes(): Int = when (this) {
    TvTimeMatchFilter.ALL -> R.string.tvtime_import_filter_all
    TvTimeMatchFilter.EXACT -> R.string.tvtime_import_filter_exact
    TvTimeMatchFilter.HIGH_CONFIDENCE -> R.string.tvtime_import_filter_high
    TvTimeMatchFilter.NEEDS_REVIEW -> R.string.tvtime_import_filter_review
    TvTimeMatchFilter.UNMATCHED -> R.string.tvtime_import_filter_unmatched
    TvTimeMatchFilter.INVALID -> R.string.tvtime_import_filter_invalid
    TvTimeMatchFilter.SKIPPED -> R.string.tvtime_import_filter_skipped
}

private fun TvTimeMatchConfidence.labelRes(): Int = when (this) {
    TvTimeMatchConfidence.EXACT -> R.string.tvtime_import_confidence_exact
    TvTimeMatchConfidence.HIGH_CONFIDENCE -> R.string.tvtime_import_confidence_high
    TvTimeMatchConfidence.AMBIGUOUS -> R.string.tvtime_import_confidence_ambiguous
    TvTimeMatchConfidence.UNMATCHED -> R.string.tvtime_import_confidence_unmatched
    TvTimeMatchConfidence.INVALID -> R.string.tvtime_import_confidence_invalid
    TvTimeMatchConfidence.SKIPPED -> R.string.tvtime_import_confidence_skipped
}

private fun com.cydoniancitizen.bingee.data.imports.tvtime.TvTimeMatchReason.labelRes(): Int = when (this) {
    com.cydoniancitizen.bingee.data.imports.tvtime.TvTimeMatchReason.EXACT_EXTERNAL_ID -> R.string.tvtime_import_reason_exact_external_id
    com.cydoniancitizen.bingee.data.imports.tvtime.TvTimeMatchReason.EXACT_EPISODE_ID -> R.string.tvtime_import_reason_exact_episode_id
    com.cydoniancitizen.bingee.data.imports.tvtime.TvTimeMatchReason.EXACT_NUMBERING -> R.string.tvtime_import_reason_exact_numbering
    com.cydoniancitizen.bingee.data.imports.tvtime.TvTimeMatchReason.TITLE_AND_YEAR_UNIQUE -> R.string.tvtime_import_reason_title_year
    com.cydoniancitizen.bingee.data.imports.tvtime.TvTimeMatchReason.SERIES_ID_REQUIRED -> R.string.tvtime_import_reason_series_id
    com.cydoniancitizen.bingee.data.imports.tvtime.TvTimeMatchReason.CONFLICTING_EXTERNAL_IDS -> R.string.tvtime_import_reason_conflicting_ids
    com.cydoniancitizen.bingee.data.imports.tvtime.TvTimeMatchReason.MULTIPLE_CANDIDATES -> R.string.tvtime_import_reason_multiple
    com.cydoniancitizen.bingee.data.imports.tvtime.TvTimeMatchReason.NO_CANDIDATE -> R.string.tvtime_import_reason_none
    com.cydoniancitizen.bingee.data.imports.tvtime.TvTimeMatchReason.MEDIA_TYPE_MISMATCH -> R.string.tvtime_import_reason_type
    com.cydoniancitizen.bingee.data.imports.tvtime.TvTimeMatchReason.MISSING_PARENT -> R.string.tvtime_import_reason_parent
    com.cydoniancitizen.bingee.data.imports.tvtime.TvTimeMatchReason.MISSING_SEASON -> R.string.tvtime_import_reason_season
    com.cydoniancitizen.bingee.data.imports.tvtime.TvTimeMatchReason.MISSING_EPISODE -> R.string.tvtime_import_reason_episode
    com.cydoniancitizen.bingee.data.imports.tvtime.TvTimeMatchReason.SPECIAL_REQUIRES_REVIEW -> R.string.tvtime_import_reason_special
    com.cydoniancitizen.bingee.data.imports.tvtime.TvTimeMatchReason.PROVIDER_ERROR -> R.string.tvtime_import_reason_provider
    com.cydoniancitizen.bingee.data.imports.tvtime.TvTimeMatchReason.INVALID_SOURCE -> R.string.tvtime_import_reason_invalid
}
