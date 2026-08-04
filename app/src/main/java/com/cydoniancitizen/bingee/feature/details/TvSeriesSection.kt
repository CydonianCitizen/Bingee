package com.cydoniancitizen.bingee.feature.details

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.cydoniancitizen.bingee.R
import com.cydoniancitizen.bingee.core.designsystem.theme.BingeeDimensions
import com.cydoniancitizen.bingee.core.model.CacheFreshness
import com.cydoniancitizen.bingee.core.model.CachedSeason
import com.cydoniancitizen.bingee.core.model.EpisodeWatchState
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
                Text(
                    if (content.progress.trackableEpisodes == 0) {
                        stringResource(R.string.library_progress_unavailable)
                    } else {
                        stringResource(
                            R.string.detail_series_progress,
                            content.progress.watchedEpisodes,
                            content.progress.trackableEpisodes,
                            content.progress.completedSeasons,
                            content.progress.trackableSeasons
                        )
                    }
                )
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
    val title =
        season.season.name
            ?: if (season.season.seasonNumber == 0) {
                stringResource(R.string.detail_specials_title)
            } else {
                stringResource(R.string.detail_season_fallback, season.season.seasonNumber)
            }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(BingeeDimensions.contentSpacing),
            verticalArrangement = Arrangement.spacedBy(BingeeDimensions.elementSpacing)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(
                    R.string.detail_season_number_and_count,
                    season.season.seasonNumber,
                    season.season.episodeCount
                )
            )
            Text(
                if (season.progress.trackableEpisodes == 0) {
                    stringResource(R.string.library_progress_unavailable)
                } else {
                    stringResource(
                        R.string.detail_season_progress,
                        season.progress.watchedEpisodes,
                        season.progress.trackableEpisodes
                    )
                }
            )
            if (season.episodeCacheFreshness == CacheFreshness.STALE) {
                Text(
                    text = stringResource(R.string.detail_season_stale),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(
                onClick = { onToggleExpanded(season) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    stringResource(
                        if (expanded) R.string.detail_collapse_season else R.string.detail_expand_season,
                        title
                    )
                )
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
                    if (load == SeasonLoadState.Idle) {
                        Button(
                            onClick = { onRetrySeason(season) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.detail_refresh_season))
                        }
                    }
                    Button(
                        onClick = { onToggleSeason(season) },
                        enabled = ref !in state.pendingSeasons && season.progress.trackableEpisodes > 0,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            stringResource(
                                when {
                                    ref in state.pendingSeasons -> R.string.detail_progress_updating
                                    season.progress.isComplete -> R.string.detail_mark_season_unwatched
                                    else -> R.string.detail_mark_season_watched
                                }
                            )
                        )
                    }
                    if (season.episodes.isNotEmpty()) {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp),
                            verticalArrangement = Arrangement.spacedBy(BingeeDimensions.elementSpacing)
                        ) {
                            items(
                                items = season.episodes,
                                key = {
                                    it.episode.externalRef.source.name +
                                        ":" +
                                        it.episode.externalRef.externalId
                                }
                            ) { episode ->
                                EpisodeRow(
                                    episode = episode,
                                    pending = episode.episode.externalRef in state.pendingEpisodes,
                                    onToggle = { onToggleEpisode(episode) }
                                )
                            }
                        }
                    }
                    if (season.episodes.isEmpty()) {
                        Text(stringResource(R.string.detail_episodes_empty))
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeRow(episode: TrackedEpisode, pending: Boolean, onToggle: () -> Unit) {
    val metadata = episode.episode
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = BingeeDimensions.elementSpacing),
        horizontalArrangement = Arrangement.spacedBy(BingeeDimensions.elementSpacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val imageModifier = Modifier.width(120.dp).height(68.dp)
        val placeholder = painterResource(R.drawable.poster_placeholder)
        if (metadata.stillUrl == null) {
            Image(
                painter = placeholder,
                contentDescription = stringResource(R.string.detail_episode_still_missing, metadata.title),
                modifier = imageModifier,
                contentScale = ContentScale.Crop
            )
        } else {
            AsyncImage(
                model = metadata.stillUrl,
                contentDescription = stringResource(R.string.detail_episode_still, metadata.title),
                placeholder = placeholder,
                error = placeholder,
                fallback = placeholder,
                modifier = imageModifier,
                contentScale = ContentScale.Crop
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(BingeeDimensions.elementSpacing)
        ) {
            Text(
                stringResource(R.string.detail_episode_title, metadata.episodeNumber, metadata.title),
                style = MaterialTheme.typography.titleSmall
            )
            metadata.airDate?.let { Text(stringResource(R.string.detail_episode_air_date, it.toString())) }
                ?: Text(stringResource(R.string.detail_episode_air_date_unknown))
            metadata.runtime?.let {
                Text(stringResource(R.string.detail_minutes, it.toMinutes()))
            }
            val unavailable = episode.watchState == EpisodeWatchState.Unavailable
            val watchStateDescription = when {
                pending -> stringResource(R.string.detail_progress_updating)
                unavailable -> stringResource(R.string.detail_episode_future)
                episode.watchState is EpisodeWatchState.Watched ->
                    stringResource(R.string.detail_episode_watched_state)
                else -> stringResource(R.string.detail_episode_unwatched_state)
            }
            Button(
                onClick = onToggle,
                enabled = !pending && !unavailable,
                modifier = Modifier.semantics { stateDescription = watchStateDescription }
            ) {
                Text(
                    stringResource(
                        when {
                            pending -> R.string.detail_progress_updating
                            unavailable -> R.string.detail_episode_future
                            episode.watchState is EpisodeWatchState.Watched -> R.string.detail_mark_unwatched
                            else -> R.string.detail_mark_watched
                        }
                    )
                )
            }
        }
    }
}
