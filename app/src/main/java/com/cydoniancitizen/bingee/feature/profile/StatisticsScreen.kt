package com.cydoniancitizen.bingee.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cydoniancitizen.bingee.R
import com.cydoniancitizen.bingee.core.designsystem.component.MediaPoster
import com.cydoniancitizen.bingee.core.designsystem.theme.BingeeDimensions
import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.domain.model.WatchedStatistics
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

@Composable
internal fun StatisticsScreen(
    onBack: () -> Unit,
    onOpenDetails: (ExternalMediaRef, MediaType) -> Unit,
    viewModel: ProfileViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = BingeeDimensions.elementSpacing, vertical = BingeeDimensions.elementSpacing),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.detail_back))
            }
            Text(
                text = stringResource(R.string.statistics_title),
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() },
                style = MaterialTheme.typography.titleLarge
            )
        }
        StatisticsContent(
            statistics = state.statistics,
            onOpenDetails = onOpenDetails
        )
    }
}

@Composable
internal fun StatisticsContent(
    statistics: WatchedStatistics,
    onOpenDetails: (ExternalMediaRef, MediaType) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentLocale = LocalConfiguration.current.locales[0]

    if (statistics.isEmpty) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(BingeeDimensions.elementSpacing)
            ) {
                Icon(
                    imageVector = Icons.Default.DateRange,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.height(48.dp)
                )
                Text(
                    text = stringResource(R.string.statistics_empty_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.statistics_empty_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = BingeeDimensions.screenPadding),
        verticalArrangement = Arrangement.spacedBy(BingeeDimensions.contentSpacing)
    ) {
        item {
            // Stat Cards Grid
            Column(verticalArrangement = Arrangement.spacedBy(BingeeDimensions.elementSpacing)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(BingeeDimensions.elementSpacing)
                ) {
                    StatCard(
                        title = stringResource(R.string.statistics_movies_watched),
                        value = statistics.moviesWatchedCount.toString(),
                        icon = Icons.Default.Info,
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        title = stringResource(R.string.statistics_tv_completed),
                        value = statistics.tvSeriesCompletedCount.toString(),
                        icon = Icons.Default.Info,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(BingeeDimensions.elementSpacing)
                ) {
                    StatCard(
                        title = stringResource(R.string.statistics_episodes_watched),
                        value = statistics.episodesWatchedCount.toString(),
                        modifier = Modifier.weight(1f)
                    )
                    val hours = statistics.estimatedWatchTimeMinutes / 60
                    val watchTimeText = if (hours > 0) {
                        stringResource(R.string.statistics_watch_time_hours, hours)
                    } else {
                        stringResource(R.string.statistics_watch_time_minutes, statistics.estimatedWatchTimeMinutes)
                    }
                    StatCard(
                        title = stringResource(R.string.statistics_watch_time),
                        value = watchTimeText,
                        subtitle = if (statistics.isWatchTimeIncomplete) {
                            stringResource(
                                R.string.statistics_watch_time_incomplete
                            )
                        } else {
                            null
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(BingeeDimensions.elementSpacing)
                ) {
                    val avgRatingText =
                        statistics.averagePersonalRating?.let { String.format(currentLocale, "%.1f / 10", it) }
                            ?: "-"
                    StatCard(
                        title = stringResource(R.string.statistics_avg_rating),
                        value = avgRatingText,
                        icon = Icons.Default.Star,
                        modifier = Modifier.weight(1f)
                    )
                    val ratedPercentText = String.format(
                        currentLocale,
                        "%.0f%%",
                        statistics.ratedTitlesPercentage
                    )
                    StatCard(
                        title = stringResource(R.string.statistics_rated_percentage),
                        value = ratedPercentText,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Media Distribution Section
        item {
            Card(
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(BingeeDimensions.contentSpacing),
                    verticalArrangement = Arrangement.spacedBy(BingeeDimensions.elementSpacing)
                ) {
                    Text(
                        text = stringResource(R.string.statistics_distribution),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    val dist = statistics.mediaTypeDistribution
                    if (dist.total > 0) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(16.dp)
                                .clip(RoundedCornerShape(8.dp))
                        ) {
                            if (dist.movieCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .weight(dist.moviePercentage.toFloat().coerceAtLeast(0.01f))
                                        .height(16.dp)
                                        .background(MaterialTheme.colorScheme.primary)
                                )
                            }
                            if (dist.tvSeriesCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .weight(dist.tvSeriesPercentage.toFloat().coerceAtLeast(0.01f))
                                        .height(16.dp)
                                        .background(MaterialTheme.colorScheme.secondary)
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(
                                    R.string.statistics_distribution_item,
                                    stringResource(R.string.profile_tab_movies),
                                    dist.movieCount,
                                    String.format(currentLocale, "%.0f%%", dist.moviePercentage)
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = stringResource(
                                    R.string.statistics_distribution_item,
                                    stringResource(R.string.profile_tab_tv_series),
                                    dist.tvSeriesCount,
                                    String.format(currentLocale, "%.0f%%", dist.tvSeriesPercentage)
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }
        }

        // Watched by Month History Section (if present)
        if (statistics.watchedByMonthYear.isNotEmpty()) {
            item {
                Card(
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(BingeeDimensions.contentSpacing),
                        verticalArrangement = Arrangement.spacedBy(BingeeDimensions.elementSpacing)
                    ) {
                        Text(
                            text = stringResource(R.string.statistics_by_month),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        statistics.watchedByMonthYear.take(12).forEach { item ->
                            val monthName = Month.of(item.month).getDisplayName(TextStyle.SHORT, currentLocale)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("$monthName ${item.year}", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "${item.count}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // Recently Completed Row
        if (statistics.recentlyCompletedTitles.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(BingeeDimensions.elementSpacing)) {
                    Text(
                        text = stringResource(R.string.statistics_recently_completed),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(BingeeDimensions.elementSpacing)) {
                        items(
                            items = statistics.recentlyCompletedTitles,
                            key = { "${it.mediaRef.source}:${it.mediaRef.externalId}" }
                        ) { entry ->
                            Card(
                                onClick = { onOpenDetails(entry.mediaRef, entry.mediaType) },
                                modifier = Modifier.width(110.dp)
                            ) {
                                Column {
                                    MediaPoster(
                                        title = entry.title,
                                        posterUrl = entry.posterUrl,
                                        modifier = Modifier.width(110.dp).height(160.dp)
                                    )
                                    Text(
                                        text = entry.title,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(BingeeDimensions.screenPadding))
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null
) {
    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = MaterialTheme.shapes.medium,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(BingeeDimensions.elementSpacing),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                icon?.let {
                    Icon(
                        imageVector = it,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.height(18.dp).width(18.dp)
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
