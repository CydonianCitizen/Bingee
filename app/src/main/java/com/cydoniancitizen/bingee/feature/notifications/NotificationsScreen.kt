package com.cydoniancitizen.bingee.feature.notifications

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cydoniancitizen.bingee.R
import com.cydoniancitizen.bingee.core.designsystem.component.EmptyState
import com.cydoniancitizen.bingee.core.designsystem.component.MediaPoster
import com.cydoniancitizen.bingee.core.designsystem.theme.BingeeDimensions
import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.core.model.ReleaseEvent
import com.cydoniancitizen.bingee.core.model.ReleaseEventType
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NotificationsScreen(
    onBack: () -> Unit,
    onOpenDetails: (ExternalMediaRef, MediaType) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NotificationsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val refreshFailedText = stringResource(R.string.notifications_refresh_failed)
    val retryText = stringResource(R.string.action_retry)

    LaunchedEffect(state.refreshState, state.error) {
        if (state.refreshState == NotificationRefreshState.Failed &&
            state.contentState !is NotificationsContentState.Error
        ) {
            val result = snackbarHostState.showSnackbar(
                message = refreshFailedText,
                actionLabel = retryText
            )
            viewModel.dismissRefreshError()
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.refresh()
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            NotificationsTopBar(onBack = onBack)
        }
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = state.refreshState == NotificationRefreshState.Refreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (val contentState = state.contentState) {
                NotificationsContentState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(BingeeDimensions.elementSpacing)
                        ) {
                            CircularProgressIndicator()
                            Text(
                                text = stringResource(R.string.notifications_loading),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                NotificationsContentState.NoFollowedSeries -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        EmptyState(
                            title = stringResource(R.string.notifications_title),
                            body = stringResource(R.string.notifications_empty_no_followed_series),
                            modifier = Modifier.padding(BingeeDimensions.screenPadding)
                        )
                    }
                }

                NotificationsContentState.NoEvents -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        EmptyState(
                            title = stringResource(R.string.notifications_title),
                            body = stringResource(R.string.notifications_empty_no_events),
                            modifier = Modifier.padding(BingeeDimensions.screenPadding)
                        )
                    }
                }

                is NotificationsContentState.Content -> {
                    NotificationsList(
                        groups = contentState.groups,
                        today = state.today,
                        onItemClick = { event -> onOpenDetails(event.mediaRef, MediaType.SERIES) },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                is NotificationsContentState.Error -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(BingeeDimensions.elementSpacing),
                            modifier = Modifier.padding(BingeeDimensions.screenPadding)
                        ) {
                            Text(
                                text = stringResource(R.string.notifications_refresh_failed),
                                color = MaterialTheme.colorScheme.error
                            )
                            Button(
                                onClick = viewModel::refresh,
                                enabled = state.refreshState != NotificationRefreshState.Refreshing
                            ) {
                                Text(stringResource(R.string.action_retry))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationsTopBar(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = BingeeDimensions.screenPadding, vertical = BingeeDimensions.elementSpacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.detail_back)
            )
        }
        Text(
            text = stringResource(R.string.notifications_title),
            modifier = Modifier.weight(1f).semantics { heading() },
            style = MaterialTheme.typography.headlineMedium
        )
    }
}

@Composable
private fun NotificationsList(
    groups: List<NotificationGroup>,
    today: LocalDate,
    onItemClick: (ReleaseEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.padding(horizontal = BingeeDimensions.screenPadding),
        verticalArrangement = Arrangement.spacedBy(BingeeDimensions.contentSpacing)
    ) {
        groups.forEach { group ->
            item(key = "header_${group.category}") {
                Text(
                    text = group.category.headerText(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(top = BingeeDimensions.elementSpacing, bottom = 4.dp)
                        .semantics { heading() }
                )
            }

            items(
                items = group.items,
                key = { event -> event.stableKey }
            ) { event ->
                NotificationItemCard(
                    event = event,
                    today = today,
                    onClick = { onItemClick(event) }
                )
            }
        }
        item {
            Spacer(modifier = Modifier.height(BingeeDimensions.screenPadding))
        }
    }
}

@Composable
private fun NotificationGroupCategory.headerText(): String = when (this) {
    NotificationGroupCategory.UPCOMING -> stringResource(R.string.notifications_group_upcoming)
    NotificationGroupCategory.TODAY -> stringResource(R.string.notifications_group_today)
    NotificationGroupCategory.THIS_WEEK -> stringResource(R.string.notifications_group_this_week)
    NotificationGroupCategory.EARLIER -> stringResource(R.string.notifications_group_earlier)
}

@Composable
private fun NotificationItemCard(
    event: ReleaseEvent,
    today: LocalDate,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(BingeeDimensions.elementSpacing),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MediaPoster(
                title = event.title,
                posterUrl = event.posterUrl,
                modifier = Modifier
                    .size(width = 48.dp, height = 72.dp)
                    .clip(RoundedCornerShape(8.dp))
            )

            Spacer(modifier = Modifier.width(BingeeDimensions.elementSpacing))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(2.dp))

                NotificationEventCopy(event = event, today = today)

                if (!event.subjectTitle.isNullOrBlank()) {
                    val detailInfo = buildSeasonEpisodeDetail(event)
                    if (detailInfo.isNotBlank()) {
                        Text(
                            text = detailInfo,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = formatEventDateBadge(event.eventDate, today),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun NotificationEventCopy(event: ReleaseEvent, today: LocalDate, modifier: Modifier = Modifier) {
    val eventText = when (event.subject.eventType) {
        ReleaseEventType.EPISODE_AIRING -> {
            val epNum = event.episodeNumber ?: 1
            when {
                event.eventDate == today -> stringResource(R.string.notification_item_new_episode_today)
                event.eventDate > today -> stringResource(
                    R.string.notification_item_episode_starts,
                    epNum,
                    formatLocalizedDate(event.eventDate)
                )
                else -> stringResource(R.string.notification_item_episode_available, epNum)
            }
        }

        ReleaseEventType.SEASON_PREMIERE -> {
            val seasonNum = event.seasonNumber ?: 1
            when {
                event.eventDate == today -> stringResource(R.string.notification_item_new_season_today)
                event.eventDate > today -> stringResource(
                    R.string.notification_item_season_starts,
                    seasonNum,
                    formatLocalizedDate(event.eventDate)
                )
                else -> stringResource(R.string.notification_item_new_season_available)
            }
        }

        ReleaseEventType.MOVIE_RELEASE -> ""
    }

    Text(
        text = eventText,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
    )
}

private fun buildSeasonEpisodeDetail(event: ReleaseEvent): String {
    val season = event.seasonNumber
    val episode = event.episodeNumber
    val title = event.subjectTitle

    return when {
        season != null && episode != null && !title.isNullOrBlank() -> "S$season E$episode • $title"
        season != null && episode != null -> "S$season E$episode"
        season != null && !title.isNullOrBlank() -> "Season $season • $title"
        !title.isNullOrBlank() -> title
        else -> ""
    }
}

@Composable
private fun formatEventDateBadge(eventDate: LocalDate, today: LocalDate): String = when {
    eventDate == today -> stringResource(R.string.notifications_group_today)
    eventDate == today.minusDays(1) -> "Yesterday"
    eventDate == today.plusDays(1) -> "Tomorrow"
    else -> formatLocalizedDate(eventDate)
}

private fun formatLocalizedDate(date: LocalDate): String {
    val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    return date.format(formatter)
}
