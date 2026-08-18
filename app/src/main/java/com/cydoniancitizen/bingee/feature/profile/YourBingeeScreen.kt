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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cydoniancitizen.bingee.R
import com.cydoniancitizen.bingee.core.designsystem.component.ErrorState
import com.cydoniancitizen.bingee.core.designsystem.component.LoadingState
import com.cydoniancitizen.bingee.core.designsystem.component.MediaPoster
import com.cydoniancitizen.bingee.core.designsystem.theme.BingeeDimensions
import com.cydoniancitizen.bingee.core.designsystem.theme.BingeePodiumColors
import com.cydoniancitizen.bingee.core.designsystem.theme.BingeeStatusColors
import com.cydoniancitizen.bingee.core.model.ContinueWatchingItem
import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.LibraryEntry
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.core.model.toNavigableDetailsRef
import com.cydoniancitizen.bingee.core.ui.toUiError
import com.cydoniancitizen.bingee.domain.model.GenreStatistic
import com.cydoniancitizen.bingee.domain.model.ViewingDurationLabels
import com.cydoniancitizen.bingee.domain.model.WatchedStatistics
import com.cydoniancitizen.bingee.domain.model.formatViewingDuration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun YourBingeeContent(
    state: ProfileUiState,
    onOpenSettings: () -> Unit,
    onOpenDetails: (ExternalMediaRef, MediaType) -> Unit,
    onOpenCollection: (ProfileCollectionShortcut) -> Unit,
    onNavigateToSearch: () -> Unit,
    onOpenStatistics: () -> Unit,
    onRetryStatistics: () -> Unit,
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
                item {
                    StatisticsSection(
                        state = state,
                        onOpenStatistics = onOpenStatistics,
                        onRetry = onRetryStatistics
                    )
                }
            }
        }
    }
}

@Composable
private fun StatisticsSection(state: ProfileUiState, onOpenStatistics: () -> Unit, onRetry: () -> Unit) {
    SectionHeader(title = stringResource(R.string.profile_statistics_title))
    when {
        state.statisticsError != null -> {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(state.statisticsError.toUiError().messageRes),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
                TextButton(onClick = onRetry, contentPadding = PaddingValues(0.dp)) {
                    Text(stringResource(R.string.action_retry))
                }
            }
        }
        state.isStatisticsLoading -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        else -> StatisticsPreview(
            statistics = state.statistics,
            onOpenStatistics = onOpenStatistics
        )
    }
}

@Composable
private fun StatisticsPreview(statistics: WatchedStatistics, onOpenStatistics: () -> Unit) {
    val durationLabels = ViewingDurationLabels(
        day = stringResource(R.string.statistics_duration_day_short),
        hour = stringResource(R.string.statistics_duration_hour_short),
        minute = stringResource(R.string.statistics_duration_minute_short)
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        StatisticsMediaBlock(
            heading = stringResource(R.string.profile_statistics_movies_heading),
            watchedLabel = stringResource(R.string.profile_statistics_movies_watched),
            watchedCount = statistics.moviesWatchedCount,
            watchTime = statistics.movieWatchTimeMinutes,
            watchTimeIncomplete = statistics.movieWatchTimeIncomplete,
            genres = statistics.movieGenres,
            durationLabels = durationLabels
        )
        Spacer(Modifier.height(20.dp))
        StatisticsMediaBlock(
            heading = stringResource(R.string.profile_statistics_series_heading),
            watchedLabel = stringResource(R.string.profile_statistics_series_watched),
            watchedCount = statistics.tvSeriesCompletedCount,
            watchTime = statistics.seriesWatchTimeMinutes,
            watchTimeIncomplete = statistics.seriesWatchTimeIncomplete,
            genres = statistics.seriesGenres,
            durationLabels = durationLabels
        )
        TextButton(
            onClick = onOpenStatistics,
            modifier = Modifier.align(Alignment.End)
        ) {
            Text(stringResource(R.string.profile_statistics_view_all))
        }
    }
}

@Composable
private fun StatisticsMediaBlock(
    heading: String,
    watchedLabel: String,
    watchedCount: Int,
    watchTime: Long,
    watchTimeIncomplete: Boolean,
    genres: List<GenreStatistic>,
    durationLabels: ViewingDurationLabels
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = heading,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() }
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            StatisticsMetric(
                value = watchedCount.toString(),
                label = watchedLabel,
                modifier = Modifier.weight(1f)
            )
            StatisticsMetric(
                value = if (watchTimeIncomplete) {
                    stringResource(R.string.statistics_watch_time_unavailable)
                } else {
                    formatViewingDuration(watchTime, durationLabels)
                },
                label = stringResource(R.string.profile_statistics_watch_time),
                modifier = Modifier.weight(1f)
            )
        }
        Text(
            text = stringResource(R.string.profile_statistics_genres),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.semantics { heading() }
        )
        if (genres.size < 3) {
            Text(
                text = stringResource(R.string.profile_statistics_not_enough_data),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            GenrePodium(genres)
        }
    }
}

@Composable
private fun StatisticsMetric(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

internal fun podiumPresentationOrder(ranked: List<GenreStatistic>): List<GenreStatistic> = listOfNotNull(
    ranked.getOrNull(1),
    ranked.getOrNull(0),
    ranked.getOrNull(2)
)

@Composable
private fun GenrePodium(ranked: List<GenreStatistic>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 90.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        podiumPresentationOrder(ranked).forEachIndexed { index, genre ->
            val (height, background, content) = when (index) {
                0 -> Triple(70.dp, BingeePodiumColors.silver, BingeePodiumColors.onSilver)
                1 -> Triple(90.dp, BingeePodiumColors.gold, BingeePodiumColors.onGold)
                else -> Triple(64.dp, BingeePodiumColors.bronze, BingeePodiumColors.onBronze)
            }
            GenrePodiumStep(
                genre = genre,
                background = background,
                content = content,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = height)
            )
        }
    }
}

@Composable
private fun GenrePodiumStep(genre: GenreStatistic, background: Color, content: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
            .background(background)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = genre.name,
            color = content,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = genre.titleCount.toString(),
            color = content,
            style = MaterialTheme.typography.titleMedium
        )
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
                    onClick = item.mediaRef.toNavigableDetailsRef()?.let { reference ->
                        { onOpenDetails(reference, item.mediaType) }
                    }
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
                    onClick = entry.navigableDetailsRef?.let { reference ->
                        { onOpenDetails(reference, entry.mediaType) }
                    }
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
private fun WatchingPosterItem(item: ContinueWatchingItem, onClick: (() -> Unit)?) {
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
            .clickable(enabled = onClick != null, onClick = onClick ?: {})
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
private fun FavoritePosterItem(entry: LibraryEntry, onClick: (() -> Unit)?) {
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
            .clickable(enabled = onClick != null, onClick = onClick ?: {})
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
