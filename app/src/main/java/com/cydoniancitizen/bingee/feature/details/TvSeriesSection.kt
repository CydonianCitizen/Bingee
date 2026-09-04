package com.cydoniancitizen.bingee.feature.details

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.cydoniancitizen.bingee.R
import com.cydoniancitizen.bingee.core.designsystem.theme.BingeeDimensions
import com.cydoniancitizen.bingee.core.model.CacheFreshness
import com.cydoniancitizen.bingee.core.model.CachedSeason
import com.cydoniancitizen.bingee.core.model.EpisodeWatchState
import com.cydoniancitizen.bingee.core.model.SeasonProgress
import com.cydoniancitizen.bingee.core.model.TrackedEpisode
import com.cydoniancitizen.bingee.core.result.AppError
import com.cydoniancitizen.bingee.core.ui.toUiError

@Composable
internal fun TvSeriesSection(
    state: SeriesDetailUiState,
    onToggleExpanded: (CachedSeason) -> Unit,
    onRetrySeason: (CachedSeason) -> Unit,
    onToggleEpisode: (TrackedEpisode) -> Unit,
    onToggleSeason: (CachedSeason) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (state.content == SeriesContentState.NotApplicable) return
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(BingeeDimensions.contentSpacing)
    ) {
        Text(
            text = stringResource(R.string.detail_series_progress_title),
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleLarge
        )
        when (val content = state.content) {
            SeriesContentState.NotApplicable -> Unit
            SeriesContentState.Loading -> Text(stringResource(R.string.detail_seasons_loading))
            is SeriesContentState.Error -> Text(
                text = stringResource(content.error.toUiError().messageRes),
                color = MaterialTheme.colorScheme.error
            )
            is SeriesContentState.Ready -> {
                if (content.progress.trackableEpisodes == 0) {
                    Text(stringResource(R.string.library_progress_unavailable))
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(BingeeDimensions.elementSpacing)) {
                        Text(
                            stringResource(
                                R.string.detail_series_progress,
                                content.progress.watchedEpisodes,
                                content.progress.trackableEpisodes,
                                content.progress.completedSeasons,
                                content.progress.trackableSeasons
                            )
                        )
                        WatchProgressBar(content.progress.fraction)
                    }
                }
                val regular = content.seasons.filter { it.season.seasonNumber > 0 }
                val specials = content.seasons.filter { it.season.seasonNumber == 0 }
                regular.forEach { season ->
                    SeasonCard(
                        season,
                        state,
                        onToggleExpanded,
                        onRetrySeason,
                        onToggleEpisode,
                        onToggleSeason,
                        onOpenSettings
                    )
                }
                if (specials.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.detail_specials_title),
                        modifier = Modifier.semantics { heading() },
                        style = MaterialTheme.typography.titleMedium
                    )
                    specials.forEach { season ->
                        SeasonCard(
                            season,
                            state,
                            onToggleExpanded,
                            onRetrySeason,
                            onToggleEpisode,
                            onToggleSeason,
                            onOpenSettings
                        )
                    }
                }
                if (content.seasons.isEmpty()) {
                    Text(stringResource(R.string.detail_seasons_empty))
                }
            }
        }
    }
}

/**
 * The counts next to this bar already state the same progress in words, so the bar is cleared for
 * accessibility: left as it is, TalkBack announces the identical value a second time as a
 * percentage.
 */
@Composable
private fun WatchProgressBar(fraction: Float) {
    LinearProgressIndicator(
        progress = { fraction.coerceIn(0f, 1f) },
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics {}
    )
}

@Composable
private fun SeasonCard(
    season: CachedSeason,
    state: SeriesDetailUiState,
    onToggleExpanded: (CachedSeason) -> Unit,
    onRetrySeason: (CachedSeason) -> Unit,
    onToggleEpisode: (TrackedEpisode) -> Unit,
    onToggleSeason: (CachedSeason) -> Unit,
    onOpenSettings: () -> Unit
) {
    val ref = season.season.externalRef
    val expanded = ref in state.expandedSeasons
    val load = state.seasonLoads[ref] ?: SeasonLoadState.Idle
    val pending = ref in state.pendingSeasons
    val title =
        season.season.name
            ?: if (season.season.seasonNumber == 0) {
                stringResource(R.string.detail_specials_title)
            } else {
                stringResource(R.string.detail_season_fallback, season.season.seasonNumber)
            }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            // Expanding a season changes this card's height by the whole episode list; without the
            // animation everything below it jumps that distance in a single frame.
            modifier = Modifier
                .animateContentSize()
                .padding(BingeeDimensions.contentSpacing),
            verticalArrangement = Arrangement.spacedBy(BingeeDimensions.elementSpacing)
        ) {
            SeasonHeader(
                season = season,
                title = title,
                expanded = expanded,
                refreshEnabled = load == SeasonLoadState.Idle,
                seasonActionEnabled = !pending && season.progress.trackableEpisodes > 0,
                onToggleExpanded = { onToggleExpanded(season) },
                onRefreshSeason = { onRetrySeason(season) },
                onToggleSeason = { onToggleSeason(season) }
            )
            if (season.episodeCacheFreshness == CacheFreshness.STALE) {
                Text(
                    text = stringResource(R.string.detail_season_stale),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (pending) {
                // The header's checkbox goes disabled while the season-wide write is in flight, but
                // a checkbox cannot say why. This keeps the pending state legible on the one card it
                // blocks, without disabling anything else in the section.
                Text(stringResource(R.string.detail_progress_updating))
            }
            if (expanded) {
                when (load) {
                    SeasonLoadState.Idle -> Unit
                    SeasonLoadState.Loading -> CircularProgressIndicator()
                    SeasonLoadState.Refreshing -> Text(stringResource(R.string.detail_season_refreshing))
                    is SeasonLoadState.Error -> {
                        Text(
                            text = stringResource(load.error.toUiError().messageRes),
                            color = MaterialTheme.colorScheme.error
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(BingeeDimensions.elementSpacing)) {
                            Button(onClick = { onRetrySeason(season) }) {
                                Text(stringResource(R.string.action_retry))
                            }
                            if (load.error == AppError.Unauthorized) {
                                Button(onClick = onOpenSettings) {
                                    Text(stringResource(R.string.search_open_settings))
                                }
                            }
                        }
                    }
                }
                if (season.episodesFetchedAt != null) {
                    if (season.episodes.isEmpty()) {
                        Text(stringResource(R.string.detail_episodes_empty))
                    } else {
                        // A plain Column, not a LazyColumn: this section already lives inside the
                        // screen's scrolling list, where a nested scroller steals the drag gesture
                        // and the height cap it needs silently truncates long seasons.
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(BingeeDimensions.elementSpacing)
                        ) {
                            season.episodes.forEach { episode ->
                                val episodeRef = episode.episode.externalRef
                                key(episodeRef.source.name, episodeRef.externalId) {
                                    EpisodeRow(
                                        episode = episode,
                                        pending = episodeRef in state.pendingEpisodes,
                                        onToggle = { onToggleEpisode(episode) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SeasonHeader(
    season: CachedSeason,
    title: String,
    expanded: Boolean,
    refreshEnabled: Boolean,
    seasonActionEnabled: Boolean,
    onToggleExpanded: () -> Unit,
    onRefreshSeason: () -> Unit,
    onToggleSeason: () -> Unit
) {
    val expandLabel = stringResource(
        if (expanded) R.string.detail_collapse_season else R.string.detail_expand_season,
        title
    )
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "seasonChevron"
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // The label rides on the click action instead of replacing the description, so TalkBack
            // still reads the season title and its counts before offering the action.
            .clickable(onClickLabel = expandLabel, onClick = onToggleExpanded)
            .semantics(mergeDescendants = true) { role = Role.Button },
        verticalArrangement = Arrangement.spacedBy(BingeeDimensions.elementSpacing)
    ) {
        // Only the title shares a line with the two trailing controls. The progress sentence sits
        // below them at full card width: sharing the line, it wrapped against their gutter at every
        // font scale.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(BingeeDimensions.elementSpacing),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = stringResource(
                        R.string.detail_season_number_and_count,
                        season.season.seasonNumber,
                        season.season.episodeCount
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                // The header owns the expand/collapse action and its label.
                contentDescription = null,
                modifier = Modifier.rotate(chevronRotation)
            )
            if (season.episodesFetchedAt != null) {
                IconButton(onClick = onRefreshSeason, enabled = refreshEnabled) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.detail_refresh_season)
                    )
                }
                SeasonWatchedCheckbox(
                    progress = season.progress,
                    enabled = seasonActionEnabled,
                    onToggleSeason = onToggleSeason
                )
            }
        }
        if (season.progress.trackableEpisodes == 0) {
            Text(stringResource(R.string.library_progress_unavailable))
        } else {
            Text(
                stringResource(
                    R.string.detail_season_progress,
                    season.progress.watchedEpisodes,
                    season.progress.trackableEpisodes
                )
            )
            WatchProgressBar(season.progress.fraction)
        }
    }
}

/**
 * The season's own checkbox, reading the same three states its episode rows do: complete, partly
 * watched, untouched. The label states the action rather than the season title, which the merged
 * header node next to it already announces.
 */
@Composable
private fun SeasonWatchedCheckbox(progress: SeasonProgress, enabled: Boolean, onToggleSeason: () -> Unit) {
    val watchStateDescription = stringResource(
        when {
            progress.isComplete -> R.string.detail_season_watched_state
            progress.watchedEpisodes > 0 -> R.string.detail_season_partial_state
            else -> R.string.detail_season_unwatched_state
        }
    )
    val actionLabel = stringResource(
        if (progress.isComplete) R.string.detail_mark_season_unwatched else R.string.detail_mark_season_watched
    )
    TriStateCheckbox(
        state = when {
            progress.isComplete -> ToggleableState.On
            progress.watchedEpisodes > 0 -> ToggleableState.Indeterminate
            else -> ToggleableState.Off
        },
        onClick = onToggleSeason,
        enabled = enabled,
        modifier = Modifier.semantics {
            contentDescription = actionLabel
            stateDescription = watchStateDescription
        }
    )
}

@Composable
private fun EpisodeRow(episode: TrackedEpisode, pending: Boolean, onToggle: () -> Unit) {
    val metadata = episode.episode
    val watched = episode.watchState is EpisodeWatchState.Watched
    val unavailable = episode.watchState == EpisodeWatchState.Unavailable
    val watchStateDescription = when {
        pending -> stringResource(R.string.detail_progress_updating)
        unavailable -> stringResource(R.string.detail_episode_future)
        watched -> stringResource(R.string.detail_episode_watched_state)
        else -> stringResource(R.string.detail_episode_unwatched_state)
    }
    Row(
        // The whole row is the toggle. Merging the descendants and leaving the Checkbox without a
        // callback of its own keeps this one TalkBack target instead of a row plus a checkbox.
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = watched,
                enabled = !pending && !unavailable,
                role = Role.Checkbox,
                onValueChange = { onToggle() }
            )
            .semantics(mergeDescendants = true) { stateDescription = watchStateDescription },
        horizontalArrangement = Arrangement.spacedBy(BingeeDimensions.elementSpacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val imageModifier = Modifier
            .width(120.dp)
            .aspectRatio(16f / 9f)
            .clip(MaterialTheme.shapes.small)
        val placeholder = painterResource(R.drawable.poster_placeholder)
        if (metadata.stillUrl == null) {
            Image(
                painter = placeholder,
                // The toggleable row owns the combined description of this episode.
                contentDescription = null,
                modifier = imageModifier,
                contentScale = ContentScale.Crop
            )
        } else {
            AsyncImage(
                model = metadata.stillUrl,
                contentDescription = null,
                placeholder = placeholder,
                error = placeholder,
                fallback = placeholder,
                modifier = imageModifier,
                contentScale = ContentScale.Crop
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.detail_episode_title, metadata.episodeNumber, metadata.title),
                style = MaterialTheme.typography.titleSmall
            )
            // Air date and runtime are secondary to the title, which the default body style left
            // larger than the title it belongs to.
            val metaStyle = MaterialTheme.typography.bodySmall
            val metaColor = MaterialTheme.colorScheme.onSurfaceVariant
            metadata.airDate?.let {
                Text(
                    stringResource(R.string.detail_episode_air_date, it.toString()),
                    style = metaStyle,
                    color = metaColor
                )
            } ?: Text(
                stringResource(R.string.detail_episode_air_date_unknown),
                style = metaStyle,
                color = metaColor
            )
            metadata.runtime?.let {
                Text(stringResource(R.string.detail_minutes, it.toMinutes()), style = metaStyle, color = metaColor)
            }
        }
        Checkbox(
            checked = watched,
            onCheckedChange = null,
            // The season header's checkbox is clickable, so Material centres its 24dp box in a 48dp
            // touch target, leaving 12dp of air on each side. This one has no callback of its own
            // and would otherwise sit flush against the card padding, 12dp to the right of the
            // checkbox it belongs under. Padding one side costs the title 12dp; centring it in a
            // 48dp box would cost 24dp and wrap episode titles that fit today at font scale 1.0.
            modifier = Modifier.padding(end = 12.dp),
            enabled = !pending && !unavailable
        )
    }
}
