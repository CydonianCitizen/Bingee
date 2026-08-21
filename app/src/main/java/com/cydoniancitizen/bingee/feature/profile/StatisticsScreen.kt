package com.cydoniancitizen.bingee.feature.profile

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cydoniancitizen.bingee.R
import com.cydoniancitizen.bingee.core.designsystem.component.ErrorState
import com.cydoniancitizen.bingee.core.designsystem.component.LoadingState
import com.cydoniancitizen.bingee.core.designsystem.component.MediaPoster
import com.cydoniancitizen.bingee.core.designsystem.theme.BingeeChartColors
import com.cydoniancitizen.bingee.core.designsystem.theme.BingeeDimensions
import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.core.model.PersonalViewingEntry
import com.cydoniancitizen.bingee.core.ui.toUiError
import com.cydoniancitizen.bingee.domain.model.GenreStatistic
import com.cydoniancitizen.bingee.domain.model.MonthlyViewingData
import com.cydoniancitizen.bingee.domain.model.PersonalRatingStatistics
import com.cydoniancitizen.bingee.domain.model.RatingHistogramBucket
import com.cydoniancitizen.bingee.domain.model.StatisticsMediaScope
import com.cydoniancitizen.bingee.domain.model.TasteStatistics
import com.cydoniancitizen.bingee.domain.model.ViewingDurationLabels
import com.cydoniancitizen.bingee.domain.model.WatchedStatistics
import com.cydoniancitizen.bingee.domain.model.formatPersonalRatingAverage
import com.cydoniancitizen.bingee.domain.model.formatViewingDuration
import com.cydoniancitizen.bingee.domain.model.relativeGenreNormalization
import com.cydoniancitizen.bingee.domain.model.relativeRatingNormalization
import com.cydoniancitizen.bingee.domain.model.relativeViewingNormalization
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private const val RADAR_RING_COUNT = 4
private val RADAR_BASE_HEIGHT = 260.dp
private val RADAR_LABEL_MIN_WIDTH = 88.dp
private val RADAR_LABEL_MAX_WIDTH = 112.dp
private const val RADAR_LABEL_MAX_LINES = 3
private const val RADAR_MAX_FONT_SCALE = 1.5f

// Below this an axis is close to the centre line, so it needs no gutter on that side.
private const val RADAR_MIN_AXIS_PROJECTION = 0.2f
private val CHART_MIN_SLOT_WIDTH = 48.dp
private val CHART_SLOT_SPACING = 4.dp
private val CHART_EDGE_PADDING = 4.dp

private fun chartSlotWidth(availableWidth: Dp, itemCount: Int): Dp {
    if (itemCount == 0) return CHART_MIN_SLOT_WIDTH
    val spacingWidth = CHART_SLOT_SPACING * (itemCount - 1)
    return maxOf(
        CHART_MIN_SLOT_WIDTH,
        (availableWidth - CHART_EDGE_PADDING * 2 - spacingWidth) / itemCount
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StatisticsScreen(
    onBack: () -> Unit,
    viewModel: ProfileViewModel,
    onOpenDetails: (ExternalMediaRef, MediaType) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val statisticsError = state.statisticsError
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.statistics_title),
                        modifier = Modifier.semantics { heading() }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.detail_back)
                        )
                    }
                },
                // The app shell already applies the status bar inset to the nav host.
                windowInsets = WindowInsets(0, 0, 0, 0)
            )
        }
    ) { innerPadding ->
        when {
            statisticsError != null -> ErrorState(
                title = stringResource(R.string.statistics_error_title),
                message = stringResource(statisticsError.toUiError().messageRes),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(BingeeDimensions.screenPadding),
                retryLabel = stringResource(R.string.action_retry),
                onRetry = viewModel::retryStatistics
            )
            state.isStatisticsLoading && state.tasteStatistics.rankedGenres.isEmpty() -> LoadingState(
                message = stringResource(R.string.statistics_loading),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
            else -> StatisticsContent(
                statistics = state.statistics,
                tasteStatistics = state.tasteStatistics,
                onScopeChanged = viewModel::setStatisticsMediaScope,
                selectedMonth = state.selectedStatisticsMonth,
                onYearChanged = viewModel::setStatisticsViewingYear,
                onMonthSelected = viewModel::setStatisticsViewingMonth,
                onOpenDetails = onOpenDetails,
                modifier = Modifier.padding(innerPadding)
            )
        }
    }
}

@Composable
internal fun StatisticsContent(
    statistics: WatchedStatistics = WatchedStatistics(),
    tasteStatistics: TasteStatistics,
    onScopeChanged: (StatisticsMediaScope) -> Unit,
    selectedMonth: Int? = null,
    onYearChanged: (Int) -> Unit = {},
    onMonthSelected: (Int?) -> Unit = {},
    onOpenDetails: (ExternalMediaRef, MediaType) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val radarGenres = tasteStatistics.radarGenres
    val normalizedValues = relativeGenreNormalization(radarGenres.map(GenreStatistic::titleCount))
    var selectedRating by remember { mutableStateOf<Int?>(null) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = BingeeDimensions.screenPadding),
        contentPadding = PaddingValues(top = 12.dp, bottom = BingeeDimensions.screenPadding),
        verticalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.statistics_taste),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() }
                )
                StatisticsScopeSelector(
                    selectedScope = tasteStatistics.scope,
                    onScopeChanged = onScopeChanged
                )
                TasteRadarChart(
                    genres = radarGenres,
                    normalizedValues = normalizedValues
                )
                if (radarGenres.size < 3) {
                    Text(
                        text = stringResource(R.string.statistics_not_enough_data),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        item {
            ViewingSection(
                statistics = statistics,
                selectedMonth = selectedMonth,
                onYearChanged = onYearChanged,
                onMonthSelected = onMonthSelected
            )
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = stringResource(R.string.statistics_genres),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() }
                )
                if (tasteStatistics.rankedGenres.isNotEmpty()) {
                    GenreRanking(tasteStatistics.rankedGenres)
                }
            }
        }
        item {
            val ratingStatistics = statistics.personalRatingStatistics
            val effectiveSelectedRating = selectedRating?.takeIf { rating ->
                ratingStatistics.histogram.any { it.rating == rating && it.titleCount > 0 }
            }
            RatingSection(
                statistics = ratingStatistics,
                selectedRating = effectiveSelectedRating,
                onRatingSelected = { rating ->
                    selectedRating = if (selectedRating == rating) null else rating
                },
                onOpenDetails = onOpenDetails
            )
        }
    }
}

@Composable
private fun RatingSection(
    statistics: PersonalRatingStatistics,
    selectedRating: Int?,
    onRatingSelected: (Int) -> Unit,
    onOpenDetails: (ExternalMediaRef, MediaType) -> Unit,
    modifier: Modifier = Modifier
) {
    val locale = LocalConfiguration.current.locales[0]
    val average = statistics.averageRating?.let { formatPersonalRatingAverage(it, locale) }
        ?: stringResource(R.string.statistics_rating_unavailable)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = stringResource(R.string.statistics_ratings),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() }
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            ViewingMetric(
                value = average,
                label = stringResource(R.string.statistics_rating_average),
                modifier = Modifier.weight(1f)
            )
            ViewingMetric(
                value = statistics.ratedTitleCount.toString(),
                label = stringResource(R.string.statistics_rating_count),
                modifier = Modifier.weight(1f)
            )
        }
        RatingHistogram(
            histogram = statistics.histogram,
            selectedRating = selectedRating,
            onRatingSelected = onRatingSelected
        )
        if (statistics.ratedTitleCount == 0) {
            Text(
                text = stringResource(R.string.statistics_rating_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        selectedRating?.let { rating ->
            val selectedTitles = statistics.ratedTitles.filter { it.personalRating?.value == rating }
            val movies = selectedTitles.filter { it.mediaType == MediaType.MOVIE }
            val series = selectedTitles.filter { it.mediaType == MediaType.SERIES }
            if (movies.isNotEmpty()) {
                RatingShelf(
                    title = stringResource(R.string.statistics_rating_movies),
                    entries = movies,
                    onOpenDetails = onOpenDetails
                )
            }
            if (series.isNotEmpty()) {
                RatingShelf(
                    title = stringResource(R.string.statistics_rating_series),
                    entries = series,
                    onOpenDetails = onOpenDetails
                )
            }
        }
    }
}

@Composable
private fun RatingHistogram(
    histogram: List<RatingHistogramBucket>,
    selectedRating: Int?,
    onRatingSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val normalized = relativeRatingNormalization(histogram)
    BoxWithConstraints(modifier = modifier.fillMaxWidth().height(156.dp)) {
        val slotWidth = chartSlotWidth(maxWidth, histogram.size)
        val scrollState = rememberScrollState()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .horizontalScroll(scrollState),
            horizontalArrangement = Arrangement.spacedBy(CHART_SLOT_SPACING),
            verticalAlignment = Alignment.Bottom
        ) {
            Spacer(Modifier.width(CHART_EDGE_PADDING))
            histogram.forEachIndexed { index, bucket ->
                RatingHistogramBar(
                    bucket = bucket,
                    normalizedValue = normalized.getOrElse(index) { 0f },
                    isSelected = selectedRating == bucket.rating,
                    onClick = { onRatingSelected(bucket.rating) },
                    modifier = Modifier
                        .width(slotWidth)
                        .height(156.dp)
                )
            }
            Spacer(Modifier.width(CHART_EDGE_PADDING))
        }
    }
}

@Composable
private fun RatingHistogramBar(
    bucket: RatingHistogramBucket,
    normalizedValue: Float,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val canSelect = bucket.titleCount > 0
    val selectedSuffix = if (isSelected) {
        stringResource(R.string.statistics_rating_selected_suffix)
    } else {
        ""
    }
    val description = pluralStringResource(
        R.plurals.statistics_rating_bar_description,
        bucket.titleCount,
        bucket.rating,
        bucket.titleCount,
        selectedSuffix
    )
    Column(
        modifier = modifier
            .clickable(enabled = canSelect, onClick = onClick)
            .semantics {
                contentDescription = description
                role = Role.Button
                selected = isSelected
                if (!canSelect) disabled()
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .width(20.dp)
                .height(128.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f))
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(normalizedValue.coerceIn(0f, 1f))
                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                    .background(
                        if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.78f)
                        }
                    )
            )
        }
        Text(
            text = bucket.rating.toString(),
            style = if (isSelected) MaterialTheme.typography.labelLarge else MaterialTheme.typography.labelMedium,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
        Box(
            modifier = Modifier
                .padding(top = 2.dp)
                .size(width = 20.dp, height = 3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
        )
    }
}

@Composable
private fun RatingShelf(
    title: String,
    entries: List<PersonalViewingEntry>,
    onOpenDetails: (ExternalMediaRef, MediaType) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() }
        )
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(end = 4.dp)
        ) {
            items(
                items = entries,
                key = { "${it.mediaRef.source.name}:${it.mediaRef.externalId}" }
            ) { entry ->
                RatingPosterItem(
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
private fun RatingPosterItem(entry: PersonalViewingEntry, onClick: (() -> Unit)?) {
    val (withYear, withoutYear) = when (entry.mediaType) {
        MediaType.MOVIE -> R.string.profile_media_type_movie to R.string.profile_media_type_movie_no_year
        MediaType.SERIES -> R.string.profile_media_type_series to R.string.profile_media_type_series_no_year
    }
    val mediaMeta = entry.releaseDate?.year?.let { stringResource(withYear, it) }
        ?: stringResource(withoutYear)
    val description = stringResource(R.string.statistics_rating_poster_accessibility, entry.title, mediaMeta)
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
            height = 198.dp,
            // The clickable item owns the combined description of this entry.
            contentDescription = null
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
private fun ViewingSection(
    statistics: WatchedStatistics,
    selectedMonth: Int?,
    onYearChanged: (Int) -> Unit,
    onMonthSelected: (Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    val durationLabels = ViewingDurationLabels(
        day = stringResource(R.string.statistics_duration_day_short),
        hour = stringResource(R.string.statistics_duration_hour_short),
        minute = stringResource(R.string.statistics_duration_minute_short)
    )
    val monthly = statistics.monthlyViewing
    val fallbackYear = monthly.currentYear.takeIf { it > 0 } ?: monthly.selectedYear
    val year = monthly.selectedYear.takeIf { it > 0 } ?: fallbackYear
    val months = if (monthly.months.size == 12) {
        monthly.months
    } else {
        (1..12).map { month -> MonthlyViewingData(year = year, month = month) }
    }
    val years = monthly.availableYears.ifEmpty { year.takeIf { it > 0 }?.let(::listOf).orEmpty() }
    val hasNoActivity = months.all { it.totalMinutes == 0L && !it.isIncomplete }
    val hasIncompleteActivity = months.any { it.isIncomplete }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = stringResource(R.string.statistics_viewing),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() }
        )
        ViewingSummary(
            statistics = statistics,
            durationLabels = durationLabels
        )
        if (year > 0) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.statistics_viewing_time),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { heading() }
                )
                ViewingYearSelector(
                    year = year,
                    years = years,
                    onYearChanged = onYearChanged
                )
            }
            ViewingLegend()
            ViewingMonthChart(
                months = months,
                monthly = monthly,
                selectedMonth = selectedMonth,
                onMonthSelected = onMonthSelected
            )
            if (hasIncompleteActivity) {
                Text(
                    text = stringResource(R.string.statistics_viewing_incomplete_year),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (hasNoActivity) {
                Text(
                    text = stringResource(R.string.statistics_viewing_no_activity),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            selectedMonth?.let { month ->
                months.getOrNull(month - 1)?.let {
                    ViewingMonthDetail(it, durationLabels)
                }
            }
        }
    }
}

@Composable
private fun ViewingSummary(
    statistics: WatchedStatistics,
    durationLabels: ViewingDurationLabels,
    modifier: Modifier = Modifier
) {
    val unavailable = stringResource(R.string.statistics_watch_time_unavailable)
    val total = if (statistics.isWatchTimeIncomplete) {
        unavailable
    } else {
        formatViewingDuration(statistics.watchTimeMinutes, durationLabels)
    }
    val movieTime = if (statistics.movieWatchTimeIncomplete) {
        unavailable
    } else {
        formatViewingDuration(statistics.movieWatchTimeMinutes, durationLabels)
    }
    val seriesTime = if (statistics.seriesWatchTimeIncomplete) {
        unavailable
    } else {
        formatViewingDuration(statistics.seriesWatchTimeMinutes, durationLabels)
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            ViewingMetric(
                value = total,
                label = stringResource(R.string.statistics_viewing_total),
                modifier = Modifier.weight(1f)
            )
            ViewingMetric(
                value = statistics.moviesWatchedCount.toString(),
                label = stringResource(R.string.statistics_movies_watched),
                modifier = Modifier.weight(1f)
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            ViewingMetric(
                value = statistics.tvSeriesCompletedCount.toString(),
                label = stringResource(R.string.statistics_series_viewed),
                modifier = Modifier.weight(1f)
            )
            ViewingMetric(
                value = statistics.episodesWatchedCount.toString(),
                label = stringResource(R.string.statistics_episodes_watched),
                modifier = Modifier.weight(1f)
            )
        }
        Text(
            text = stringResource(
                R.string.statistics_viewing_breakdown,
                stringResource(R.string.statistics_movies_short),
                movieTime,
                stringResource(R.string.statistics_series_short),
                seriesTime
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ViewingMetric(value: String, label: String, modifier: Modifier = Modifier) {
    val metricDescription = stringResource(R.string.statistics_metric_accessibility, label, value)
    Column(
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = metricDescription
        },
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.heightIn(min = 32.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ViewingYearSelector(year: Int, years: List<Int>, onYearChanged: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) {
            Text(year.toString())
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = stringResource(R.string.statistics_viewing_choose_year)
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            years.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.toString()) },
                    onClick = {
                        expanded = false
                        onYearChanged(option)
                    }
                )
            }
        }
    }
}

@Composable
private fun ViewingLegend(modifier: Modifier = Modifier) {
    val seriesColor = viewingSeriesColor()
    val movieColor = viewingMovieColor()
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ViewingLegendItem(
            color = movieColor,
            label = stringResource(R.string.statistics_movies_short)
        )
        ViewingLegendItem(
            color = seriesColor,
            label = stringResource(R.string.statistics_series_short)
        )
    }
}

@Composable
private fun ViewingLegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color)
        )
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun ViewingMonthChart(
    months: List<MonthlyViewingData>,
    monthly: com.cydoniancitizen.bingee.domain.model.MonthlyViewingStatistics,
    selectedMonth: Int?,
    onMonthSelected: (Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    val shortMonths = stringArrayResource(R.array.statistics_months_short)
    val fullMonths = stringArrayResource(R.array.statistics_months_full)
    val normalized = relativeViewingNormalization(months)
    val seriesColor = viewingSeriesColor()
    val movieColor = viewingMovieColor()
    val chartDescription = stringResource(
        R.string.statistics_viewing_chart_description,
        monthly.selectedYear
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = chartDescription
            },
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(156.dp)) {
            val slotWidth = chartSlotWidth(maxWidth, months.size)
            val scrollState = rememberScrollState()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .horizontalScroll(scrollState),
                horizontalArrangement = Arrangement.spacedBy(CHART_SLOT_SPACING),
                verticalAlignment = Alignment.Bottom
            ) {
                Spacer(Modifier.width(CHART_EDGE_PADDING))
                months.forEachIndexed { index, month ->
                    ViewingMonthBar(
                        month = month,
                        monthLabel = shortMonths.getOrElse(index) { "" },
                        fullMonthLabel = fullMonths.getOrElse(index) { "" },
                        normalizedValue = normalized.getOrElse(index) { 0f },
                        isSelected = selectedMonth == month.month,
                        isFuture = monthly.selectedYear == monthly.currentYear &&
                            month.month > monthly.currentMonth,
                        movieColor = movieColor,
                        seriesColor = seriesColor,
                        onClick = { onMonthSelected(month.month) },
                        modifier = Modifier
                            .width(slotWidth)
                            .height(156.dp)
                    )
                }
                Spacer(Modifier.width(CHART_EDGE_PADDING))
            }
        }
    }
}

@Composable
private fun ViewingMonthBar(
    month: MonthlyViewingData,
    monthLabel: String,
    fullMonthLabel: String,
    normalizedValue: Float,
    isSelected: Boolean,
    isFuture: Boolean,
    movieColor: Color,
    seriesColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val durationLabels = ViewingDurationLabels(
        day = stringResource(R.string.statistics_duration_day_short),
        hour = stringResource(R.string.statistics_duration_hour_short),
        minute = stringResource(R.string.statistics_duration_minute_short)
    )
    val unavailable = stringResource(R.string.statistics_watch_time_unavailable)
    val movieValue = if (month.movieTimeIncomplete) {
        unavailable
    } else {
        formatViewingDuration(month.movieMinutes, durationLabels)
    }
    val seriesValue = if (month.seriesTimeIncomplete) {
        unavailable
    } else {
        formatViewingDuration(month.seriesMinutes, durationLabels)
    }
    val totalValue = if (month.isIncomplete) {
        unavailable
    } else {
        formatViewingDuration(month.totalMinutes, durationLabels)
    }
    val description = stringResource(
        R.string.statistics_viewing_month_accessibility,
        fullMonthLabel,
        month.year,
        movieValue,
        seriesValue,
        totalValue
    )
    Column(
        modifier = modifier
            .clickable(enabled = !isFuture, onClick = onClick)
            .semantics {
                contentDescription = description
                role = Role.Button
                selected = isSelected
                if (isFuture) disabled()
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(132.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            val height = 132.dp * normalizedValue.coerceIn(0f, 1f)
            Column(
                modifier = Modifier
                    .width(20.dp)
                    .height(height),
                verticalArrangement = Arrangement.Bottom
            ) {
                if (month.movieMinutes > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(month.movieMinutes.toFloat())
                            .background(movieColor)
                    )
                }
                if (month.seriesMinutes > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(month.seriesMinutes.toFloat())
                            .background(seriesColor)
                    )
                }
            }
        }
        Text(
            text = monthLabel,
            style = if (isSelected) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelSmall,
            color = if (isFuture) {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            textAlign = TextAlign.Center,
            maxLines = 1
        )
        Box(
            modifier = Modifier
                .padding(top = 2.dp)
                .size(width = 20.dp, height = 3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(
                    if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                )
        )
    }
}

@Composable
private fun ViewingMonthDetail(month: MonthlyViewingData, durationLabels: ViewingDurationLabels) {
    val fullMonths = stringArrayResource(R.array.statistics_months_full)
    val unavailable = stringResource(R.string.statistics_watch_time_unavailable)
    val movieValue = if (month.movieTimeIncomplete) {
        unavailable
    } else {
        formatViewingDuration(month.movieMinutes, durationLabels)
    }
    val seriesValue = if (month.seriesTimeIncomplete) {
        unavailable
    } else {
        formatViewingDuration(month.seriesMinutes, durationLabels)
    }
    val totalValue = if (month.isIncomplete) {
        unavailable
    } else {
        formatViewingDuration(month.totalMinutes, durationLabels)
    }
    val detailDescription = stringResource(
        R.string.statistics_viewing_month_detail_accessibility,
        fullMonths.getOrElse(month.month - 1) { "" },
        month.year,
        movieValue,
        seriesValue,
        totalValue
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = detailDescription
            },
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = stringResource(
                R.string.statistics_viewing_month_title,
                fullMonths.getOrElse(month.month - 1) { "" },
                month.year
            ),
            style = MaterialTheme.typography.titleSmall
        )
        ViewingDetailRow(stringResource(R.string.statistics_movies_short), movieValue)
        ViewingDetailRow(stringResource(R.string.statistics_series_short), seriesValue)
        ViewingDetailRow(stringResource(R.string.statistics_total_short), totalValue)
        if (month.isIncomplete) {
            Text(
                text = stringResource(R.string.statistics_viewing_incomplete),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ViewingDetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun viewingSeriesColor(): Color = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
    BingeeChartColors.viewingSeriesDark
} else {
    BingeeChartColors.viewingSeriesLight
}

@Composable
private fun viewingMovieColor(): Color = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
    BingeeChartColors.taste
} else {
    BingeeChartColors.viewingMovieLight
}

@Composable
private fun StatisticsScopeSelector(
    selectedScope: StatisticsMediaScope,
    onScopeChanged: (StatisticsMediaScope) -> Unit,
    modifier: Modifier = Modifier
) {
    val scopes = StatisticsMediaScope.entries
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        scopes.forEachIndexed { index, scope ->
            SegmentedButton(
                selected = selectedScope == scope,
                onClick = { onScopeChanged(scope) },
                shape = SegmentedButtonDefaults.itemShape(index, scopes.size),
                label = {
                    Text(
                        text = stringResource(
                            when (scope) {
                                StatisticsMediaScope.ALL -> R.string.statistics_filter_all
                                StatisticsMediaScope.MOVIES -> R.string.statistics_filter_movies
                                StatisticsMediaScope.SERIES -> R.string.statistics_filter_series
                            }
                        )
                    )
                }
            )
        }
    }
}

@Composable
private fun TasteRadarChart(
    genres: List<GenreStatistic>,
    normalizedValues: List<Float>,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val chartDescription = if (genres.size >= 3) {
        stringResource(
            R.string.statistics_radar_description,
            genres.joinToString { it.name }
        )
    } else {
        stringResource(R.string.statistics_radar_empty_description)
    }
    val labelStyle = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.onSurface)
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
    val axisColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
    val accentColor = MaterialTheme.colorScheme.primary

    // Labels are sp-sized, so the frame has to grow with the font scale or they collide with the web.
    // On a compact screen the width binds the chart first, so the extra height is capped to it rather
    // than left as dead space above the web.
    val density = LocalDensity.current
    val fontScale = density.fontScale.coerceIn(1f, RADAR_MAX_FONT_SCALE)
    val availableWidth = with(density) { LocalWindowInfo.current.containerSize.width.toDp() } -
        BingeeDimensions.screenPadding * 2
    val chartHeight = (RADAR_BASE_HEIGHT * fontScale).coerceAtMost(
        availableWidth.coerceAtLeast(RADAR_BASE_HEIGHT)
    )
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(chartHeight)
            .semantics { contentDescription = chartDescription }
    ) {
        drawRadar(
            genres = genres,
            normalizedValues = normalizedValues,
            textMeasurer = textMeasurer,
            labelStyle = labelStyle,
            gridColor = gridColor,
            axisColor = axisColor,
            accentColor = accentColor
        )
    }
}

private fun DrawScope.drawRadar(
    genres: List<GenreStatistic>,
    normalizedValues: List<Float>,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    labelStyle: TextStyle,
    gridColor: Color,
    axisColor: Color,
    accentColor: Color
) {
    val axisCount = genres.size
    val labelGap = 8.dp.toPx()
    // A compact screen still owes long genre names a readable gutter, so the fraction has a floor.
    val labelWidth = min(
        RADAR_LABEL_MAX_WIDTH.toPx(),
        max(size.width * 0.24f, RADAR_LABEL_MIN_WIDTH.toPx())
    )
    val labels = genres.map { genre ->
        textMeasurer.measure(
            text = AnnotatedString(genre.name),
            style = labelStyle,
            constraints = Constraints(maxWidth = labelWidth.roundToInt().coerceAtLeast(1)),
            maxLines = RADAR_LABEL_MAX_LINES,
            overflow = TextOverflow.Ellipsis
        )
    }
    // Only the widest and tallest axis projections actually need clearance, so the radius is bounded
    // by those instead of by the worst case an axis-aligned layout would imply.
    val maxAbsCos = (0 until axisCount)
        .maxOfOrNull { abs(cos(radarAngle(it, axisCount))) }
        ?.coerceAtLeast(RADAR_MIN_AXIS_PROJECTION) ?: 1f
    val maxAbsSin = (0 until axisCount)
        .maxOfOrNull { abs(sin(radarAngle(it, axisCount))) }
        ?.coerceAtLeast(RADAR_MIN_AXIS_PROJECTION) ?: 1f
    val labelHeight = labels.maxOfOrNull { it.size.height.toFloat() } ?: 0f
    val radius = min(
        (size.width / 2f - labelWidth - labelGap) / maxAbsCos,
        (size.height / 2f - labelHeight - labelGap) / maxAbsSin
    ).coerceAtLeast(0f)
    val center = Offset(size.width / 2f, size.height / 2f)

    repeat(RADAR_RING_COUNT) { index ->
        val ringRadius = radius * (index + 1) / RADAR_RING_COUNT
        if (axisCount >= 3) {
            drawPath(
                path = radarPath(center, ringRadius, axisCount),
                color = gridColor,
                style = Stroke(width = 1.dp.toPx())
            )
        } else {
            drawCircle(
                color = gridColor,
                radius = ringRadius,
                center = center,
                style = Stroke(width = 1.dp.toPx())
            )
        }
    }

    if (axisCount < 3) return

    repeat(axisCount) { index ->
        drawLine(
            color = axisColor,
            start = center,
            end = radarPoint(center, radius, index, axisCount),
            strokeWidth = 1.dp.toPx()
        )
    }

    val polygon = Path()
    normalizedValues.forEachIndexed { index, value ->
        val point = radarPoint(center, radius * value.coerceIn(0f, 1f), index, axisCount)
        if (index == 0) polygon.moveTo(point.x, point.y) else polygon.lineTo(point.x, point.y)
    }
    polygon.close()
    drawPath(polygon, BingeeChartColors.taste.copy(alpha = 0.16f))
    drawPath(path = polygon, color = accentColor, style = Stroke(width = 2.dp.toPx()))

    genres.indices.forEach { index ->
        val dataPoint = radarPoint(center, radius * normalizedValues[index].coerceIn(0f, 1f), index, axisCount)
        val axisPoint = radarPoint(center, radius, index, axisCount)
        drawCircle(accentColor, radius = 3.5.dp.toPx(), center = dataPoint)

        val layout = labels[index]
        val angle = radarAngle(index, axisCount)
        val direction = Offset(cos(angle), sin(angle))
        val labelX = when {
            direction.x > 0.25f -> axisPoint.x + labelGap
            direction.x < -0.25f -> axisPoint.x - labelGap - layout.size.width
            else -> axisPoint.x - layout.size.width / 2f
        }
        val labelY = when {
            direction.y > 0.35f -> axisPoint.y + labelGap / 2f
            direction.y < -0.35f -> axisPoint.y - labelGap / 2f - layout.size.height
            else -> axisPoint.y - layout.size.height / 2f
        }
        drawText(
            textLayoutResult = layout,
            topLeft = Offset(
                labelX.coerceIn(0f, size.width - layout.size.width),
                labelY.coerceIn(0f, size.height - layout.size.height)
            )
        )
    }
}

private fun radarPath(center: Offset, radius: Float, axisCount: Int): Path = Path().apply {
    repeat(axisCount) { index ->
        val point = radarPoint(center, radius, index, axisCount)
        if (index == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
    }
    close()
}

private fun radarPoint(center: Offset, radius: Float, index: Int, axisCount: Int): Offset {
    val angle = radarAngle(index, axisCount)
    return Offset(
        x = center.x + cos(angle) * radius,
        y = center.y + sin(angle) * radius
    )
}

private fun radarAngle(index: Int, axisCount: Int): Float = (-PI / 2.0 + index * (2.0 * PI / axisCount)).toFloat()

@Composable
private fun GenreRanking(rankedGenres: List<GenreStatistic>, modifier: Modifier = Modifier) {
    val normalizedValues = relativeGenreNormalization(rankedGenres.map(GenreStatistic::titleCount))
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        rankedGenres.forEachIndexed { index, genre ->
            GenreRankingRow(
                genre = genre,
                normalizedValue = normalizedValues[index]
            )
        }
    }
}

@Composable
private fun GenreRankingRow(genre: GenreStatistic, normalizedValue: Float, modifier: Modifier = Modifier) {
    val rowDescription = pluralStringResource(
        R.plurals.statistics_genre_row_description,
        genre.titleCount,
        genre.name,
        genre.titleCount
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = rowDescription },
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = genre.name,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = genre.titleCount.toString(),
                modifier = Modifier.widthIn(min = 24.dp),
                style = MaterialTheme.typography.labelLarge
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(normalizedValue.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}
