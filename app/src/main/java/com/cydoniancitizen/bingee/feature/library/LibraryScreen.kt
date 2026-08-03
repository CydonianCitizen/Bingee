package com.cydoniancitizen.bingee.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.cydoniancitizen.bingee.core.model.LibraryMediaFilter
import com.cydoniancitizen.bingee.core.model.LibraryProgress
import com.cydoniancitizen.bingee.core.model.LibraryQuery
import com.cydoniancitizen.bingee.core.model.LibrarySort
import com.cydoniancitizen.bingee.core.model.LibraryStateFilter
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
        onSearchQueryChanged = viewModel::onSearchQueryChanged,
        onClearSearch = viewModel::clearSearch,
        onMediaFilterChanged = viewModel::onMediaFilterChanged,
        onStateFilterChanged = viewModel::onStateFilterChanged,
        onSortChanged = viewModel::onSortChanged,
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
    onSearchQueryChanged: (String) -> Unit,
    onClearSearch: () -> Unit,
    onMediaFilterChanged: (LibraryMediaFilter) -> Unit,
    onStateFilterChanged: (LibraryStateFilter) -> Unit,
    onSortChanged: (LibrarySort) -> Unit,
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
        LibraryControls(
            query = state.query,
            onSearchQueryChanged = onSearchQueryChanged,
            onClearSearch = onClearSearch,
            onMediaFilterChanged = onMediaFilterChanged,
            onStateFilterChanged = onStateFilterChanged,
            onSortChanged = onSortChanged
        )
        if (state.query.hasActiveFilters) {
            Text(
                text = stringResource(R.string.library_filters_active),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge
            )
        }
        state.actionError?.let { error ->
            val uiError = error.toUiError()
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
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
            LibraryContentState.Loading -> LoadingState(stringResource(R.string.library_loading))
            LibraryContentState.Empty -> EmptyState(
                title = stringResource(R.string.library_empty_title),
                body = stringResource(R.string.library_empty_body)
            )
            LibraryContentState.NoResults -> EmptyState(
                title = stringResource(R.string.library_no_results_title),
                body = stringResource(R.string.library_no_results_body)
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
            is LibraryContentState.Entries -> {
                Text(stringResource(R.string.library_result_count, state.resultCount))
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryControls(
    query: LibraryQuery,
    onSearchQueryChanged: (String) -> Unit,
    onClearSearch: () -> Unit,
    onMediaFilterChanged: (LibraryMediaFilter) -> Unit,
    onStateFilterChanged: (LibraryStateFilter) -> Unit,
    onSortChanged: (LibrarySort) -> Unit
) {
    OutlinedTextField(
        value = query.searchQuery,
        onValueChange = onSearchQueryChanged,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.library_search_label)) },
        singleLine = true,
        trailingIcon = if (query.searchQuery.isNotEmpty()) {
            {
                IconButton(onClick = onClearSearch) {
                    Icon(Icons.Default.Close, stringResource(R.string.library_search_clear))
                }
            }
        } else {
            null
        }
    )
    val mediaFilters = LibraryMediaFilter.entries
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        mediaFilters.forEachIndexed { index, filter ->
            SegmentedButton(
                selected = query.mediaFilter == filter,
                onClick = { onMediaFilterChanged(filter) },
                shape = SegmentedButtonDefaults.itemShape(index, mediaFilters.size),
                label = { Text(stringResource(filter.labelRes())) }
            )
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(BingeeDimensions.elementSpacing)
    ) {
        FilterMenu(
            label = stringResource(R.string.library_state_filter),
            selectedLabel = stringResource(query.stateFilter.labelRes(query.mediaFilter)),
            options = availableStateFilters(query.mediaFilter),
            optionLabel = { stringResource(it.labelRes(query.mediaFilter)) },
            onSelected = onStateFilterChanged,
            modifier = Modifier.weight(1f)
        )
        FilterMenu(
            label = stringResource(R.string.library_sort),
            selectedLabel = stringResource(query.sort.labelRes()),
            options = LibrarySort.entries,
            optionLabel = { stringResource(it.labelRes()) },
            onSelected = onSortChanged,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun <T> FilterMenu(
    label: String,
    selectedLabel: String,
    options: List<T>,
    optionLabel: @Composable (T) -> String,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(selectedLabel, modifier = Modifier.weight(1f), maxLines = 1)
            Icon(Icons.Default.ArrowDropDown, null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        expanded = false
                        onSelected(option)
                    }
                )
            }
        }
    }
}

@Composable
private fun LibraryItem(entry: LibraryEntry, isRemoving: Boolean, onRemove: () -> Unit, onOpenDetails: () -> Unit) {
    val openDetailsDescription = stringResource(R.string.open_details, entry.title)
    Card(
        onClick = onOpenDetails,
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = openDetailsDescription }
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
                        if (entry.mediaType ==
                            MediaType.MOVIE
                        ) {
                            R.string.library_type_movie
                        } else {
                            R.string.library_type_tv
                        }
                    )
                )
                entry.releaseDate?.let { Text(stringResource(R.string.search_release_year, it.year)) }
                Text(entry.progress.displayText(), style = MaterialTheme.typography.labelLarge)
                val ratingText = entry.personalRating?.let {
                    stringResource(R.string.library_rating_value, it.value)
                } ?: stringResource(R.string.library_rating_unrated)
                Text(
                    text = ratingText,
                    modifier = Modifier.semantics { contentDescription = ratingText },
                    style = MaterialTheme.typography.labelLarge
                )
                entry.overview?.let {
                    Text(
                        text = it,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Button(onClick = onRemove, enabled = !isRemoving) {
                    Text(
                        stringResource(
                            if (isRemoving) R.string.library_action_updating else R.string.library_action_remove
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryProgress.displayText(): String = when (this) {
    LibraryProgress.Unavailable -> stringResource(R.string.library_progress_unavailable)
    is LibraryProgress.Movie -> stringResource(
        if (state is MovieWatchState.Watched) R.string.library_progress_watched else R.string.library_progress_unwatched
    )
    is LibraryProgress.Series -> stringResource(
        R.string.library_progress_episodes,
        progress.watchedEpisodes,
        progress.trackableEpisodes
    )
}

private val LibraryQuery.hasActiveFilters: Boolean get() =
    searchQuery.isNotBlank() || mediaFilter != LibraryMediaFilter.ALL || stateFilter != LibraryStateFilter.ALL

private fun availableStateFilters(mediaFilter: LibraryMediaFilter): List<LibraryStateFilter> =
    if (mediaFilter == LibraryMediaFilter.MOVIES) {
        listOf(LibraryStateFilter.ALL, LibraryStateFilter.NOT_STARTED, LibraryStateFilter.COMPLETED)
    } else {
        LibraryStateFilter.entries
    }

private fun LibraryMediaFilter.labelRes(): Int = when (this) {
    LibraryMediaFilter.ALL -> R.string.library_filter_all
    LibraryMediaFilter.MOVIES -> R.string.library_filter_movies
    LibraryMediaFilter.TV_SERIES -> R.string.library_filter_tv
}

private fun LibraryStateFilter.labelRes(mediaFilter: LibraryMediaFilter): Int = when (this) {
    LibraryStateFilter.ALL -> R.string.library_state_all
    LibraryStateFilter.NOT_STARTED ->
        if (mediaFilter ==
            LibraryMediaFilter.MOVIES
        ) {
            R.string.library_progress_unwatched
        } else {
            R.string.library_state_not_started
        }
    LibraryStateFilter.IN_PROGRESS -> R.string.library_state_in_progress
    LibraryStateFilter.COMPLETED ->
        if (mediaFilter ==
            LibraryMediaFilter.MOVIES
        ) {
            R.string.library_progress_watched
        } else {
            R.string.library_state_completed
        }
    LibraryStateFilter.PROGRESS_UNAVAILABLE -> R.string.library_progress_unavailable
}

private fun LibrarySort.labelRes(): Int = when (this) {
    LibrarySort.RECENTLY_ADDED -> R.string.library_sort_recent
    LibrarySort.TITLE -> R.string.library_sort_title
    LibrarySort.PROGRESS -> R.string.library_sort_progress
    LibrarySort.PERSONAL_RATING -> R.string.library_sort_rating
}
