package com.cydoniancitizen.bingee.feature.details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cydoniancitizen.bingee.R
import com.cydoniancitizen.bingee.core.model.LinkedMediaIdentity
import com.cydoniancitizen.bingee.core.model.MediaLinkGroup
import com.cydoniancitizen.bingee.core.model.MediaSource

@Composable
fun LinkedSectionCard(
    linkGroup: MediaLinkGroup,
    currentIdentity: LinkedMediaIdentity,
    onOpenOtherMember: (LinkedMediaIdentity) -> Unit,
    onChangePreferred: (newPreferred: LinkedMediaIdentity) -> Unit,
    onUnlink: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showUnlinkDialog by remember { mutableStateOf(false) }

    val otherMember = if (linkGroup.first == currentIdentity) linkGroup.second else linkGroup.first
    val isCurrentPreferred = linkGroup.preferredPresentation == currentIdentity

    val otherProviderLabel = when (otherMember.source) {
        MediaSource.TMDB -> "TMDB (${otherMember.mediaType.name})"
        MediaSource.JIKAN -> "Jikan (Anime)"
        MediaSource.IMDB -> "IMDb"
    }

    val currentProviderLabel = when (currentIdentity.source) {
        MediaSource.TMDB -> "TMDB (${currentIdentity.mediaType.name})"
        MediaSource.JIKAN -> "Jikan (Anime)"
        MediaSource.IMDB -> "IMDb"
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.linked_versions),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = stringResource(R.string.linked_providers_summary, currentProviderLabel, otherProviderLabel),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (isCurrentPreferred) {
                    "This version is set as your preferred presentation."
                } else {
                    "The other version ($otherProviderLabel) is set as your preferred presentation."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { onOpenOtherMember(otherMember) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = "Open $otherProviderLabel")
                }

                if (!isCurrentPreferred) {
                    Button(
                        onClick = { onChangePreferred(currentIdentity) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(text = stringResource(R.string.preferred_version))
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            TextButton(
                onClick = { showUnlinkDialog = true },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(
                    text = stringResource(R.string.separate_versions),
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }

    if (showUnlinkDialog) {
        AlertDialog(
            onDismissRequest = { showUnlinkDialog = false },
            title = { Text(text = stringResource(R.string.unlink_confirm_title)) },
            text = { Text(text = stringResource(R.string.unlinking_explanation)) },
            confirmButton = {
                Button(
                    onClick = {
                        showUnlinkDialog = false
                        onUnlink()
                    }
                ) {
                    Text(text = stringResource(R.string.separate_versions))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showUnlinkDialog = false }) {
                    Text(text = stringResource(R.string.action_cancel))
                }
            }
        )
    }
}
