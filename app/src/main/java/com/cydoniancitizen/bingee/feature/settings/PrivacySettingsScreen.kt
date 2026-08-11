package com.cydoniancitizen.bingee.feature.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cydoniancitizen.bingee.R
import com.cydoniancitizen.bingee.core.designsystem.theme.BingeeDimensions
import com.cydoniancitizen.bingee.feature.credential.CredentialEditor

@Composable
internal fun PrivacySettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PrivacyViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    PrivacySettingsContent(
        state = state,
        onInputChanged = viewModel::onInputChanged,
        onSubmit = viewModel::submit,
        onRetry = viewModel::retry,
        onRequestRemoval = viewModel::requestRemoval,
        onDismissRemoval = viewModel::dismissRemoval,
        onConfirmRemoval = viewModel::confirmRemoval,
        onBack = onBack,
        modifier = modifier
    )
}

@Composable
internal fun PrivacySettingsContent(
    state: PrivacyUiState,
    onInputChanged: (String) -> Unit,
    onSubmit: (String) -> Unit,
    onRetry: (String) -> Unit,
    onRequestRemoval: () -> Unit,
    onDismissRemoval: () -> Unit,
    onConfirmRemoval: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(BingeeDimensions.screenPadding),
        verticalArrangement = Arrangement.spacedBy(BingeeDimensions.contentSpacing)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.detail_back)
                )
            }
            Text(
                text = stringResource(R.string.privacy_title),
                modifier = Modifier.weight(1f).semantics { heading() },
                style = MaterialTheme.typography.headlineMedium
            )
        }

        Text(
            text = stringResource(R.string.privacy_body),
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = stringResource(R.string.privacy_summary),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = stringResource(R.string.privacy_documentation_reference),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        HorizontalDivider()

        CredentialEditor(
            titleRes = R.string.settings_tmdb_title,
            descriptionRes = R.string.settings_tmdb_description,
            credentialStatus = state.credentialStatus,
            inputStatus = state.inputStatus,
            error = state.error,
            onInputChanged = onInputChanged,
            onSubmit = onSubmit,
            onRetry = onRetry
        )
        Button(
            onClick = onRequestRemoval,
            enabled = state.canRemove && !state.isSubmitting && !state.isRemoving
        ) {
            Text(stringResource(R.string.credential_remove))
        }
        Text(
            text = stringResource(R.string.credential_remove_explanation),
            style = MaterialTheme.typography.bodyMedium
        )

        HorizontalDivider()

        Image(
            painter = painterResource(R.drawable.tmdb_logo),
            contentDescription = stringResource(R.string.tmdb_logo_description),
            modifier = Modifier.height(48.dp)
        )
        Text(
            text = stringResource(R.string.tmdb_attribution),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    if (state.showRemovalConfirmation) {
        AlertDialog(
            onDismissRequest = onDismissRemoval,
            title = { Text(stringResource(R.string.credential_remove_confirm_title)) },
            text = { Text(stringResource(R.string.credential_remove_confirm_body)) },
            confirmButton = {
                TextButton(onClick = onConfirmRemoval) {
                    Text(stringResource(R.string.credential_remove))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissRemoval) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}
