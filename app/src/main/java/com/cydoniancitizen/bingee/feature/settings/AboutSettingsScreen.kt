package com.cydoniancitizen.bingee.feature.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cydoniancitizen.bingee.R
import com.cydoniancitizen.bingee.core.designsystem.theme.BingeeDimensions

private const val GITHUB_REPO_URL = "https://github.com/CydonianCitizen/Bingee"

@Composable
internal fun AboutSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AboutViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    AboutSettingsContent(
        state = state,
        onCheckForUpdates = viewModel::checkForUpdates,
        onBack = onBack,
        modifier = modifier
    )
}

@Composable
internal fun AboutSettingsContent(
    state: AboutUiState,
    onCheckForUpdates: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

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
                text = stringResource(R.string.settings_nav_about),
                modifier = Modifier.weight(1f).semantics { heading() },
                style = MaterialTheme.typography.headlineMedium
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = stringResource(R.string.about_app_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.about_version_label, state.installedVersion),
                style = MaterialTheme.typography.bodyMedium
            )
        }

        HorizontalDivider()

        UpdateCheckerSection(
            updateState = state.updateState,
            installedVersion = state.installedVersion,
            onCheckForUpdates = onCheckForUpdates,
            context = context
        )

        HorizontalDivider()

        OpenSourceSection(context = context)
    }
}

@Composable
private fun UpdateCheckerSection(
    updateState: UpdateCheckUiState,
    installedVersion: String,
    onCheckForUpdates: () -> Unit,
    context: Context
) {
    Column(verticalArrangement = Arrangement.spacedBy(BingeeDimensions.elementSpacing)) {
        Text(
            text = stringResource(R.string.update_check_action),
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleMedium
        )

        when (updateState) {
            is UpdateCheckUiState.Idle -> {
                Button(
                    onClick = onCheckForUpdates,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.update_check_action))
                }
            }

            is UpdateCheckUiState.Checking -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.update_checking))
                }
            }

            is UpdateCheckUiState.UpToDate -> {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.update_up_to_date),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.about_version_label, installedVersion),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                OutlinedButton(
                    onClick = onCheckForUpdates,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.update_check_action))
                }
            }

            is UpdateCheckUiState.UpdateAvailable -> {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(R.string.update_available),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.update_current_version, updateState.installedVersion),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = stringResource(R.string.update_latest_version, updateState.latestVersion),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Button(
                    onClick = { openUrlInBrowser(context, updateState.releaseUrl) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.update_view_release))
                }
            }

            is UpdateCheckUiState.Error -> {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = updateState.message ?: stringResource(R.string.update_error),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Button(
                        onClick = onCheckForUpdates,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.action_retry))
                    }
                }
            }
        }
    }
}

@Composable
private fun OpenSourceSection(context: Context) {
    Column(verticalArrangement = Arrangement.spacedBy(BingeeDimensions.elementSpacing)) {
        Text(
            text = stringResource(R.string.settings_open_source_title),
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = stringResource(R.string.settings_open_source_description),
            style = MaterialTheme.typography.bodyMedium
        )
        Button(
            onClick = { openUrlInBrowser(context, GITHUB_REPO_URL) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.settings_open_source_github_action))
        }
    }
}

private fun openUrlInBrowser(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(intent)
    } catch (_: Exception) {
    }
}
