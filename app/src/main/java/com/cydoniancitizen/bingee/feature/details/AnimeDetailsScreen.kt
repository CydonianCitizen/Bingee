package com.cydoniancitizen.bingee.feature.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cydoniancitizen.bingee.R
import com.cydoniancitizen.bingee.core.designsystem.component.ErrorState
import com.cydoniancitizen.bingee.core.designsystem.component.LoadingState
import com.cydoniancitizen.bingee.core.designsystem.component.MediaPoster
import com.cydoniancitizen.bingee.core.designsystem.component.OfflineBanner
import com.cydoniancitizen.bingee.core.designsystem.theme.BingeeDimensions
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import com.cydoniancitizen.bingee.core.model.AnimeDetails
import com.cydoniancitizen.bingee.core.model.AnimeFormat
import com.cydoniancitizen.bingee.core.model.AnimeProgressState
import com.cydoniancitizen.bingee.core.model.AnimeRelation
import com.cydoniancitizen.bingee.core.model.AnimeWatchProgress
import com.cydoniancitizen.bingee.core.model.CacheFreshness
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.core.result.AppError
import com.cydoniancitizen.bingee.core.ui.toUiError
import java.time.LocalDate

@Composable
internal fun AnimeDetailsScreen(
    onBack: () -> Unit,
    onOpenRelated: (AnimeRelation) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AnimeDetailsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    AnimeDetailsContent(
        state = state,
        onBack = onBack,
        onRefresh = viewModel::refresh,
        onRetry = viewModel::retry,
        onToggleLibrary = viewModel::toggleLibrary,
        onToggleFavorite = viewModel::toggleFavorite,
        onSetWatchedDate = viewModel::setWatchedDate,
        onToggleMovieWatched = viewModel::toggleMovieWatched,
        onToggleSeriesWatched = viewModel::toggleSeriesWatched,
        onIncrement = viewModel::increment,
        onDecrement = viewModel::decrement,
        onSetCount = viewModel::setCount,
        onComplete = viewModel::markComplete,
        onIncomplete = viewModel::markIncomplete,
        onOpenRelated = onOpenRelated,
        onSelectRating = viewModel::selectRating,
        onSaveRating = viewModel::setRating,
        onRemoveRating = viewModel::removeRating,
        onDismissRatingError = viewModel::dismissRatingError,
        modifier = modifier
    )
}

@Composable
internal fun AnimeDetailsContent(
    state: AnimeDetailsUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onToggleLibrary: () -> Unit,
    onToggleFavorite: () -> Unit = {},
    onSetWatchedDate: (LocalDate?) -> Unit = {},
    onToggleMovieWatched: () -> Unit = {},
    onToggleSeriesWatched: () -> Unit = {},
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onSetCount: (Int) -> Unit,
    onComplete: () -> Unit,
    onIncomplete: () -> Unit,
    onOpenRelated: (AnimeRelation) -> Unit,
    onSelectRating: (Int) -> Unit,
    onSaveRating: () -> Unit,
    onRemoveRating: () -> Unit,
    onDismissRatingError: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = BingeeDimensions.elementSpacing),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.detail_back))
            }
            Text(
                stringResource(R.string.anime_detail_title),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge
            )
            IconButton(
                onClick = onToggleFavorite,
                enabled = !state.favoriteUpdating && state.isInLibrary != null
            ) {
                Icon(
                    imageVector = if (state.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = stringResource(
                        if (state.isFavorite) R.string.favorite_remove else R.string.favorite_add
                    ),
                    tint = if (state.isFavorite) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
            }
            if (state.refreshing) {
                CircularProgressIndicator()
            } else {
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, stringResource(R.string.detail_refresh))
                }
            }
        }
        when (val content = state.content) {
            AnimeDetailContentState.Loading -> LoadingState(stringResource(R.string.detail_loading))
            is AnimeDetailContentState.Error -> ErrorState(
                title = stringResource(R.string.detail_error_title),
                message = stringResource(content.error.toUiError().messageRes),
                retryLabel = if (content.error.isRetryable) stringResource(R.string.action_retry) else null,
                onRetry = if (content.error.isRetryable) onRetry else null
            )
            is AnimeDetailContentState.Content -> AnimeDetailBody(
                details = content.cached.details,
                stale = content.cached.freshness == CacheFreshness.STALE,
                refreshError = state.refreshError,
                isInLibrary = state.isInLibrary,
                libraryUpdating = state.libraryUpdating,
                watchedDate = state.watchedDate,
                watchedDateUpdating = state.watchedDateUpdating,
                progress = state.progress,
                progressUpdating = state.progressUpdating,
                progressError = state.progressError,
                rating = state.rating,
                onToggleLibrary = onToggleLibrary,
                onSetWatchedDate = onSetWatchedDate,
                onToggleMovieWatched = onToggleMovieWatched,
                onToggleSeriesWatched = onToggleSeriesWatched,
                onIncrement = onIncrement,
                onDecrement = onDecrement,
                onSetCount = onSetCount,
                onComplete = onComplete,
                onIncomplete = onIncomplete,
                onOpenRelated = onOpenRelated,
                onSelectRating = onSelectRating,
                onSaveRating = onSaveRating,
                onRemoveRating = onRemoveRating,
                onDismissRatingError = onDismissRatingError
            )
        }
    }
}

@Composable
private fun AnimeDetailBody(
    details: AnimeDetails,
    stale: Boolean,
    refreshError: AppError?,
    isInLibrary: Boolean?,
    libraryUpdating: Boolean,
    watchedDate: LocalDate?,
    watchedDateUpdating: Boolean,
    progress: AnimeWatchProgress?,
    progressUpdating: Boolean,
    progressError: AppError?,
    rating: DetailRatingState,
    onToggleLibrary: () -> Unit,
    onSetWatchedDate: (LocalDate?) -> Unit,
    onToggleMovieWatched: () -> Unit,
    onToggleSeriesWatched: () -> Unit,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onSetCount: (Int) -> Unit,
    onComplete: () -> Unit,
    onIncomplete: () -> Unit,
    onOpenRelated: (AnimeRelation) -> Unit,
    onSelectRating: (Int) -> Unit,
    onSaveRating: () -> Unit,
    onRemoveRating: () -> Unit,
    onDismissRatingError: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(BingeeDimensions.screenPadding),
        verticalArrangement = Arrangement.spacedBy(BingeeDimensions.contentSpacing)
    ) {
        if (refreshError != null) {
            OfflineBanner(
                stringResource(R.string.detail_refresh_failed, stringResource(refreshError.toUiError().messageRes))
            )
        } else if (stale) {
            OfflineBanner(stringResource(R.string.detail_stale_data))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(BingeeDimensions.contentSpacing)) {
            MediaPoster(details.title, details.posterUrl)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(BingeeDimensions.elementSpacing)) {
                Text(details.title, Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineMedium)
                details.englishTitle?.takeIf { it != details.title }?.let { Text(it) }
                details.japaneseTitle?.let { Text(it) }
                Text(details.format.name.replace('_', ' '))
                Text(details.status.name.replace('_', ' '))
                details.episodeCount?.let { Text(stringResource(R.string.anime_episode_total, it)) }
                details.startDate?.let { Text(stringResource(R.string.anime_aired_from, it.toString())) }
                details.endDate?.let { Text(stringResource(R.string.anime_aired_to, it.toString())) }
            }
        }
        Button(
            onClick = onToggleLibrary,
            enabled = isInLibrary != null && !libraryUpdating,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                stringResource(
                    if (isInLibrary ==
                        true
                    ) {
                        R.string.library_action_remove
                    } else {
                        R.string.library_action_add
                    }
                )
            )
        }
        details.season?.let { Text(stringResource(R.string.anime_season_year, it, details.year ?: "")) }
        details.duration?.let { DetailLine(stringResource(R.string.anime_duration), it) }
        details.providerScore?.let {
            DetailLine(stringResource(R.string.anime_provider_score), stringResource(R.string.anime_score_value, it))
        }
        Text(stringResource(R.string.anime_provider_attribution), style = MaterialTheme.typography.labelMedium)
        RatingSection(
            state = rating,
            onSelect = onSelectRating,
            onSave = onSaveRating,
            onRemove = onRemoveRating,
            onDismissError = onDismissRatingError
        )
        AnimeProgressSection(
            details,
            progress,
            progressUpdating,
            progressError,
            onIncrement,
            onDecrement,
            onSetCount,
            onComplete,
            onIncomplete
        )
        val mediaType = if (details.format == AnimeFormat.MOVIE) MediaType.MOVIE else MediaType.SERIES
        AnimeWatchedDateSection(
            watchedDate = watchedDate,
            isUpdating = watchedDateUpdating,
            releaseDate = details.startDate,
            mediaType = mediaType,
            onSetWatchedDate = onSetWatchedDate
        )
        details.synopsis?.let {
            Text(stringResource(R.string.detail_overview), fontWeight = FontWeight.Bold)
            Text(it)
        }
        if (details.relations.isNotEmpty()) {
            Text(stringResource(R.string.anime_related), Modifier.semantics { heading() }, fontWeight = FontWeight.Bold)
            details.relations.forEach { relation ->
                OutlinedButton(onClick = { onOpenRelated(relation) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.anime_relation, relation.relation, relation.title))
                }
            }
            Text(stringResource(R.string.anime_relations_separate), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun AnimeProgressSection(
    details: AnimeDetails,
    progress: AnimeWatchProgress?,
    updating: Boolean,
    error: AppError?,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onSetCount: (Int) -> Unit,
    onComplete: () -> Unit,
    onIncomplete: () -> Unit
) {
    val count = progress?.watchedEpisodes ?: 0
    val state = progress?.state(details.episodeCount) ?: AnimeProgressState.NOT_STARTED
    Text(stringResource(R.string.anime_progress), Modifier.semantics { heading() }, fontWeight = FontWeight.Bold)
    if (details.format == AnimeFormat.MOVIE) {
        val movieStateDescription = stringResource(
            if (state == AnimeProgressState.COMPLETED) {
                R.string.library_progress_watched
            } else {
                R.string.library_progress_unwatched
            }
        )
        Button(
            onClick = if (state == AnimeProgressState.COMPLETED) onIncomplete else onComplete,
            enabled = !updating,
            modifier = Modifier.semantics {
                stateDescription = movieStateDescription
            }
        ) {
            Text(
                stringResource(
                    if (state ==
                        AnimeProgressState.COMPLETED
                    ) {
                        R.string.anime_unwatched
                    } else {
                        R.string.anime_watched
                    }
                )
            )
        }
    } else {
        val progressStateDescription = stringResource(
            when (state) {
                AnimeProgressState.NOT_STARTED -> R.string.library_state_not_started
                AnimeProgressState.IN_PROGRESS -> R.string.library_state_in_progress
                AnimeProgressState.COMPLETED -> R.string.library_state_completed
            }
        )
        Text(
            if (details.episodeCount == null) {
                stringResource(R.string.anime_progress_unknown, count)
            } else {
                stringResource(R.string.anime_progress_known, count, details.episodeCount)
            },
            Modifier.semantics { stateDescription = progressStateDescription }
        )
        val decreaseDescription = stringResource(R.string.anime_progress_decrease)
        val increaseDescription = stringResource(R.string.anime_progress_increase)
        val decreaseDisabledDescription = stringResource(R.string.anime_progress_decrease_disabled)
        Row(horizontalArrangement = Arrangement.spacedBy(BingeeDimensions.elementSpacing)) {
            OutlinedButton(
                onClick = onDecrement,
                enabled = !updating && count > 0,
                modifier = Modifier.semantics {
                    contentDescription = decreaseDescription
                    if (count == 0) {
                        stateDescription = decreaseDisabledDescription
                    }
                }
            ) { Text("−") }
            OutlinedButton(
                onClick = onIncrement,
                enabled = !updating && count < AnimeWatchProgress.MAX_WATCHED_EPISODES,
                modifier = Modifier.semantics {
                    contentDescription = increaseDescription
                }
            ) { Text("+") }
        }
        var direct by remember(count) { mutableStateOf(count.toString()) }
        OutlinedTextField(
            value = direct,
            onValueChange = { direct = it.filter(Char::isDigit).take(6) },
            label = { Text(stringResource(R.string.anime_progress_edit)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true
        )
        Button(
            onClick = { direct.toIntOrNull()?.let(onSetCount) },
            enabled = !updating && direct.toIntOrNull() != null
        ) { Text(stringResource(R.string.action_save)) }
        Button(
            onClick = if (state == AnimeProgressState.COMPLETED) onIncomplete else onComplete,
            enabled = !updating
        ) {
            Text(
                stringResource(
                    if (state ==
                        AnimeProgressState.COMPLETED
                    ) {
                        R.string.anime_mark_incomplete
                    } else {
                        R.string.anime_mark_complete
                    }
                )
            )
        }
        if (details.episodeCount == null) Text(stringResource(R.string.anime_unknown_total_explanation))
    }
    error?.let {
        Text(
            stringResource(it.toUiError().messageRes),
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            color = MaterialTheme.colorScheme.error
        )
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Text(label, fontWeight = FontWeight.Bold)
    Text(value)
}

@Composable
private fun AnimeWatchedDateSection(
    watchedDate: LocalDate?,
    isUpdating: Boolean,
    releaseDate: LocalDate?,
    mediaType: MediaType,
    onSetWatchedDate: (LocalDate?) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    val labelRes = if (mediaType == MediaType.MOVIE) R.string.watched_date_label else R.string.completion_date_label
    val editRes = if (mediaType == MediaType.MOVIE) R.string.watched_date_edit else R.string.completion_date_edit

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(BingeeDimensions.elementSpacing)
    ) {
        Text(
            text = stringResource(labelRes),
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleLarge
        )
        if (watchedDate != null) {
            Text(watchedDate.toString())
        }
        Row(horizontalArrangement = Arrangement.spacedBy(BingeeDimensions.elementSpacing)) {
            Button(
                onClick = { showDialog = true },
                enabled = !isUpdating
            ) {
                Text(stringResource(editRes))
            }
            if (watchedDate != null) {
                TextButton(
                    onClick = { onSetWatchedDate(null) },
                    enabled = !isUpdating
                ) {
                    Text(stringResource(R.string.action_dismiss))
                }
            }
        }
    }
    if (showDialog) {
        WatchedDateDialog(
            currentDate = watchedDate,
            releaseDate = releaseDate,
            mediaType = mediaType,
            onConfirm = { date ->
                onSetWatchedDate(date)
                showDialog = false
            },
            onDismiss = { showDialog = false }
        )
    }
}
