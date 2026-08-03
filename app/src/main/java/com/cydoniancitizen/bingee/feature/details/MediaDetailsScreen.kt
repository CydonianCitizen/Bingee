package com.cydoniancitizen.bingee.feature.details

import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.cydoniancitizen.bingee.R
import com.cydoniancitizen.bingee.core.designsystem.component.ErrorState
import com.cydoniancitizen.bingee.core.designsystem.component.LoadingState
import com.cydoniancitizen.bingee.core.designsystem.component.MediaPoster
import com.cydoniancitizen.bingee.core.designsystem.component.OfflineBanner
import com.cydoniancitizen.bingee.core.designsystem.theme.BingeeDimensions
import com.cydoniancitizen.bingee.core.model.CacheFreshness
import com.cydoniancitizen.bingee.core.model.MediaDetails
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.core.model.ProductionStatus
import com.cydoniancitizen.bingee.core.result.AppError
import com.cydoniancitizen.bingee.core.ui.toUiError

@Composable
internal fun MediaDetailsScreen(
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MediaDetailsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    MediaDetailsContent(
        state = state,
        onBack = onBack,
        onRefresh = viewModel::refresh,
        onRetry = viewModel::retry,
        onToggleLibrary = viewModel::toggleLibrary,
        onToggleMovieWatched = viewModel::toggleMovieWatched,
        onToggleSeasonExpanded = viewModel::toggleSeasonExpanded,
        onRetrySeason = viewModel::retrySeason,
        onToggleEpisode = viewModel::toggleEpisode,
        onToggleSeasonWatched = viewModel::toggleSeasonWatched,
        onSelectRating = viewModel::selectRating,
        onSaveRating = viewModel::setRating,
        onRemoveRating = viewModel::removeRating,
        onDismissRatingError = viewModel::dismissRatingError,
        onDismissLibraryError = viewModel::dismissLibraryError,
        onDismissProgressError = viewModel::dismissProgressError,
        onOpenSettings = onOpenSettings,
        modifier = modifier
    )
}

@Composable
internal fun MediaDetailsContent(
    state: MediaDetailsUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onToggleLibrary: () -> Unit,
    onToggleMovieWatched: () -> Unit = {},
    onToggleSeasonExpanded: (com.cydoniancitizen.bingee.core.model.CachedSeason) -> Unit = {},
    onRetrySeason: (com.cydoniancitizen.bingee.core.model.CachedSeason) -> Unit = {},
    onToggleEpisode: (com.cydoniancitizen.bingee.core.model.TrackedEpisode) -> Unit = {},
    onToggleSeasonWatched: (com.cydoniancitizen.bingee.core.model.CachedSeason) -> Unit = {},
    onSelectRating: (Int) -> Unit = {},
    onSaveRating: () -> Unit = {},
    onRemoveRating: () -> Unit = {},
    onDismissRatingError: () -> Unit = {},
    onDismissLibraryError: () -> Unit,
    onDismissProgressError: () -> Unit = {},
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = BingeeDimensions.elementSpacing),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.detail_back))
            }
            Text(
                text = stringResource(R.string.detail_screen_title),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge
            )
            if (state.refresh == DetailRefreshState.Refreshing) {
                CircularProgressIndicator(modifier = Modifier.padding(BingeeDimensions.elementSpacing))
            } else if (state.content is DetailContentState.Content) {
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, stringResource(R.string.detail_refresh))
                }
            }
        }

        when (val content = state.content) {
            DetailContentState.Resolving,
            DetailContentState.Loading -> LoadingState(stringResource(R.string.detail_loading))
            is DetailContentState.Error -> FullDetailError(content.error, onRetry, onOpenSettings)
            is DetailContentState.Content -> DetailBody(
                details = content.cached.details,
                isStale = content.cached.freshness == CacheFreshness.STALE,
                refreshError = (state.refresh as? DetailRefreshState.Error)?.error,
                isInLibrary = state.isInLibrary,
                isLibraryUpdating = state.libraryAction == DetailLibraryActionState.UPDATING,
                libraryError = state.libraryError,
                onToggleLibrary = onToggleLibrary,
                movieProgress = state.movieProgress,
                series = state.series,
                progressError = state.progressError,
                rating = state.rating,
                onToggleMovieWatched = onToggleMovieWatched,
                onToggleSeasonExpanded = onToggleSeasonExpanded,
                onRetrySeason = onRetrySeason,
                onToggleEpisode = onToggleEpisode,
                onToggleSeasonWatched = onToggleSeasonWatched,
                onSelectRating = onSelectRating,
                onSaveRating = onSaveRating,
                onRemoveRating = onRemoveRating,
                onDismissRatingError = onDismissRatingError,
                onDismissLibraryError = onDismissLibraryError,
                onDismissProgressError = onDismissProgressError,
                onOpenSettings = onOpenSettings
            )
        }
    }
}

@Composable
private fun FullDetailError(error: AppError, onRetry: () -> Unit, onOpenSettings: () -> Unit) {
    val uiError = error.toUiError()
    Column(
        modifier = Modifier.fillMaxWidth().padding(BingeeDimensions.screenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(BingeeDimensions.elementSpacing)
    ) {
        ErrorState(
            title = stringResource(R.string.detail_error_title),
            message = stringResource(uiError.messageRes),
            retryLabel = if (uiError.canRetry) stringResource(R.string.action_retry) else null,
            onRetry = if (uiError.canRetry) onRetry else null
        )
        if (error == AppError.Unauthorized) {
            Button(onClick = onOpenSettings) { Text(stringResource(R.string.search_open_settings)) }
        }
    }
}

@Composable
private fun DetailBody(
    details: MediaDetails,
    isStale: Boolean,
    refreshError: AppError?,
    isInLibrary: Boolean?,
    isLibraryUpdating: Boolean,
    libraryError: AppError?,
    movieProgress: MovieProgressState,
    series: SeriesDetailUiState,
    progressError: AppError?,
    rating: DetailRatingState,
    onToggleLibrary: () -> Unit,
    onToggleMovieWatched: () -> Unit,
    onToggleSeasonExpanded: (com.cydoniancitizen.bingee.core.model.CachedSeason) -> Unit,
    onRetrySeason: (com.cydoniancitizen.bingee.core.model.CachedSeason) -> Unit,
    onToggleEpisode: (com.cydoniancitizen.bingee.core.model.TrackedEpisode) -> Unit,
    onToggleSeasonWatched: (com.cydoniancitizen.bingee.core.model.CachedSeason) -> Unit,
    onSelectRating: (Int) -> Unit,
    onSaveRating: () -> Unit,
    onRemoveRating: () -> Unit,
    onDismissRatingError: () -> Unit,
    onDismissLibraryError: () -> Unit,
    onDismissProgressError: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(BingeeDimensions.contentSpacing)
    ) {
        if (refreshError != null) {
            OfflineBanner(
                stringResource(R.string.detail_refresh_failed, stringResource(refreshError.toUiError().messageRes))
            )
        } else if (isStale) {
            OfflineBanner(stringResource(R.string.detail_stale_data))
        }
        DetailBackdrop(details)
        Column(
            modifier = Modifier.padding(horizontal = BingeeDimensions.screenPadding),
            verticalArrangement = Arrangement.spacedBy(BingeeDimensions.elementSpacing)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(BingeeDimensions.contentSpacing)) {
                MediaPoster(details.title, details.posterUrl)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(BingeeDimensions.elementSpacing)
                ) {
                    Text(
                        text = details.title,
                        modifier = Modifier.semantics { heading() },
                        style = MaterialTheme.typography.headlineMedium
                    )
                    details.originalTitle?.let {
                        Text(stringResource(R.string.detail_original_title, it))
                    }
                    Text(
                        stringResource(
                            if (details.mediaType == MediaType.MOVIE) {
                                R.string.library_type_movie
                            } else {
                                R.string.library_type_tv
                            }
                        )
                    )
                    details.releaseDate?.let { Text(stringResource(R.string.detail_date, it.toString())) }
                    Text(stringResource(statusString(details.productionStatus)))
                }
            }
            Button(
                onClick = onToggleLibrary,
                enabled = isInLibrary != null && !isLibraryUpdating,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    stringResource(
                        when {
                            isLibraryUpdating -> R.string.library_action_updating
                            isInLibrary == true -> R.string.library_action_remove
                            else -> R.string.library_action_add
                        }
                    )
                )
            }
            libraryError?.let { error ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(error.toUiError().messageRes),
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.error
                    )
                    TextButton(onClick = onDismissLibraryError) {
                        Text(stringResource(R.string.action_dismiss))
                    }
                }
            }
            progressError?.let { error ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(error.toUiError().messageRes),
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.error
                    )
                    TextButton(onClick = onDismissProgressError) {
                        Text(stringResource(R.string.action_dismiss))
                    }
                }
            }
            RatingSection(
                state = rating,
                onSelect = onSelectRating,
                onSave = onSaveRating,
                onRemove = onRemoveRating,
                onDismissError = onDismissRatingError
            )
            if (details.mediaType == MediaType.MOVIE) {
                MovieProgressSection(movieProgress, onToggleMovieWatched)
            } else {
                TvSeriesSection(
                    state = series,
                    onToggleExpanded = onToggleSeasonExpanded,
                    onRetrySeason = onRetrySeason,
                    onToggleEpisode = onToggleEpisode,
                    onToggleSeason = onToggleSeasonWatched,
                    onOpenSettings = onOpenSettings
                )
            }
            if (details.genres.isNotEmpty()) {
                DetailField(R.string.detail_genres, details.genres.joinToString { it.name })
            }
            details.runtime?.let {
                DetailField(R.string.detail_runtime, stringResource(R.string.detail_minutes, it.toMinutes()))
            }
            details.episodeRuntime?.let {
                DetailField(R.string.detail_episode_runtime, stringResource(R.string.detail_minutes, it.toMinutes()))
            }
            details.originalLanguage?.let {
                DetailField(R.string.detail_original_language, it)
            }
            details.overview?.let {
                Text(stringResource(R.string.detail_overview), fontWeight = FontWeight.Bold)
                Text(it, style = MaterialTheme.typography.bodyLarge)
            }
            if (refreshError == AppError.Unauthorized) {
                Button(onClick = onOpenSettings) { Text(stringResource(R.string.search_open_settings)) }
            }
            Box(modifier = Modifier.height(BingeeDimensions.screenPadding))
        }
    }
}

@Composable
private fun DetailBackdrop(details: MediaDetails) {
    val modifier = Modifier.fillMaxWidth().height(220.dp)
    val placeholder = painterResource(R.drawable.poster_placeholder)
    if (details.backdropUrl == null) {
        Image(
            painter = placeholder,
            contentDescription = stringResource(R.string.detail_backdrop_missing, details.title),
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
    } else {
        AsyncImage(
            model = details.backdropUrl,
            contentDescription = stringResource(R.string.detail_backdrop, details.title),
            placeholder = placeholder,
            error = placeholder,
            fallback = placeholder,
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
    }
}

@Composable
private fun DetailField(@StringRes labelRes: Int, value: String) {
    Text(stringResource(labelRes), fontWeight = FontWeight.Bold)
    Text(value)
}

private fun statusString(status: ProductionStatus): Int = when (status) {
    ProductionStatus.RUMORED -> R.string.detail_status_rumored
    ProductionStatus.PLANNED -> R.string.detail_status_planned
    ProductionStatus.IN_PRODUCTION -> R.string.detail_status_in_production
    ProductionStatus.POST_PRODUCTION -> R.string.detail_status_post_production
    ProductionStatus.RELEASED -> R.string.detail_status_released
    ProductionStatus.RETURNING_SERIES -> R.string.detail_status_returning
    ProductionStatus.ENDED -> R.string.detail_status_ended
    ProductionStatus.CANCELED -> R.string.detail_status_canceled
    ProductionStatus.PILOT -> R.string.detail_status_pilot
    ProductionStatus.UNKNOWN -> R.string.detail_status_unknown
}
