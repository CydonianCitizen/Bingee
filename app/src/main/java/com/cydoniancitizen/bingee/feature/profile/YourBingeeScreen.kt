package com.cydoniancitizen.bingee.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cydoniancitizen.bingee.R
import com.cydoniancitizen.bingee.core.designsystem.component.ErrorState
import com.cydoniancitizen.bingee.core.designsystem.component.LoadingState
import com.cydoniancitizen.bingee.core.designsystem.component.MediaPoster
import com.cydoniancitizen.bingee.core.designsystem.theme.BingeeDimensions
import com.cydoniancitizen.bingee.core.designsystem.theme.BingeeStatusColors
import com.cydoniancitizen.bingee.core.model.ContinueWatchingItem
import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.LibraryEntry
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.core.ui.toUiError

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun YourBingeeContent(
    state: ProfileUiState,
    onOpenSettings: () -> Unit,
    onOpenDetails: (ExternalMediaRef, MediaType) -> Unit,
    onOpenCollection: (ProfileCollectionShortcut) -> Unit,
    onNavigateToSearch: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.profile_title_dashboard),
                        modifier = Modifier.semantics { heading() }
                    )
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.nav_settings)
                        )
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        when {
            state.isLoading -> LoadingState(
                message = stringResource(R.string.library_loading),
                modifier = Modifier.padding(innerPadding)
            )
            state.loadError != null -> ErrorState(
                title = stringResource(R.string.library_error_title),
                message = stringResource(state.loadError.toUiError().messageRes),
                modifier = Modifier.padding(innerPadding),
                retryLabel = stringResource(R.string.action_retry),
                onRetry = onRetry
            )
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(
                    start = BingeeDimensions.screenPadding,
                    top = 8.dp,
                    end = BingeeDimensions.screenPadding,
                    bottom = 32.dp
                ),
                verticalArrangement = Arrangement.spacedBy(32.dp)
            ) {
                item {
                    WatchingSection(
                        items = state.watching,
                        onOpenDetails = onOpenDetails,
                        onOpenAll = { onOpenCollection(ProfileCollectionShortcut.WATCHING) },
                        onNavigateToSearch = onNavigateToSearch
                    )
                }
                item {
                    CollectionSection(
                        counts = state.collectionCounts,
                        onOpenCollection = onOpenCollection
                    )
                }
                item {
                    FavoritesSection(
                        items = state.favorites,
                        onOpenDetails = onOpenDetails,
                        onOpenAll = { onOpenCollection(ProfileCollectionShortcut.FAVORITES) },
                        onNavigateToSearch = onNavigateToSearch
                    )
                }
            }
        }
    }
}

@Composable
private fun WatchingSection(
    items: List<ContinueWatchingItem>,
    onOpenDetails: (ExternalMediaRef, MediaType) -> Unit,
    onOpenAll: () -> Unit,
    onNavigateToSearch: () -> Unit
) {
    SectionHeader(
        title = stringResource(R.string.profile_watching_title),
        actionLabel = stringResource(R.string.profile_view_all),
        onAction = onOpenAll
    )
    Spacer(Modifier.height(12.dp))
    if (items.isEmpty()) {
        InlineEmptyState(
            title = stringResource(R.string.profile_watching_empty_title),
            body = stringResource(R.string.profile_watching_empty_body),
            actionLabel = stringResource(R.string.profile_empty_action_search),
            onAction = onNavigateToSearch
        )
    } else {
        PosterRow {
            items.forEach { item ->
                WatchingPosterItem(
                    item = item,
                    onClick = { onOpenDetails(item.mediaRef, item.mediaType) }
                )
            }
        }
    }
}

@Composable
private fun CollectionSection(counts: ProfileCollectionCounts, onOpenCollection: (ProfileCollectionShortcut) -> Unit) {
    SectionHeader(title = stringResource(R.string.profile_collection_title))
    Spacer(Modifier.height(12.dp))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            CollectionShortcut(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Star,
                label = stringResource(R.string.profile_collection_watch_later),
                count = counts.watchLater,
                onClick = { onOpenCollection(ProfileCollectionShortcut.WATCH_LATER) }
            )
            CollectionShortcut(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Check,
                label = stringResource(R.string.profile_collection_watched),
                count = counts.watched,
                onClick = { onOpenCollection(ProfileCollectionShortcut.WATCHED) }
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            CollectionShortcut(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Favorite,
                iconTint = MaterialTheme.colorScheme.error,
                label = stringResource(R.string.profile_collection_favorites),
                count = counts.favorites,
                onClick = { onOpenCollection(ProfileCollectionShortcut.FAVORITES) }
            )
            CollectionShortcut(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Close,
                label = stringResource(R.string.profile_collection_abandoned),
                count = counts.abandoned,
                onClick = { onOpenCollection(ProfileCollectionShortcut.ABANDONED) }
            )
        }
    }
}

@Composable
private fun FavoritesSection(
    items: List<LibraryEntry>,
    onOpenDetails: (ExternalMediaRef, MediaType) -> Unit,
    onOpenAll: () -> Unit,
    onNavigateToSearch: () -> Unit
) {
    SectionHeader(
        title = stringResource(R.string.profile_favorites_title),
        actionLabel = stringResource(R.string.profile_view_all),
        onAction = onOpenAll
    )
    Spacer(Modifier.height(12.dp))
    if (items.isEmpty()) {
        InlineEmptyState(
            title = stringResource(R.string.profile_favorites_empty_title),
            actionLabel = stringResource(R.string.profile_empty_action_search),
            onAction = onNavigateToSearch
        )
    } else {
        PosterRow {
            items.forEach { entry ->
                FavoritePosterItem(
                    entry = entry,
                    onClick = { onOpenDetails(entry.mediaRef, entry.mediaType) }
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
            style = MaterialTheme.typography.titleLarge
        )
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
private fun PosterRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        content = content
    )
}

@Composable
private fun WatchingPosterItem(item: ContinueWatchingItem, onClick: () -> Unit) {
    val position = item.nextEpisode?.let {
        stringResource(R.string.profile_episode_position, it.seasonNumber, it.episodeNumber)
    } ?: stringResource(
        R.string.profile_watching_progress_position,
        item.progress.watchedEpisodes,
        item.progress.trackableEpisodes
    )
    val description = stringResource(
        R.string.profile_watching_accessibility,
        item.title,
        item.progress.watchedEpisodes,
        item.progress.trackableEpisodes,
        position
    )
    Column(
        modifier = Modifier
            .width(132.dp)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = description
                role = Role.Button
            }
    ) {
        MediaPoster(
            title = item.title,
            posterUrl = item.posterUrl,
            width = 132.dp,
            height = 198.dp
        )
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { item.progress.fraction.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = BingeeStatusColors.progressing,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = item.title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = position,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun FavoritePosterItem(entry: LibraryEntry, onClick: () -> Unit) {
    val mediaMeta = stringResource(
        if (entry.mediaType == MediaType.MOVIE) {
            R.string.profile_media_type_movie
        } else {
            R.string.profile_media_type_series
        },
        entry.releaseDate?.year ?: 0
    ).takeUnless { entry.releaseDate == null }
        ?: stringResource(
            if (entry.mediaType == MediaType.MOVIE) {
                R.string.profile_media_type_movie_no_year
            } else {
                R.string.profile_media_type_series_no_year
            }
        )
    val description = stringResource(R.string.profile_favorite_accessibility, entry.title, mediaMeta)
    Column(
        modifier = Modifier
            .width(132.dp)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = description
                role = Role.Button
            }
    ) {
        MediaPoster(
            title = entry.title,
            posterUrl = entry.posterUrl,
            width = 132.dp,
            height = 198.dp
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = entry.title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = mediaMeta,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CollectionShortcut(
    modifier: Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    count: Int,
    onClick: () -> Unit,
    iconTint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    val text = stringResource(R.string.profile_collection_shortcut, label, count)
    Row(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = text
                role = Role.Button
            }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun InlineEmptyState(title: String, body: String? = null, actionLabel: String, onAction: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        body?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = onAction, contentPadding = PaddingValues(0.dp)) {
            Text(actionLabel)
        }
    }
}
