package com.cydoniancitizen.bingee.feature.equivalence

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cydoniancitizen.bingee.R
import com.cydoniancitizen.bingee.core.model.LinkedMediaIdentity
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.domain.equivalence.MediaEquivalenceClassification
import com.cydoniancitizen.bingee.domain.equivalence.MediaEquivalenceSignal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaComparisonBottomSheet(
    first: LinkedMediaIdentity,
    second: LinkedMediaIdentity,
    onDismissRequest: () -> Unit,
    onLinkSuccess: () -> Unit,
    viewModel: MediaComparisonViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(first, second) {
        viewModel.loadComparison(first, second)
    }

    LaunchedEffect(uiState.linkSuccess) {
        if (uiState.linkSuccess) {
            onLinkSuccess()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier
    ) {
        val scrollState = rememberScrollState()

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .verticalScroll(scrollState)
        ) {
            Text(
                text = stringResource(R.string.compare_versions),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (uiState.isLoading) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                }
            } else {
                val eval = uiState.evaluation
                if (eval != null) {
                    // Candidate Classification Badge / Header
                    val classificationText = when (eval.classification) {
                        MediaEquivalenceClassification.EXACT_IDENTITY -> "Exact shared identity (IMDb)"
                        MediaEquivalenceClassification.STRONG_POSSIBLE_SAME_WORK -> "Strong title & metadata agreement"
                        else -> "Possible duplicate suggestion"
                    }

                    Text(
                        text = classificationText,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Side-by-side or stacked provider records comparison
                    ComparisonMemberCard(
                        identity = eval.first,
                        isSelectedPreferred = uiState.selectedPreferred == eval.first,
                        onSelectPreferred = { viewModel.selectPreferred(eval.first) }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    ComparisonMemberCard(
                        identity = eval.second,
                        isSelectedPreferred = uiState.selectedPreferred == eval.second,
                        onSelectPreferred = { viewModel.selectPreferred(eval.second) }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Structured evidence reasons
                    Text(
                        text = "Why Bingee suggested this pair:",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    for (signal in eval.positiveSignals) {
                        val signalLabel = when (signal) {
                            MediaEquivalenceSignal.SHARED_IMDB_ID -> "• Verified matching IMDb identifier"
                            MediaEquivalenceSignal.EXACT_NORMALIZED_TITLE -> "• Identical normalized title"
                            MediaEquivalenceSignal.EXACT_ORIGINAL_TITLE -> "• Matching original title"
                            MediaEquivalenceSignal.EXACT_ENGLISH_TITLE -> "• Matching English title"
                            MediaEquivalenceSignal.EXACT_JAPANESE_TITLE -> "• Matching Japanese title"
                            MediaEquivalenceSignal.EXACT_RELEASE_YEAR -> "• Matching release year"
                            MediaEquivalenceSignal.EXACT_RELEASE_DATE -> "• Matching exact release date"
                            MediaEquivalenceSignal.COMPATIBLE_FORMAT -> "• Compatible provider format"
                            MediaEquivalenceSignal.COMPATIBLE_MEDIA_TYPE -> "• Compatible media type"
                            MediaEquivalenceSignal.USER_SELECTED_PAIR -> "• User selected pair"
                        }
                        Text(
                            text = signalLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Explanatory Box
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surfaceContainerHigh,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.preferred_explanation),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.linking_explanation),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (uiState.isStale) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "This candidate suggestion is no longer eligible for linking.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(onClick = onDismissRequest) {
                            Text(text = stringResource(R.string.action_cancel))
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Button(
                            onClick = { viewModel.confirmLink() },
                            enabled = !uiState.isLinking && !uiState.isStale
                        ) {
                            if (uiState.isLinking) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(text = stringResource(R.string.link_versions))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ComparisonMemberCard(
    identity: LinkedMediaIdentity,
    isSelectedPreferred: Boolean,
    onSelectPreferred: () -> Unit
) {
    val providerLabel = when (identity.source) {
        MediaSource.TMDB -> "TMDB (${identity.mediaType.name})"
        MediaSource.JIKAN -> "Jikan (Anime)"
        MediaSource.IMDB -> "IMDb"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (isSelectedPreferred) 2.dp else 1.dp,
                color = if (isSelectedPreferred) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
                shape = RoundedCornerShape(12.dp)
            )
            .background(
                color = if (isSelectedPreferred) {
                    MaterialTheme.colorScheme.primaryContainer.copy(
                        alpha = 0.2f
                    )
                } else {
                    MaterialTheme.colorScheme.surface
                },
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onSelectPreferred)
            .padding(12.dp)
            .semantics {
                contentDescription =
                    "$providerLabel ID ${identity.externalId}${if (isSelectedPreferred) " selected as preferred version" else ""}"
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = isSelectedPreferred,
            onClick = onSelectPreferred
        )

        Spacer(modifier = Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = providerLabel,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "ID: ${identity.externalId}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (isSelectedPreferred) {
            Text(
                text = stringResource(R.string.preferred_version),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
