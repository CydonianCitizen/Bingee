package com.cydoniancitizen.bingee.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cydoniancitizen.bingee.R
import com.cydoniancitizen.bingee.core.designsystem.component.EmptyState
import com.cydoniancitizen.bingee.core.designsystem.component.ErrorState
import com.cydoniancitizen.bingee.core.designsystem.component.LoadingState
import com.cydoniancitizen.bingee.core.designsystem.component.MediaPoster
import com.cydoniancitizen.bingee.core.designsystem.theme.BingeeDimensions
import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.MediaSearchResult
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.core.model.ReleaseDateCategory
import com.cydoniancitizen.bingee.core.model.ReleaseDateGroup
import com.cydoniancitizen.bingee.core.model.ReleaseEvent
import com.cydoniancitizen.bingee.core.model.ReleaseEventType
import com.cydoniancitizen.bingee.core.ui.toUiError
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Composable
internal fun HomeScreen(
    onOpenNotifications: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenDetails: (ExternalMediaRef, MediaType) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HomeContent(
        state = state,
        onRefresh = viewModel::refresh,
        onRetryLocal = viewModel::retryLocal,
        onDismissFeedback = viewModel::dismissRefreshFeedback,
        onOpenNotifications = onOpenNotifications,
        onOpenSettings = onOpenSettings,
        onOpenDetails = onOpenDetails,
        onAddToWatchlist = viewModel::addToWatchlist,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeContent(
    state: HomeUiState,
    onRefresh: () -> Unit,
    onRetryLocal: () -> Unit,
    onDismissFeedback: () -> Unit,
    onOpenNotifications: () -> Unit = {},
    onOpenSettings: () -> Unit,
    onOpenDetails: (ExternalMediaRef, MediaType) -> Unit,
    onAddToWatchlist: (MediaSearchResult) -> Unit = {},
    modifier: Modifier = Modifier
) {
    PullToRefreshBox(
        isRefreshing = state.refresh == HomeRefreshState.Refreshing,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(BingeeDimensions.screenPadding),
            verticalArrangement = Arrangement.spacedBy(BingeeDimensions.contentSpacing)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.home_title),
                    modifier = Modifier.weight(1f).semantics { heading() },
                    style = MaterialTheme.typography.headlineMedium
                )
                IconButton(onClick = onOpenNotifications) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = stringResource(R.string.notifications_title)
                    )
                }
            }
            state.lastSuccessfulRefreshAt?.let {
                Text(
                    stringResource(R.string.home_last_updated, it.localized()),
                    style = MaterialTheme.typography.labelMedium
                )
            }
            RefreshFeedback(
                refresh = state.refresh,
                onRetry = onRefresh,
                onSettings = onOpenSettings,
                onDismiss = onDismissFeedback
            )

            // Featured Releases Section
            if (state.featuredReleases.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.home_featured_releases),
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.titleLarge
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(BingeeDimensions.elementSpacing),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(
                        items = state.featuredReleases,
                        key = { "${it.externalRef.source}:${it.externalRef.externalId}" }
                    ) { item ->
                        FeaturedReleaseCard(
                            item = item,
                            inWatchlist = item.externalRef in state.libraryMemberships,
                            isAdding = item.externalRef in state.addingToWatchlist,
                            onAddToWatchlist = { onAddToWatchlist(item) },
                            onClick = { onOpenDetails(item.externalRef, item.mediaType) }
                        )
                    }
                }
            }

            // Events Content
            when (val content = state.content) {
                HomeContentState.Loading -> LoadingState(stringResource(R.string.home_loading))
                HomeContentState.Empty -> EmptyState(
                    title = stringResource(R.string.home_empty_title),
                    body = stringResource(R.string.home_empty_body)
                )
                is HomeContentState.Error -> {
                    val error = content.error.toUiError()
                    ErrorState(
                        title = stringResource(R.string.home_local_error_title),
                        message = stringResource(error.messageRes),
                        retryLabel = stringResource(R.string.action_retry),
                        onRetry = onRetryLocal
                    )
                }
                is HomeContentState.Events -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(BingeeDimensions.elementSpacing)
                    ) {
                        content.groups.forEach { group ->
                            stickyHeader(key = "date:${group.date}") {
                                DateHeader(group)
                            }
                            items(group.events, key = ReleaseEvent::stableKey) { event ->
                                ReleaseEventCard(
                                    event = event,
                                    category = group.category,
                                    onClick = { onOpenDetails(event.mediaRef, event.mediaType) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FeaturedReleaseCard(
    item: MediaSearchResult,
    inWatchlist: Boolean,
    isAdding: Boolean,
    onAddToWatchlist: () -> Unit,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .size(width = 200.dp, height = 300.dp)
            .semantics { contentDescription = item.title }
    ) {
        Column(
            modifier = Modifier.padding(BingeeDimensions.elementSpacing),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            MediaPoster(
                title = item.title,
                posterUrl = item.posterUrl,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            item.releaseDate?.let {
                Text(
                    text = stringResource(R.string.search_release_year, it.year),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Button(
                onClick = onAddToWatchlist,
                enabled = !inWatchlist && !isAdding,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(
                        if (inWatchlist) R.string.action_in_watchlist else R.string.action_add_to_watchlist
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun RefreshFeedback(
    refresh: HomeRefreshState,
    onRetry: () -> Unit,
    onSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    when (refresh) {
        HomeRefreshState.Idle -> Unit
        HomeRefreshState.Refreshing -> Row(
            horizontalArrangement = Arrangement.spacedBy(BingeeDimensions.elementSpacing),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(Modifier.size(24.dp))
            Text(stringResource(R.string.home_refreshing))
        }
        HomeRefreshState.Complete -> FeedbackRow(
            stringResource(R.string.home_refresh_complete),
            onDismiss
        )
        is HomeRefreshState.Partial -> FeedbackRow(
            stringResource(
                R.string.home_refresh_partial,
                refresh.summary.operationsSucceeded,
                refresh.summary.operationsFailed
            ),
            onDismiss,
            onRetry
        )
        is HomeRefreshState.Failed -> {
            val error = refresh.error.toUiError()
            FeedbackRow(stringResource(error.messageRes), onDismiss, onRetry)
        }
        HomeRefreshState.NoWork -> FeedbackRow(stringResource(R.string.home_refresh_no_work), onDismiss)
        HomeRefreshState.CredentialRequired -> FeedbackRow(
            message = stringResource(R.string.home_refresh_credential_required),
            onDismiss = onDismiss,
            action = onSettings,
            actionLabel = stringResource(R.string.search_open_settings)
        )
    }
}

@Composable
private fun FeedbackRow(
    message: String,
    onDismiss: () -> Unit,
    action: (() -> Unit)? = null,
    actionLabel: String = stringResource(R.string.action_retry)
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(message, modifier = Modifier.weight(1f))
        action?.let {
            TextButton(onClick = it) { Text(actionLabel) }
        }
        TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_dismiss)) }
    }
}

@Composable
private fun DateHeader(group: ReleaseDateGroup) {
    val category = when (group.category) {
        ReleaseDateCategory.RECENT -> stringResource(R.string.home_date_recent)
        ReleaseDateCategory.TODAY -> stringResource(R.string.home_date_today)
        ReleaseDateCategory.UPCOMING -> stringResource(R.string.home_date_upcoming)
    }
    Text(
        text = stringResource(R.string.home_date_header, group.date.localized(), category),
        modifier = Modifier.fillMaxWidth().semantics { heading() },
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun ReleaseEventCard(event: ReleaseEvent, category: ReleaseDateCategory, onClick: () -> Unit) {
    val openDescription = stringResource(R.string.home_open_event_details, event.title)
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = openDescription }
    ) {
        Row(
            modifier = Modifier.padding(BingeeDimensions.elementSpacing),
            horizontalArrangement = Arrangement.spacedBy(BingeeDimensions.contentSpacing)
        ) {
            MediaPoster(event.title, event.posterUrl)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(BingeeDimensions.elementSpacing)
            ) {
                Text(event.title, style = MaterialTheme.typography.titleMedium)
                Text(event.description(), style = MaterialTheme.typography.bodyMedium)
                Text(
                    when (category) {
                        ReleaseDateCategory.RECENT -> stringResource(R.string.home_event_recent)
                        ReleaseDateCategory.TODAY -> stringResource(R.string.home_event_today)
                        ReleaseDateCategory.UPCOMING -> stringResource(R.string.home_event_upcoming)
                    },
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun ReleaseEvent.description(): String = when (subject.eventType) {
    ReleaseEventType.MOVIE_RELEASE -> stringResource(R.string.home_event_movie_release)
    ReleaseEventType.ANIME_PREMIERE -> stringResource(R.string.home_event_anime_premiere)
    ReleaseEventType.SEASON_PREMIERE -> {
        val name = subjectTitle ?: stringResource(R.string.detail_season_fallback, requireNotNull(seasonNumber))
        stringResource(R.string.home_event_season, requireNotNull(seasonNumber), name)
    }
    ReleaseEventType.EPISODE_AIRING -> {
        val name = subjectTitle ?: stringResource(R.string.home_episode_title_unknown)
        stringResource(
            R.string.home_event_episode,
            requireNotNull(seasonNumber),
            requireNotNull(episodeNumber),
            name
        )
    }
}

private fun java.time.LocalDate.localized(): String =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(Locale.getDefault()).format(this)

private fun Instant.localized(): String = DateTimeFormatter
    .ofLocalizedDateTime(FormatStyle.MEDIUM)
    .withLocale(Locale.getDefault())
    .withZone(ZoneId.systemDefault())
    .format(this)
