package com.cydoniancitizen.bingee.feature.settings

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cydoniancitizen.bingee.R
import com.cydoniancitizen.bingee.core.designsystem.theme.BingeeDimensions
import com.cydoniancitizen.bingee.core.model.NotificationCapabilityStatus
import com.cydoniancitizen.bingee.core.model.ReleaseNotificationLeadTime
import com.cydoniancitizen.bingee.feature.credential.CredentialEditor

@Composable
internal fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
    notificationViewModel: ReleaseNotificationSettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val notificationState by notificationViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val permanentlyDenied = !granted && context.findActivity()?.let { activity ->
            !ActivityCompat.shouldShowRequestPermissionRationale(
                activity,
                Manifest.permission.POST_NOTIFICATIONS
            )
        } == true
        notificationViewModel.onPermissionResult(granted, permanentlyDenied)
    }
    SettingsContent(
        state = state,
        onInputChanged = viewModel::onInputChanged,
        onSubmit = viewModel::submit,
        onRetry = viewModel::retry,
        onRequestRemoval = viewModel::requestRemoval,
        onDismissRemoval = viewModel::dismissRemoval,
        onConfirmRemoval = viewModel::confirmRemoval,
        notificationState = notificationState,
        onNotificationEnabledChanged = { enabled ->
            if (!enabled) {
                notificationViewModel.disableNotifications()
            } else if (notificationViewModel.onEnableRequested()) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        },
        onLeadTimeChanged = notificationViewModel::setLeadTime,
        onMovieReleasesChanged = notificationViewModel::setMovieReleases,
        onSeasonPremieresChanged = notificationViewModel::setSeasonPremieres,
        onEpisodeAiringsChanged = notificationViewModel::setEpisodeAirings,
        onOpenNotificationSettings = {
            notificationViewModel.openSystemSettings()
        },
        modifier = modifier
    )
}

@Composable
internal fun SettingsContent(
    state: SettingsUiState,
    onInputChanged: (String) -> Unit,
    onSubmit: (String) -> Unit,
    onRetry: (String) -> Unit,
    onRequestRemoval: () -> Unit,
    onDismissRemoval: () -> Unit,
    onConfirmRemoval: () -> Unit,
    notificationState: ReleaseNotificationSettingsUiState = ReleaseNotificationSettingsUiState(),
    onNotificationEnabledChanged: (Boolean) -> Unit = {},
    onLeadTimeChanged: (ReleaseNotificationLeadTime) -> Unit = {},
    onMovieReleasesChanged: (Boolean) -> Unit = {},
    onSeasonPremieresChanged: (Boolean) -> Unit = {},
    onEpisodeAiringsChanged: (Boolean) -> Unit = {},
    onOpenNotificationSettings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier =
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(BingeeDimensions.screenPadding),
        verticalArrangement = Arrangement.spacedBy(BingeeDimensions.contentSpacing)
    ) {
        Text(
            text = stringResource(R.string.nav_settings),
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.headlineMedium
        )
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
        NotificationSettingsSection(
            state = notificationState,
            onEnabledChanged = onNotificationEnabledChanged,
            onLeadTimeChanged = onLeadTimeChanged,
            onMovieReleasesChanged = onMovieReleasesChanged,
            onSeasonPremieresChanged = onSeasonPremieresChanged,
            onEpisodeAiringsChanged = onEpisodeAiringsChanged,
            onOpenSystemSettings = onOpenNotificationSettings
        )
        HorizontalDivider()
        Text(
            text = stringResource(R.string.about_title),
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.headlineSmall
        )
        Image(
            painter = painterResource(R.drawable.tmdb_logo),
            contentDescription = stringResource(R.string.tmdb_logo_description),
            modifier = Modifier.height(48.dp)
        )
        Text(stringResource(R.string.tmdb_attribution))
        Text(stringResource(R.string.privacy_summary))
        Text(stringResource(R.string.privacy_documentation_reference))
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

@Composable
private fun NotificationSettingsSection(
    state: ReleaseNotificationSettingsUiState,
    onEnabledChanged: (Boolean) -> Unit,
    onLeadTimeChanged: (ReleaseNotificationLeadTime) -> Unit,
    onMovieReleasesChanged: (Boolean) -> Unit,
    onSeasonPremieresChanged: (Boolean) -> Unit,
    onEpisodeAiringsChanged: (Boolean) -> Unit,
    onOpenSystemSettings: () -> Unit
) {
    Text(
        text = stringResource(R.string.settings_notifications_title),
        modifier = Modifier.semantics { heading() },
        style = MaterialTheme.typography.headlineSmall
    )
    SettingSwitchRow(
        label = stringResource(R.string.settings_notifications_enable),
        checked = state.preferences.enabled,
        enabled = !state.isUpdating,
        onCheckedChange = onEnabledChanged
    )
    Text(
        text = stringResource(R.string.settings_notifications_approximate),
        style = MaterialTheme.typography.bodyMedium
    )
    val blocked = state.permanentlyDenied ||
        state.capability == NotificationCapabilityStatus.SYSTEM_BLOCKED ||
        state.capability == NotificationCapabilityStatus.CHANNEL_BLOCKED
    Text(
        text = stringResource(
            when {
                state.preferences.enabled && state.capability == NotificationCapabilityStatus.AVAILABLE ->
                    R.string.settings_notifications_permission_granted
                blocked -> R.string.settings_notifications_permission_blocked
                else -> R.string.settings_notifications_permission_required
            }
        ),
        style = MaterialTheme.typography.bodySmall
    )
    if (blocked) {
        Button(onClick = onOpenSystemSettings) {
            Text(stringResource(R.string.settings_notifications_open_system))
        }
    }
    Text(stringResource(R.string.settings_notifications_lead_time), style = MaterialTheme.typography.titleMedium)
    ReleaseNotificationLeadTime.entries.forEach { leadTime ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !state.isUpdating) { onLeadTimeChanged(leadTime) },
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = state.preferences.leadTime == leadTime,
                onClick = { onLeadTimeChanged(leadTime) },
                enabled = !state.isUpdating
            )
            Text(stringResource(leadTime.labelRes()))
        }
    }
    Text(stringResource(R.string.settings_notifications_categories), style = MaterialTheme.typography.titleMedium)
    SettingSwitchRow(
        stringResource(R.string.settings_notifications_movies),
        state.preferences.movieReleases,
        !state.isUpdating,
        onMovieReleasesChanged
    )
    SettingSwitchRow(
        stringResource(R.string.settings_notifications_seasons),
        state.preferences.seasonPremieres,
        !state.isUpdating,
        onSeasonPremieresChanged
    )
    SettingSwitchRow(
        stringResource(R.string.settings_notifications_episodes),
        state.preferences.episodeAirings,
        !state.isUpdating,
        onEpisodeAiringsChanged
    )
}

@Composable
private fun SettingSwitchRow(label: String, checked: Boolean, enabled: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

private fun ReleaseNotificationLeadTime.labelRes(): Int = when (this) {
    ReleaseNotificationLeadTime.SAME_DAY -> R.string.settings_notifications_same_day
    ReleaseNotificationLeadTime.ONE_DAY -> R.string.settings_notifications_one_day
    ReleaseNotificationLeadTime.THREE_DAYS -> R.string.settings_notifications_three_days
    ReleaseNotificationLeadTime.SEVEN_DAYS -> R.string.settings_notifications_seven_days
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
