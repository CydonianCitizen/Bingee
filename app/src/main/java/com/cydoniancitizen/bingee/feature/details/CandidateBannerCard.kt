package com.cydoniancitizen.bingee.feature.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cydoniancitizen.bingee.R
import com.cydoniancitizen.bingee.domain.equivalence.MediaEquivalenceCandidate
import com.cydoniancitizen.bingee.domain.equivalence.MediaEquivalenceClassification

@Composable
fun CandidateBannerCard(candidate: MediaEquivalenceCandidate, onCompare: () -> Unit, modifier: Modifier = Modifier) {
    val eval = candidate.evaluation
    val otherIdentity = eval.second

    val otherProviderLabel = when (otherIdentity.source) {
        com.cydoniancitizen.bingee.core.model.MediaSource.TMDB -> "TMDB (${otherIdentity.mediaType.name})"
        com.cydoniancitizen.bingee.core.model.MediaSource.JIKAN -> "Jikan (Anime)"
        com.cydoniancitizen.bingee.core.model.MediaSource.IMDB -> "IMDb"
    }

    val subtitle = when (eval.classification) {
        MediaEquivalenceClassification.EXACT_IDENTITY -> "Matching IMDb ID ($otherProviderLabel)"
        MediaEquivalenceClassification.STRONG_POSSIBLE_SAME_WORK ->
            "Strong title & release year match ($otherProviderLabel)"
        else -> "Possible duplicate suggestion ($otherProviderLabel)"
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.possible_duplicate),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(onClick = onCompare) {
                    Text(text = stringResource(R.string.compare_versions))
                }
            }
        }
    }
}
