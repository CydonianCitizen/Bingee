package com.cydoniancitizen.bingee.feature.home

import androidx.annotation.StringRes
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
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
import com.cydoniancitizen.bingee.core.model.ContinueWatchingItem
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
    onOpenDetails: (ExternalMediaRef, MediaType) -> Unit,
    modifier: Modifier = Modifier,
    onOpenNotifications: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
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
        onMarkNextEpisode = viewModel::markNextEpisodeWatched,
        onUndoMarkedEpisode = viewModel::undoMarkedEpisode,
        onDismissSnackbar = viewModel::dismissSnackbar,
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
    onOpenSettings: () -> Unit,
    onOpenDetails: (ExternalMediaRef, MediaType) -> Unit,
    modifier: Modifier = Modifier,
    onOpenNotifications: () -> Unit = {},
    onAddToWatchlist: (MediaSearchResult) -> Unit = {},
    onMarkNextEpisode: (ContinueWatchingItem) -> Unit = {},
    onUndoMarkedEpisode: () -> Unit = {},
    onDismissSnackbar: () -> Unit = {}
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val feedback = state.snackbar
    val feedbackText = when (feedback) {
        is HomeSnackbar.EpisodeMarked -> stringResource(
            R.string.home_continue_watching_marked,
            feedback.title,
            feedback.episode.seasonNumber,
            feedback.episode.episodeNumber
        )
        is HomeSnackbar.Failed -> stringResource(feedback.error.toUiError().messageRes)
        null -> null
    }
    val undoText = stringResource(R.string.action_undo)
    LaunchedEffect(feedback) {
        if (feedbackText == null) return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = feedbackText,
            actionLabel = undoText.takeIf { feedback is HomeSnackbar.EpisodeMarked },
            duration = SnackbarDuration.Long
        )
        if (result == SnackbarResult.ActionPerformed) onUndoMarkedEpisode() else onDismissSnackbar()
    }

    PullToRefreshBox(
        isRefreshing = state.refresh == HomeRefreshState.Refreshing,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize()
    ) {
        // One lazy list for the whole screen: the calendar, both discovery rows, and Continue Watching compose
        // only as they scroll into view, and no vertical scroller is nested inside another.
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(BingeeDimensions.screenPadding),
            verticalArrangement = Arrangement.spacedBy(BingeeDimensions.contentSpacing)
        ) {
            item(key = "title") {
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
            }
            state.lastSuccessfulRefreshAt?.let {
                item(key = "lastUpdated") {
                    Text(
                        stringResource(R.string.home_last_updated, it.localized()),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
            // An empty item would still take a gap in the list, so Idle adds none.
            if (state.refresh != HomeRefreshState.Idle) {
                item(key = "refresh") {
                    RefreshFeedback(
                        refresh = state.refresh,
                        onRetry = onRefresh,
                        onSettings = onOpenSettings,
                        onDismiss = onDismissFeedback
                    )
                }
            }

            // Personal release calendar stays above general discovery content.
            when (val content = state.content) {
                HomeContentState.Loading -> item(key = "loading") {
                    LoadingState(stringResource(R.string.home_loading))
                }
                HomeContentState.Empty -> item(key = "empty") {
                    EmptyState(
                        title = stringResource(R.string.home_empty_title),
                        body = stringResource(R.string.home_empty_body)
                    )
                }
                is HomeContentState.Error -> item(key = "error") {
                    val error = content.error.toUiError()
                    ErrorState(
                        title = stringResource(R.string.home_local_error_title),
                        message = stringResource(error.messageRes),
                        retryLabel = stringResource(R.string.action_retry),
                        onRetry = onRetryLocal
                    )
                }
                // One item per date keeps the tighter spacing between a date and its releases.
                is HomeContentState.Events -> items(content.groups, key = { "date:${it.date}" }) { group ->
                    Column(verticalArrangement = Arrangement.spacedBy(BingeeDimensions.elementSpacing)) {
                        DateHeader(group)
                        group.events.forEach { event ->
                            key(event.stableKey) {
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

            // General TMDB discovery content follows the personal calendar, and Continue Watching closes the
            // screen after it. Films and series get a row each: one merged row buried whichever type the
            // interleave happened to push right.
            featuredRow(
                titleRes = R.string.home_featured_movies,
                items = state.featuredMovies,
                libraryMemberships = state.libraryMemberships,
                addingToWatchlist = state.addingToWatchlist,
                onAddToWatchlist = onAddToWatchlist,
                onOpenDetails = onOpenDetails
            )
            featuredRow(
                titleRes = R.string.home_featured_series,
                items = state.featuredSeries,
                libraryMemberships = state.libraryMemberships,
                addingToWatchlist = state.addingToWatchlist,
                onAddToWatchlist = onAddToWatchlist,
                onOpenDetails = onOpenDetails
            )

            if (state.continueWatching.isNotEmpty()) {
                item(key = "continueWatchingTitle") {
                    Text(
                        text = stringResource(R.string.home_continue_watching),
                        modifier = Modifier.semantics { heading() },
                        style = MaterialTheme.typography.titleLarge
                    )
                }
                item(key = "continueWatching") {
                    val continueWatchingState = rememberLazyListState()
                    // Each card spans the row, so the section keeps the screen's side margins like the calendar
                    // cards; snapping stops a swipe on a whole card rather than between two.
                    LazyRow(
                        state = continueWatchingState,
                        flingBehavior = rememberSnapFlingBehavior(continueWatchingState),
                        horizontalArrangement = Arrangement.spacedBy(BingeeDimensions.elementSpacing),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        items(
                            items = state.continueWatching,
                            key = { "${it.mediaRef.source}:${it.mediaRef.externalId}" }
                        ) { item ->
                            ContinueWatchingCard(
                                item = item,
                                isMarking = item.mediaRef in state.markingEpisodes,
                                onClick = { onOpenDetails(item.mediaRef, item.mediaType) },
                                onMarkNextEpisode = { onMarkNextEpisode(item) },
                                modifier = Modifier.fillParentMaxWidth()
                            )
                        }
                    }
                }
            }
        }
        SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun ContinueWatchingCard(
    item: ContinueWatchingItem,
    isMarking: Boolean,
    onClick: () -> Unit,
    onMarkNextEpisode: () -> Unit,
    modifier: Modifier = Modifier
) {
    val openDescription = stringResource(R.string.home_open_continue_details, item.title)
    Card(
        onClick = onClick,
        // The height wraps its content, because a fixed one is dead space at font scale 1.0 and runs out
        // of room well before 2.0.
        modifier = modifier.semantics { contentDescription = openDescription }
    ) {
        Row(
            modifier = Modifier.padding(BingeeDimensions.elementSpacing),
            horizontalArrangement = Arrangement.spacedBy(BingeeDimensions.contentSpacing)
        ) {
            MediaPoster(
                title = item.title,
                posterUrl = item.posterUrl,
                modifier = Modifier.size(width = 88.dp, height = 132.dp),
                // The card owns the combined description of this item.
                contentDescription = null
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = pluralStringResource(
                        R.plurals.home_continue_watching_progress,
                        item.progress.trackableEpisodes,
                        item.progress.watchedEpisodes,
                        item.progress.trackableEpisodes
                    ),
                    style = MaterialTheme.typography.bodySmall
                )
                item.lastWatchedEpisode?.let {
                    Text(
                        text = stringResource(
                            R.string.home_continue_watching_last_episode,
                            it.seasonNumber,
                            it.episodeNumber
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                item.nextEpisode?.let { next ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(
                                R.string.home_continue_watching_next_episode,
                                next.seasonNumber,
                                next.episodeNumber
                            ),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        // Without a cached episode identity there is nothing to write, so the shortcut hides
                        // rather than failing; Details still offers the full season list.
                        if (item.nextEpisodeRef != null) {
                            FilledTonalIconButton(onClick = onMarkNextEpisode, enabled = !isMarking) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = stringResource(
                                        R.string.home_continue_watching_mark_watched,
                                        next.seasonNumber,
                                        next.episodeNumber
                                    )
                                )
                            }
                        }
                    }
                }
                LinearProgressIndicator(
                    progress = { item.progress.fraction.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * One discovery row. Adds nothing at all when its list is empty, so a media type the provider
 * had nothing for leaves no orphan heading behind.
 */
private fun LazyListScope.featuredRow(
    @StringRes titleRes: Int,
    items: List<MediaSearchResult>,
    libraryMemberships: Set<Pair<ExternalMediaRef, MediaType>>,
    addingToWatchlist: Set<Pair<ExternalMediaRef, MediaType>>,
    onAddToWatchlist: (MediaSearchResult) -> Unit,
    onOpenDetails: (ExternalMediaRef, MediaType) -> Unit
) {
    if (items.isEmpty()) return
    item(key = "featuredTitle:$titleRes") {
        Text(
            text = stringResource(titleRes),
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleLarge
        )
    }
    item(key = "featuredRow:$titleRes") {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(BingeeDimensions.elementSpacing),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            items(
                items = items,
                key = { "${it.externalRef.source}:${it.externalRef.externalId}" }
            ) { item ->
                FeaturedReleaseCard(
                    item = item,
                    inWatchlist = item.externalRef to item.mediaType in libraryMemberships,
                    isAdding = item.externalRef to item.mediaType in addingToWatchlist,
                    onAddToWatchlist = { onAddToWatchlist(item) },
                    onClick = { onOpenDetails(item.externalRef, item.mediaType) }
                )
            }
        }
    }
}

/** Poster width for a discovery card, chosen to sit just under the collection grid's 140 dp cell. */
private val FeaturedCardWidth = 124.dp

/** Poster aspect ratio, shared with the collection grid so both surfaces crop artwork identically. */
private val PosterAspectRatio = 0.67f

/**
 * Disc behind the poster's watchlist control. Fixed rather than themed because the artwork under it can be
 * any colour; at 60% black even a white poster leaves the white "+" near 5.7:1 and the gold bookmark near 3.9:1.
 */
private val PosterControlScrim = Color.Black.copy(alpha = 0.6f)
private val PosterControlAccent = Color(0xFFFFCC33)

@Composable
private fun FeaturedReleaseCard(
    item: MediaSearchResult,
    inWatchlist: Boolean,
    isAdding: Boolean,
    onAddToWatchlist: () -> Unit,
    onClick: () -> Unit
) {
    val watchlistDescription = stringResource(
        if (inWatchlist) R.string.action_in_watchlist else R.string.action_add_to_watchlist
    )
    Card(
        onClick = onClick,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = MaterialTheme.shapes.medium,
        // Fixed width so the row's slots line up; the height wraps the poster and its caption, the
        // same shape a collection grid cell takes.
        modifier = Modifier
            .width(FeaturedCardWidth)
            .semantics { contentDescription = item.title }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box {
                MediaPoster(
                    title = item.title,
                    posterUrl = item.posterUrl,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(PosterAspectRatio),
                    // The card owns the combined description of this item.
                    contentDescription = null
                )
                // The watchlist action rides on the poster, as the favourite toggle does in the
                // collection grid. A labelled button cannot hold "Add to Watchlist" at this width.
                // It sits on a fixed dark disc rather than theme colours, so it stays legible on any artwork.
                IconButton(
                    onClick = onAddToWatchlist,
                    enabled = !inWatchlist && !isAdding,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = PosterControlScrim,
                        contentColor = Color.White,
                        disabledContainerColor = PosterControlScrim,
                        // Disabled once in the watchlist, but the bookmark must stay fully legible; only
                        // the in-flight add is dimmed.
                        disabledContentColor = if (inWatchlist) PosterControlAccent else Color.White.copy(alpha = 0.6f)
                    ),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(32.dp)
                ) {
                    if (inWatchlist) {
                        Icon(
                            painter = painterResource(R.drawable.ic_bookmark),
                            contentDescription = watchlistDescription,
                            modifier = Modifier.size(18.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = watchlistDescription,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    // Always two lines, so a one-line title and a wrapped one produce cards of the
                    // same height and the row keeps a single bottom edge at every font scale.
                    minLines = 2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                item.releaseDate?.let {
                    Text(
                        text = stringResource(R.string.search_release_year, it.year),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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
            // The card owns the combined description of this item.
            MediaPoster(event.title, event.posterUrl, contentDescription = null)
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
