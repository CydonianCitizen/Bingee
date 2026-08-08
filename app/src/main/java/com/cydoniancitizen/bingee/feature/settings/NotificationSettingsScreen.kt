package com.cydoniancitizen.bingee.feature.settings

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.core.app.ActivityCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cydoniancitizen.bingee.R
import com.cydoniancitizen.bingee.core.designsystem.theme.BingeeDimensions
import com.cydoniancitizen.bingee.core.model.NotificationCapabilityStatus
import com.cydoniancitizen.bingee.core.model.ReleaseNotificationLeadTime

@Composable
internal fun NotificationSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReleaseNotificationSettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
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
        viewModel.onPermissionResult(granted, permanentlyDenied)
    }

    NotificationSettingsContent(
        state = state,
        onBack = onBack,
        onNotificationEnabledChanged = { enabled ->
            if (!enabled) {
                viewModel.disableNotifications()
            } else if (viewModel.onEnableRequested()) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        },
        onLeadTimeChanged = viewModel::setLeadTime,
        onMovieReleasesChanged = viewModel::setMovieReleases,
        onSeasonPremieresChanged = viewModel::setSeasonPremieres,
        onEpisodeAiringsChanged = viewModel::setEpisodeAirings,
        onOpenSystemSettings = viewModel::openSystemSettings,
        modifier = modifier
    )
}

@Composable
internal fun NotificationSettingsContent(
    state: ReleaseNotificationSettingsUiState,
    onBack: () -> Unit,
    onNotificationEnabledChanged: (Boolean) -> Unit,
    onLeadTimeChanged: (ReleaseNotificationLeadTime) -> Unit,
    onMovieReleasesChanged: (Boolean) -> Unit,
    onSeasonPremieresChanged: (Boolean) -> Unit,
    onEpisodeAiringsChanged: (Boolean) -> Unit,
    onOpenSystemSettings: () -> Unit,
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
                text = stringResource(R.string.notifications_title),
                modifier = Modifier.weight(1f).semantics { heading() },
                style = MaterialTheme.typography.headlineMedium
            )
        }

        SettingSwitchRow(
            label = stringResource(R.string.settings_notifications_enable),
            checked = state.preferences.enabled,
            enabled = !state.isUpdating,
            onCheckedChange = onNotificationEnabledChanged
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

        HorizontalDivider()

        Text(
            stringResource(R.string.settings_notifications_lead_time),
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleMedium
        )
        ReleaseNotificationLeadTime.entries.forEach { leadTime ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = state.preferences.leadTime == leadTime,
                        enabled = !state.isUpdating,
                        role = Role.RadioButton,
                        onClick = { onLeadTimeChanged(leadTime) }
                    )
                    .semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = state.preferences.leadTime == leadTime,
                    onClick = null,
                    enabled = !state.isUpdating
                )
                Text(stringResource(leadTime.labelRes()))
            }
        }

        HorizontalDivider()

        Text(
            stringResource(R.string.settings_notifications_categories),
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleMedium
        )
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
}

@Composable
private fun SettingSwitchRow(label: String, checked: Boolean, enabled: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange
            )
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
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
