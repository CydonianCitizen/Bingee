package com.cydoniancitizen.bingee.feature.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.cydoniancitizen.bingee.R
import com.cydoniancitizen.bingee.core.designsystem.theme.BingeeDimensions
import com.cydoniancitizen.bingee.core.model.MovieWatchState
import com.cydoniancitizen.bingee.core.ui.toUiError
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Composable
internal fun MovieProgressSection(state: MovieProgressState, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    if (state == MovieProgressState.NotApplicable) return
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(BingeeDimensions.elementSpacing)
    ) {
        Text(
            text = stringResource(R.string.detail_movie_progress_title),
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleLarge
        )
        when (state) {
            MovieProgressState.NotApplicable -> Unit
            MovieProgressState.Loading -> Text(stringResource(R.string.detail_progress_loading))
            is MovieProgressState.Error -> Text(
                text = stringResource(state.error.toUiError().messageRes),
                color = MaterialTheme.colorScheme.error
            )
            is MovieProgressState.Ready -> {
                val watched = state.state as? MovieWatchState.Watched
                val watchedLabel = watched?.let {
                    stringResource(R.string.detail_movie_watched_at, it.localizedWatchedDate())
                }
                val watchStateDescription = when {
                    state.updating -> stringResource(R.string.detail_progress_updating)
                    watchedLabel == null -> stringResource(R.string.detail_movie_unwatched)
                    else -> watchedLabel
                }
                Text(watchedLabel ?: stringResource(R.string.detail_movie_unwatched))
                Button(
                    onClick = onToggle,
                    enabled = !state.updating,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics {
                            stateDescription = watchStateDescription
                        }
                ) {
                    Text(
                        stringResource(
                            when {
                                state.updating -> R.string.detail_progress_updating
                                watched != null -> R.string.detail_mark_unwatched
                                else -> R.string.detail_mark_watched
                            }
                        )
                    )
                }
            }
        }
    }
}

/**
 * The canonical viewing date, formatted for the reader. The stored instant is only a fallback for
 * rows written before the local-date column existed; either way the user never sees a raw timestamp.
 */
private fun MovieWatchState.Watched.localizedWatchedDate(): String {
    val zone = ZoneId.systemDefault()
    val date = watchedDate ?: watchedAt.atZone(zone).toLocalDate()
    return DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault()).format(date)
}
