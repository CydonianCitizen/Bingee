package com.cydoniancitizen.bingee.feature.profile

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cydoniancitizen.bingee.R
import com.cydoniancitizen.bingee.core.designsystem.component.ErrorState
import com.cydoniancitizen.bingee.core.designsystem.component.LoadingState
import com.cydoniancitizen.bingee.core.designsystem.component.MediaPoster
import com.cydoniancitizen.bingee.core.designsystem.theme.BingeeDimensions
import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.LibraryEntry
import com.cydoniancitizen.bingee.core.model.LibraryProgress
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.core.model.MovieWatchState
import com.cydoniancitizen.bingee.core.model.isWatched
import com.cydoniancitizen.bingee.core.ui.toUiError
import com.cydoniancitizen.bingee.data.settings.ProfileCategory
import com.cydoniancitizen.bingee.data.settings.ProfileCollection
import com.cydoniancitizen.bingee.data.settings.ProfileViewMode
import com.cydoniancitizen.bingee.feature.details.WatchedDateDialog

@Composable
internal fun ProfileScreen(
    onOpenSettings: () -> Unit,
    onOpenDetails: (ExternalMediaRef, MediaType) -> Unit,
    modifier: Modifier = Modifier,
    onOpenStatistics: () -> Unit = {},
    onNavigateToSearch: () -> Unit = {},
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ProfileContent(
        state = state,
        onCollectionSelected = viewModel::setCollection,
        onCategorySelected = viewModel::setCategory,
        onSortSelected = viewModel::setSortOption,
        onViewModeSelected = viewModel::setViewMode,
        onSearchQueryChanged = viewModel::onSearchQueryChanged,
        onClearSearch = viewModel::clearSearch,
        onRemove = viewModel::remove,
        onToggleFavorite = viewModel::toggleFavorite,
        onSetWatchedDate = viewModel::setWatchedDate,
        onOpenSettings = onOpenSettings,
        onOpenStatistics = onOpenStatistics,
        onOpenDetails = onOpenDetails,
        onNavigateToSearch = onNavigateToSearch,
        onDismissActionError = viewModel::clearActionError,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProfileContent(
    state: ProfileUiState,
    onCollectionSelected: (ProfileCollection) -> Unit,
    onCategorySelected: (ProfileCategory) -> Unit,
    onSortSelected: (ProfileSortOption) -> Unit,
    onViewModeSelected: (ProfileViewMode) -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onClearSearch: () -> Unit,
    onRemove: (LibraryEntry) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDetails: (ExternalMediaRef, MediaType) -> Unit,
    onNavigateToSearch: () -> Unit,
    onDismissActionError: () -> Unit,
    modifier: Modifier = Modifier,
    onToggleFavorite: (LibraryEntry) -> Unit = {},
    onSetWatchedDate: (LibraryEntry, java.time.LocalDate?) -> Unit = { _, _ -> },
    onOpenStatistics: () -> Unit = {}
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(BingeeDimensions.screenPadding),
        verticalArrangement = Arrangement.spacedBy(BingeeDimensions.elementSpacing)
    ) {
        // App Bar Title + Statistics Icon + Settings Icon
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.profile_title),
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() },
                style = MaterialTheme.typography.headlineMedium
            )
            IconButton(onClick = onOpenStatistics) {
                Icon(
                    imageVector = Icons.Default.DateRange,
                    contentDescription = stringResource(R.string.statistics_title)
                )
            }
            IconButton(onClick = onOpenSettings) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = stringResource(R.string.nav_settings)
                )
            }
        }

        // Local Profile search field
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = onSearchQueryChanged,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.profile_search_placeholder)) },
            singleLine = true,
            shape = CircleShape,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
            ),
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null
                )
            },
            trailingIcon = if (state.searchQuery.isNotEmpty()) {
                {
                    IconButton(onClick = onClearSearch) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.library_search_clear)
                        )
                    }
                }
            } else {
                null
            }
        )

        // Horizontally scrollable filter chips + Sort action + Display mode toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Collection Chips
                FilterChip(
                    selected = state.collection == ProfileCollection.WATCHED,
                    onClick = { onCollectionSelected(ProfileCollection.WATCHED) },
                    label = { Text(stringResource(R.string.profile_tab_watched)) },
                    leadingIcon = if (state.collection == ProfileCollection.WATCHED) {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else {
                        null
                    }
                )
                FilterChip(
                    selected = state.collection == ProfileCollection.WATCH_LATER,
                    onClick = { onCollectionSelected(ProfileCollection.WATCH_LATER) },
                    label = { Text(stringResource(R.string.profile_tab_watch_later)) },
                    leadingIcon = if (state.collection == ProfileCollection.WATCH_LATER) {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else {
                        null
                    }
                )
                FilterChip(
                    selected = state.collection == ProfileCollection.FAVORITES,
                    onClick = { onCollectionSelected(ProfileCollection.FAVORITES) },
                    label = { Text(stringResource(R.string.profile_tab_favorites)) },
                    leadingIcon = if (state.collection == ProfileCollection.FAVORITES) {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else {
                        null
                    }
                )
                // Category Chips
                FilterChip(
                    selected = state.category == ProfileCategory.MOVIES,
                    onClick = { onCategorySelected(ProfileCategory.MOVIES) },
                    label = { Text(stringResource(R.string.profile_tab_movies)) }
                )
                FilterChip(
                    selected = state.category == ProfileCategory.TV_SERIES,
                    onClick = { onCategorySelected(ProfileCategory.TV_SERIES) },
                    label = { Text(stringResource(R.string.profile_tab_tv_series)) }
                )

                // Sort Action Dropdown
                var showSortMenu by remember { mutableStateOf(false) }
                Box {
                    FilterChip(
                        selected = false,
                        onClick = { showSortMenu = true },
                        label = { Text(stringResource(R.string.profile_sort_label, state.sortOption.labelString())) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = stringResource(R.string.library_sort),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false }
                    ) {
                        ProfileSortOption.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.labelString()) },
                                onClick = {
                                    onSortSelected(option)
                                    showSortMenu = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Display-mode Toggle
            IconButton(
                onClick = {
                    val nextMode = if (state.currentViewMode == ProfileViewMode.LIST) {
                        ProfileViewMode.GRID
                    } else {
                        ProfileViewMode.LIST
                    }
                    onViewModeSelected(nextMode)
                }
            ) {
                Icon(
                    imageVector = if (state.currentViewMode == ProfileViewMode.LIST) {
                        Icons.Default.Menu
                    } else {
                        Icons.AutoMirrored.Filled.List
                    },
                    contentDescription = stringResource(
                        if (state.currentViewMode == ProfileViewMode.LIST) {
                            R.string.profile_view_grid
                        } else {
                            R.string.profile_view_list
                        }
                    )
                )
            }
        }

        // Action error banner
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

        // Main Content Area
        when {
            state.isLoading -> LoadingState(stringResource(R.string.library_loading))
            state.loadError != null -> {
                val uiError = state.loadError.toUiError()
                ErrorState(
                    title = stringResource(R.string.library_error_title),
                    message = stringResource(uiError.messageRes)
                )
            }
            state.entries.isEmpty() -> {
                ProfileEmptyState(
                    state = state,
                    onClearSearch = onClearSearch,
                    onNavigateToSearch = onNavigateToSearch
                )
            }
            state.currentViewMode == ProfileViewMode.GRID -> {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 140.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = BingeeDimensions.elementSpacing),
                    horizontalArrangement = Arrangement.spacedBy(BingeeDimensions.elementSpacing),
                    verticalArrangement = Arrangement.spacedBy(BingeeDimensions.elementSpacing)
                ) {
                    items(
                        items = state.entries,
                        key = { "${it.mediaRef.source}:${it.mediaRef.externalId}" }
                    ) { entry ->
                        ProfileGridItem(
                            entry = entry,
                            onOpenDetails = { onOpenDetails(entry.mediaRef, entry.mediaType) },
                            onToggleFavorite = onToggleFavorite,
                            onSetWatchedDate = onSetWatchedDate
                        )
                    }
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(BingeeDimensions.elementSpacing)
                ) {
                    items(
                        items = state.entries,
                        key = { "${it.mediaRef.source}:${it.mediaRef.externalId}" }
                    ) { entry ->
                        ProfileListItem(
                            entry = entry,
                            isRemoving = entry.mediaRef in state.pendingRemovals,
                            onRemove = { onRemove(entry) },
                            onOpenDetails = { onOpenDetails(entry.mediaRef, entry.mediaType) },
                            onToggleFavorite = onToggleFavorite,
                            onSetWatchedDate = onSetWatchedDate
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileEmptyState(
    state: ProfileUiState,
    onClearSearch: () -> Unit,
    onNavigateToSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isSearching = state.searchQuery.isNotBlank()
    val icon: ImageVector = if (isSearching) Icons.Default.Search else Icons.Default.Star
    val title: String
    val body: String

    if (isSearching) {
        title = stringResource(R.string.profile_empty_search_title)
        body = stringResource(R.string.profile_empty_search_body, state.searchQuery)
    } else {
        when {
            state.collection == ProfileCollection.WATCHED && state.category == ProfileCategory.MOVIES -> {
                title = stringResource(R.string.profile_empty_watched_movies_title)
                body = stringResource(R.string.profile_empty_watched_movies_body)
            }
            state.collection == ProfileCollection.WATCHED && state.category == ProfileCategory.TV_SERIES -> {
                title = stringResource(R.string.profile_empty_watched_tv_title)
                body = stringResource(R.string.profile_empty_watched_tv_body)
            }
            state.collection == ProfileCollection.WATCH_LATER && state.category == ProfileCategory.MOVIES -> {
                title = stringResource(R.string.profile_empty_watch_later_movies_title)
                body = stringResource(R.string.profile_empty_watch_later_movies_body)
            }
            state.collection == ProfileCollection.FAVORITES && state.category == ProfileCategory.MOVIES -> {
                title = stringResource(R.string.profile_empty_favorites_movies_title)
                body = stringResource(R.string.profile_empty_favorites_movies_body)
            }
            state.collection == ProfileCollection.FAVORITES && state.category == ProfileCategory.TV_SERIES -> {
                title = stringResource(R.string.profile_empty_favorites_tv_title)
                body = stringResource(R.string.profile_empty_favorites_tv_body)
            }
            else -> {
                title = stringResource(R.string.profile_empty_watch_later_tv_title)
                body = stringResource(R.string.profile_empty_watch_later_tv_body)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(72.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(36.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        if (isSearching) {
            Button(onClick = onClearSearch) {
                Text(stringResource(R.string.profile_empty_action_clear))
            }
        } else {
            Button(onClick = onNavigateToSearch) {
                Text(stringResource(R.string.profile_empty_action_search))
            }
        }
    }
}

@Composable
private fun ProfileGridItem(
    entry: LibraryEntry,
    onOpenDetails: () -> Unit,
    onToggleFavorite: (LibraryEntry) -> Unit = {},
    onSetWatchedDate: (LibraryEntry, java.time.LocalDate?) -> Unit = { _, _ -> }
) {
    val openDetailsDescription = stringResource(R.string.open_details, entry.title)
    var showMenu by remember { mutableStateOf(false) }
    var showWatchedDateDialog by remember { mutableStateOf(false) }

    Card(
        onClick = onOpenDetails,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = openDetailsDescription }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Box {
                MediaPoster(
                    title = entry.title,
                    posterUrl = entry.posterUrl,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.67f)
                )
                IconButton(
                    onClick = { onToggleFavorite(entry) },
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Icon(
                        imageVector = if (entry.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = stringResource(
                            if (entry.isFavorite) R.string.favorite_remove else R.string.favorite_add
                        ),
                        tint = if (entry.isFavorite) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = entry.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.MoreVert, contentDescription = null)
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(
                                        if (entry.isFavorite) R.string.favorite_remove else R.string.favorite_add
                                    )
                                )
                            },
                            onClick = {
                                showMenu = false
                                onToggleFavorite(entry)
                            }
                        )
                        if (entry.isWatched()) {
                            val editRes = if (entry.mediaType ==
                                MediaType.MOVIE
                            ) {
                                R.string.watched_date_edit
                            } else {
                                R.string.completion_date_edit
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(editRes)) },
                                onClick = {
                                    showMenu = false
                                    showWatchedDateDialog = true
                                }
                            )
                        }
                    }
                }

                val subtitleText = when (val p = entry.progress) {
                    is LibraryProgress.Movie -> entry.releaseDate?.year?.toString()
                    is LibraryProgress.Series -> stringResource(
                        R.string.library_progress_episodes,
                        p.progress.watchedEpisodes,
                        p.progress.trackableEpisodes
                    )
                    LibraryProgress.Unavailable -> entry.releaseDate?.year?.toString()
                }

                subtitleText?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                entry.personalRating?.let { rating ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "${rating.value}/10",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }

                // TV Progress Indicator when applicable
                when (val p = entry.progress) {
                    is LibraryProgress.Series -> {
                        if (p.progress.trackableEpisodes > 0) {
                            val fraction = p.progress.watchedEpisodes.toFloat() / p.progress.trackableEpisodes
                            Spacer(modifier = Modifier.height(2.dp))
                            LinearProgressIndicator(
                                progress = { fraction.coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                            )
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    if (showWatchedDateDialog) {
        WatchedDateDialog(
            currentDate = entry.watchedDate,
            releaseDate = entry.releaseDate,
            mediaType = entry.mediaType,
            onConfirm = { date ->
                onSetWatchedDate(entry, date)
                showWatchedDateDialog = false
            },
            onDismiss = { showWatchedDateDialog = false }
        )
    }
}

@Composable
private fun ProfileListItem(
    entry: LibraryEntry,
    isRemoving: Boolean,
    onRemove: () -> Unit,
    onOpenDetails: () -> Unit,
    onToggleFavorite: (LibraryEntry) -> Unit = {},
    onSetWatchedDate: (LibraryEntry, java.time.LocalDate?) -> Unit = { _, _ -> }
) {
    val openDetailsDescription = stringResource(R.string.open_details, entry.title)
    var showWatchedDateDialog by remember { mutableStateOf(false) }

    Card(
        onClick = onOpenDetails,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = openDetailsDescription }
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(entry.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    IconButton(onClick = { onToggleFavorite(entry) }) {
                        Icon(
                            imageVector = if (entry.isFavorite) {
                                Icons.Filled.Favorite
                            } else {
                                Icons.Outlined.FavoriteBorder
                            },
                            contentDescription = stringResource(
                                if (entry.isFavorite) R.string.favorite_remove else R.string.favorite_add
                            ),
                            tint = if (entry.isFavorite) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Text(
                    stringResource(
                        when (entry.mediaType) {
                            MediaType.MOVIE -> R.string.library_type_movie
                            MediaType.SERIES -> R.string.library_type_tv
                        }
                    )
                )
                entry.releaseDate?.let { Text(stringResource(R.string.search_release_year, it.year)) }
                Text(entry.progress.displayText(), style = MaterialTheme.typography.labelLarge)
                entry.watchedDate?.let {
                    val label = if (entry.mediaType ==
                        MediaType.MOVIE
                    ) {
                        R.string.watched_date_label
                    } else {
                        R.string.completion_date_label
                    }
                    Text("${stringResource(label)}: $it", style = MaterialTheme.typography.bodySmall)
                }
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onRemove, enabled = !isRemoving) {
                        Text(
                            stringResource(
                                if (isRemoving) R.string.library_action_updating else R.string.library_action_remove
                            )
                        )
                    }
                    if (entry.isWatched()) {
                        val editRes = if (entry.mediaType ==
                            MediaType.MOVIE
                        ) {
                            R.string.watched_date_edit
                        } else {
                            R.string.completion_date_edit
                        }
                        TextButton(onClick = { showWatchedDateDialog = true }) {
                            Text(stringResource(editRes))
                        }
                    }
                }
            }
        }
    }

    if (showWatchedDateDialog) {
        WatchedDateDialog(
            currentDate = entry.watchedDate,
            releaseDate = entry.releaseDate,
            mediaType = entry.mediaType,
            onConfirm = { date ->
                onSetWatchedDate(entry, date)
                showWatchedDateDialog = false
            },
            onDismiss = { showWatchedDateDialog = false }
        )
    }
}

@Composable
private fun ProfileSortOption.labelString(): String = when (this) {
    ProfileSortOption.RECENTLY_ADDED -> stringResource(R.string.library_sort_recent)
    ProfileSortOption.TITLE -> stringResource(R.string.library_sort_title)
    ProfileSortOption.RATING -> stringResource(R.string.library_sort_rating)
    ProfileSortOption.PROGRESS -> stringResource(R.string.library_sort_progress)
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
