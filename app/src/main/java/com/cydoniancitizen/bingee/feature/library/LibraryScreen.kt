package com.cydoniancitizen.bingee.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cydoniancitizen.bingee.R
import com.cydoniancitizen.bingee.core.designsystem.component.EmptyState
import com.cydoniancitizen.bingee.core.designsystem.component.ErrorState
import com.cydoniancitizen.bingee.core.designsystem.component.LoadingState
import com.cydoniancitizen.bingee.core.designsystem.component.MediaPoster
import com.cydoniancitizen.bingee.core.designsystem.theme.BingeeDimensions
import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.LibraryEntry
import com.cydoniancitizen.bingee.core.model.LibraryProgress
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.core.model.MovieWatchState
import com.cydoniancitizen.bingee.core.ui.toUiError

@Composable
internal fun LibraryScreen(
    onOpenDetails: (ExternalMediaRef, MediaType) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LibraryContent(
        state = state,
        onFilterChanged = viewModel::onFilterChanged,
        onRetry = viewModel::retry,
        onRemove = viewModel::remove,
        onOpenDetails = onOpenDetails,
        onDismissActionError = viewModel::clearActionError,
        modifier = modifier
    )
}

@Composable
internal fun LibraryContent(
    state: LibraryUiState,
    onFilterChanged: (LibraryFilter) -> Unit,
    onRetry: () -> Unit,
    onRemove: (LibraryEntry) -> Unit,
    onOpenDetails: (ExternalMediaRef, MediaType) -> Unit = { _, _ -> },
    onDismissActionError: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize().padding(BingeeDimensions.screenPadding),
        verticalArrangement = Arrangement.spacedBy(BingeeDimensions.contentSpacing)
    ) {
        Text(
            text = stringResource(R.string.library_title),
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.headlineMedium
        )
        LibraryFilters(state.filter, onFilterChanged)
        state.actionError?.let { error ->
            val uiError = error.toUiError()
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(uiError.messageRes),
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.error
                )
                TextButton(onClick = onDismissActionError) {
                    Text(stringResource(R.string.action_dismiss))
                }
            }
        }
        when (val content = state.content) {
            LibraryContentState.Loading ->
                LoadingState(stringResource(R.string.library_loading))

            LibraryContentState.Empty ->
                EmptyState(
                    title = stringResource(R.string.library_empty_title),
                    body = stringResource(R.string.library_empty_body)
                )

            is LibraryContentState.Error -> {
                val uiError = content.error.toUiError()
                ErrorState(
                    title = stringResource(R.string.library_error_title),
                    message = stringResource(uiError.messageRes),
                    retryLabel = stringResource(R.string.action_retry),
                    onRetry = onRetry
                )
            }

            is LibraryContentState.Entries ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(BingeeDimensions.elementSpacing)
                ) {
                    items(
                        items = content.items,
                        key = { "${it.mediaRef.source}:${it.mediaRef.externalId}" }
                    ) { entry ->
                        LibraryItem(
                            entry = entry,
                            isRemoving = entry.mediaRef in state.pendingRemovals,
                            onRemove = { onRemove(entry) },
                            onOpenDetails = { onOpenDetails(entry.mediaRef, entry.mediaType) }
                        )
                    }
                }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryFilters(selected: LibraryFilter, onSelected: (LibraryFilter) -> Unit) {
    val filters = LibraryFilter.entries
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        filters.forEachIndexed { index, filter ->
            SegmentedButton(
                selected = selected == filter,
                onClick = { onSelected(filter) },
                shape = SegmentedButtonDefaults.itemShape(index, filters.size),
                label = {
                    Text(
                        stringResource(
                            when (filter) {
                                LibraryFilter.ALL -> R.string.library_filter_all
                                LibraryFilter.MOVIES -> R.string.library_filter_movies
                                LibraryFilter.TV_SERIES -> R.string.library_filter_tv
                            }
                        )
                    )
                }
            )
        }
    }
}

@Composable
private fun LibraryItem(entry: LibraryEntry, isRemoving: Boolean, onRemove: () -> Unit, onOpenDetails: () -> Unit) {
    val openDetailsDescription = stringResource(R.string.open_details, entry.title)
    Card(
        onClick = onOpenDetails,
        modifier = Modifier.fillMaxWidth().semantics {
            contentDescription = openDetailsDescription
        }
    ) {
        Row(
            modifier = Modifier.padding(BingeeDimensions.elementSpacing),
            horizontalArrangement = Arrangement.spacedBy(BingeeDimensions.contentSpacing)
        ) {
            MediaPoster(title = entry.title, posterUrl = entry.posterUrl)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(BingeeDimensions.elementSpacing)
            ) {
                Text(entry.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(
                        if (entry.mediaType == MediaType.MOVIE) {
                            R.string.library_type_movie
                        } else {
                            R.string.library_type_tv
                        }
                    ),
                    style = MaterialTheme.typography.labelLarge
                )
                entry.releaseDate?.let {
                    Text(
                        stringResource(R.string.search_release_year, it.year),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                Text(
                    text = when (val progress = entry.progress) {
                        LibraryProgress.Unavailable -> stringResource(R.string.library_progress_unavailable)
                        is LibraryProgress.Movie -> stringResource(
                            if (progress.state is MovieWatchState.Watched) {
                                R.string.library_progress_watched
                            } else {
                                R.string.library_progress_unwatched
                            }
                        )
                        is LibraryProgress.Series -> stringResource(
                            R.string.library_progress_episodes,
                            progress.progress.watchedEpisodes,
                            progress.progress.trackableEpisodes
                        )
                    },
                    style = MaterialTheme.typography.labelLarge
                )
                entry.overview?.let {
                    Text(
                        text = it,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Button(onClick = onRemove, enabled = !isRemoving) {
                    Text(
                        stringResource(
                            if (isRemoving) {
                                R.string.library_action_updating
                            } else {
                                R.string.library_action_remove
                            }
                        )
                    )
                }
            }
        }
    }
}
