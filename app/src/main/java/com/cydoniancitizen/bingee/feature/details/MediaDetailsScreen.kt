package com.cydoniancitizen.bingee.feature.details

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cydoniancitizen.bingee.R
import com.cydoniancitizen.bingee.core.designsystem.component.ErrorState
import com.cydoniancitizen.bingee.core.designsystem.component.LoadingState
import com.cydoniancitizen.bingee.core.designsystem.component.OfflineBanner
import com.cydoniancitizen.bingee.core.designsystem.theme.BingeeDimensions
import com.cydoniancitizen.bingee.core.model.CacheFreshness
import com.cydoniancitizen.bingee.core.model.MediaDetails
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.core.model.ProductionStatus
import com.cydoniancitizen.bingee.core.model.WatchedDateChoice
import com.cydoniancitizen.bingee.core.model.WatchedDateValidationResult
import com.cydoniancitizen.bingee.core.model.isValid
import com.cydoniancitizen.bingee.core.model.validateWatchedDate
import com.cydoniancitizen.bingee.core.result.AppError
import com.cydoniancitizen.bingee.core.ui.toUiError
import java.time.LocalDate

/** Scroll distance over which the transparent top app bar fades into an opaque one. */
private val BarCollapseDistance = 160.dp

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
        onToggleFavorite = viewModel::toggleFavorite,
        onToggleSeriesAbandoned = viewModel::toggleSeriesAbandoned,
        onSetWatchedDate = viewModel::setWatchedDate,
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
    onDismissLibraryError: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    onToggleFavorite: () -> Unit = {},
    onToggleSeriesAbandoned: () -> Unit = {},
    onSetWatchedDate: (LocalDate?) -> Unit = {},
    onToggleMovieWatched: () -> Unit = {},
    onToggleSeasonExpanded: (com.cydoniancitizen.bingee.core.model.CachedSeason) -> Unit = {},
    onRetrySeason: (com.cydoniancitizen.bingee.core.model.CachedSeason) -> Unit = {},
    onToggleEpisode: (com.cydoniancitizen.bingee.core.model.TrackedEpisode) -> Unit = {},
    onToggleSeasonWatched: (com.cydoniancitizen.bingee.core.model.CachedSeason) -> Unit = {},
    onSelectRating: (Int) -> Unit = {},
    onSaveRating: () -> Unit = {},
    onRemoveRating: () -> Unit = {},
    onDismissRatingError: () -> Unit = {},
    onDismissProgressError: () -> Unit = {}
) {
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val content = state.content
    val details = (content as? DetailContentState.Content)?.cached?.details
    val collapseThresholdPx = with(LocalDensity.current) { BarCollapseDistance.toPx() }
    val collapseFraction by remember(details, collapseThresholdPx) {
        derivedStateOf {
            when {
                // Loading and error states have no artwork behind the bar, so it starts opaque.
                details == null -> 1f
                listState.firstVisibleItemIndex > 0 -> 1f
                else -> (listState.firstVisibleItemScrollOffset / collapseThresholdPx).coerceIn(0f, 1f)
            }
        }
    }

    val libraryErrorText = state.libraryError?.let { stringResource(it.toUiError().messageRes) }
    LaunchedEffect(libraryErrorText) {
        if (libraryErrorText != null) {
            snackbarHostState.showSnackbar(libraryErrorText)
            onDismissLibraryError()
        }
    }
    val progressErrorText = state.progressError?.let { stringResource(it.toUiError().messageRes) }
    LaunchedEffect(progressErrorText) {
        if (progressErrorText != null) {
            snackbarHostState.showSnackbar(progressErrorText)
            onDismissProgressError()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        // The app shell already applies the status bar inset to the nav host.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            DetailTopBar(
                title = details?.title ?: stringResource(R.string.detail_screen_title),
                collapseFraction = collapseFraction,
                state = state,
                onBack = onBack,
                onRefresh = onRefresh,
                onToggleFavorite = onToggleFavorite
            )
        }
    ) { innerPadding ->
        when (content) {
            DetailContentState.Resolving,
            DetailContentState.Loading -> LoadingState(
                message = stringResource(R.string.detail_loading),
                modifier = Modifier.padding(innerPadding)
            )
            is DetailContentState.Error -> FullDetailError(
                error = content.error,
                onRetry = onRetry,
                onOpenSettings = onOpenSettings,
                modifier = Modifier.padding(innerPadding)
            )
            is DetailContentState.Content -> DetailBody(
                listState = listState,
                // The hero scrolls underneath the transparent bar, so the body drops the top inset
                // and keeps only the bottom one.
                contentPadding = PaddingValues(
                    bottom = innerPadding.calculateBottomPadding() + BingeeDimensions.screenPadding
                ),
                details = content.cached.details,
                isStale = content.cached.freshness == CacheFreshness.STALE,
                refreshError = (state.refresh as? DetailRefreshState.Error)?.error,
                today = state.today,
                isInLibrary = state.isInLibrary,
                isAbandoned = state.isAbandoned,
                isLibraryUpdating = state.libraryAction == DetailLibraryActionState.UPDATING,
                watchedDate = state.watchedDate,
                watchedDateUpdating = state.watchedDateUpdating,
                onSetWatchedDate = onSetWatchedDate,
                onToggleLibrary = onToggleLibrary,
                onToggleSeriesAbandoned = onToggleSeriesAbandoned,
                movieProgress = state.movieProgress,
                series = state.series,
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
                onOpenSettings = onOpenSettings
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailTopBar(
    title: String,
    collapseFraction: Float,
    state: MediaDetailsUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    // Icons start white over the artwork and land on onSurface once the bar is opaque, so they stay
    // legible against a bright backdrop and against the bar's own surface alike.
    val iconTint = lerp(Color.White, scheme.onSurface, collapseFraction)
    val favoriteTint = lerp(
        Color.White,
        if (state.isFavorite) scheme.error else scheme.onSurface,
        collapseFraction
    )
    TopAppBar(
        title = {
            // Composed only once the bar is opaque: while the hero title is the one on screen, a
            // second node carrying the same text would make the title assertions ambiguous.
            if (collapseFraction >= 1f) {
                Text(text = title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.detail_back),
                    tint = iconTint
                )
            }
        },
        actions = {
            IconButton(
                onClick = onToggleFavorite,
                enabled = !state.favoriteUpdating && state.isInLibrary != null
            ) {
                Icon(
                    imageVector = if (state.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = stringResource(
                        if (state.isFavorite) R.string.favorite_remove else R.string.favorite_add
                    ),
                    tint = favoriteTint
                )
            }
            if (state.refresh == DetailRefreshState.Refreshing) {
                CircularProgressIndicator(modifier = Modifier.padding(BingeeDimensions.elementSpacing))
            } else if (state.content is DetailContentState.Content) {
                IconButton(onClick = onRefresh) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.detail_refresh),
                        tint = iconTint
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = scheme.surface.copy(alpha = collapseFraction),
            titleContentColor = scheme.onSurface
        ),
        // The app shell already applies the status bar inset to the nav host.
        windowInsets = WindowInsets(0, 0, 0, 0)
    )
}

@Composable
private fun FullDetailError(
    error: AppError,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiError = error.toUiError()
    Column(
        modifier = modifier.fillMaxWidth().padding(BingeeDimensions.screenPadding),
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
    listState: LazyListState,
    contentPadding: PaddingValues,
    details: MediaDetails,
    isStale: Boolean,
    refreshError: AppError?,
    today: LocalDate,
    isInLibrary: Boolean?,
    isAbandoned: Boolean,
    isLibraryUpdating: Boolean,
    watchedDate: LocalDate?,
    watchedDateUpdating: Boolean,
    onSetWatchedDate: (LocalDate?) -> Unit,
    movieProgress: MovieProgressState,
    series: SeriesDetailUiState,
    rating: DetailRatingState,
    onToggleLibrary: () -> Unit,
    onToggleSeriesAbandoned: () -> Unit,
    onToggleMovieWatched: () -> Unit,
    onToggleSeasonExpanded: (com.cydoniancitizen.bingee.core.model.CachedSeason) -> Unit,
    onRetrySeason: (com.cydoniancitizen.bingee.core.model.CachedSeason) -> Unit,
    onToggleEpisode: (com.cydoniancitizen.bingee.core.model.TrackedEpisode) -> Unit,
    onToggleSeasonWatched: (com.cydoniancitizen.bingee.core.model.CachedSeason) -> Unit,
    onSelectRating: (Int) -> Unit,
    onSaveRating: () -> Unit,
    onRemoveRating: () -> Unit,
    onDismissRatingError: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val sectionModifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = BingeeDimensions.screenPadding)
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(BingeeDimensions.contentSpacing)
    ) {
        item(key = "hero") { DetailHero(details) }
        if (refreshError != null || isStale) {
            item(key = "banner") {
                val message = if (refreshError != null) {
                    stringResource(
                        R.string.detail_refresh_failed,
                        stringResource(refreshError.toUiError().messageRes)
                    )
                } else {
                    stringResource(R.string.detail_stale_data)
                }
                OfflineBanner(message = message, modifier = sectionModifier)
            }
        }
        item(key = "chips") {
            DetailChips(
                statusLabel = stringResource(statusString(details.productionStatus)),
                genres = details.genres,
                modifier = sectionModifier
            )
        }
        item(key = "library") {
            val label = stringResource(
                when {
                    isLibraryUpdating -> R.string.library_action_updating
                    isInLibrary == true -> R.string.library_action_remove
                    else -> R.string.library_action_add
                }
            )
            val libraryEnabled = isInLibrary != null && !isLibraryUpdating
            // Adding a title is what this screen is for, so it keeps the filled button. Removing one
            // discards the user's own record and has no undo, so it steps down to tonal rather than
            // staying the loudest control on the page.
            if (isInLibrary == true) {
                FilledTonalButton(
                    onClick = onToggleLibrary,
                    enabled = libraryEnabled,
                    modifier = sectionModifier
                ) {
                    Text(label)
                }
            } else {
                Button(onClick = onToggleLibrary, enabled = libraryEnabled, modifier = sectionModifier) {
                    Text(label)
                }
            }
        }
        details.overview?.let { overview ->
            item(key = "overview") {
                Column(
                    modifier = sectionModifier,
                    verticalArrangement = Arrangement.spacedBy(BingeeDimensions.elementSpacing)
                ) {
                    Text(
                        text = stringResource(R.string.detail_overview),
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(overview, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        item(key = "rating") {
            RatingSection(
                state = rating,
                onSelect = onSelectRating,
                onSave = onSaveRating,
                onRemove = onRemoveRating,
                onDismissError = onDismissRatingError,
                modifier = sectionModifier
            )
        }
        item(key = "progress") {
            Column(
                modifier = sectionModifier,
                verticalArrangement = Arrangement.spacedBy(BingeeDimensions.elementSpacing)
            ) {
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
                    if (isInLibrary == true) {
                        TextButton(onClick = onToggleSeriesAbandoned, enabled = !isLibraryUpdating) {
                            Text(
                                stringResource(
                                    if (isAbandoned) {
                                        R.string.series_tracking_restore
                                    } else {
                                        R.string.series_tracking_abandon
                                    }
                                )
                            )
                        }
                    }
                }
            }
        }
        item(key = "watchedDate") {
            WatchedDateSection(
                watchedDate = watchedDate,
                today = today,
                isUpdating = watchedDateUpdating,
                releaseDate = details.releaseDate,
                mediaType = details.mediaType,
                onSetWatchedDate = onSetWatchedDate,
                modifier = sectionModifier
            )
        }
        item(key = "metadata") {
            Column(
                modifier = sectionModifier,
                verticalArrangement = Arrangement.spacedBy(BingeeDimensions.elementSpacing)
            ) {
                details.episodeRuntime?.let {
                    DetailField(
                        labelRes = R.string.detail_episode_runtime,
                        value = stringResource(R.string.detail_minutes, it.toMinutes())
                    )
                }
                details.releaseDate?.let {
                    DetailField(R.string.detail_date, it.localizedMedium())
                }
                details.originalLanguage?.let {
                    DetailField(R.string.detail_original_language, it)
                }
                if (refreshError == AppError.Unauthorized) {
                    Button(onClick = onOpenSettings) { Text(stringResource(R.string.search_open_settings)) }
                }
            }
        }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WatchedDateSection(
    watchedDate: LocalDate?,
    today: LocalDate,
    isUpdating: Boolean,
    releaseDate: LocalDate?,
    mediaType: MediaType,
    onSetWatchedDate: (LocalDate?) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDialog by remember { mutableStateOf(false) }
    val labelRes = if (mediaType == MediaType.MOVIE) R.string.watched_date_label else R.string.completion_date_label
    val editRes = if (mediaType == MediaType.MOVIE) R.string.watched_date_edit else R.string.completion_date_edit

    Column(
        modifier = modifier.fillMaxWidth(),
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
            today = today,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WatchedDateDialog(
    currentDate: LocalDate?,
    today: LocalDate,
    releaseDate: LocalDate?,
    mediaType: MediaType = MediaType.MOVIE,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedChoice by remember { mutableStateOf(WatchedDateChoice.TODAY) }
    var customDate by remember { mutableStateOf(currentDate ?: today) }
    var showCustomDatePicker by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }

    val resolvedDate = when (selectedChoice) {
        WatchedDateChoice.TODAY -> today
        WatchedDateChoice.RELEASE_DATE -> releaseDate ?: today
        WatchedDateChoice.CUSTOM_DATE -> customDate
    }

    val titleRes = if (mediaType == MediaType.MOVIE) R.string.watched_date_label else R.string.completion_date_label
    val futureDateError = stringResource(R.string.watched_date_future_error, today)
    val beforeReleaseDateError = releaseDate?.let {
        stringResource(R.string.watched_date_before_release_error, it)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(titleRes)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(BingeeDimensions.elementSpacing)) {
                WatchedDateChoice.entries.forEach { choice ->
                    if (choice == WatchedDateChoice.RELEASE_DATE && releaseDate == null) return@forEach
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = selectedChoice == choice,
                                onClick = {
                                    selectedChoice = choice
                                    validationError = null
                                    if (choice == WatchedDateChoice.CUSTOM_DATE) {
                                        showCustomDatePicker = true
                                    }
                                },
                                role = Role.RadioButton
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selectedChoice == choice, onClick = null)
                        Text(
                            text = when (choice) {
                                WatchedDateChoice.TODAY -> stringResource(R.string.watched_date_today)
                                WatchedDateChoice.RELEASE_DATE -> stringResource(R.string.watched_date_release_date) +
                                    ": $releaseDate"
                                WatchedDateChoice.CUSTOM_DATE -> stringResource(R.string.watched_date_custom) +
                                    ": $customDate"
                            },
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
                if (selectedChoice == WatchedDateChoice.CUSTOM_DATE) {
                    TextButton(onClick = { showCustomDatePicker = true }) {
                        Text(stringResource(R.string.watched_date_custom) + " ($customDate)")
                    }
                }
                validationError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val result = validateWatchedDate(resolvedDate, releaseDate, today)
                if (result.isValid()) {
                    onConfirm(resolvedDate)
                } else {
                    validationError = when (result) {
                        is WatchedDateValidationResult.FutureDateRejected ->
                            futureDateError
                        is WatchedDateValidationResult.DatePrecedesReleaseRejected ->
                            beforeReleaseDateError
                        else -> null
                    }
                }
            }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )

    if (showCustomDatePicker) {
        val initialEpochMillis = customDate.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
        val datePickerState = androidx.compose.material3.rememberDatePickerState(
            initialSelectedDateMillis = initialEpochMillis
        )
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = { showCustomDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        customDate = java.time.Instant.ofEpochMilli(millis)
                            .atZone(java.time.ZoneOffset.UTC)
                            .toLocalDate()
                    }
                    showCustomDatePicker = false
                }) {
                    Text(stringResource(R.string.action_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomDatePicker = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        ) {
            androidx.compose.material3.DatePicker(state = datePickerState)
        }
    }
}
