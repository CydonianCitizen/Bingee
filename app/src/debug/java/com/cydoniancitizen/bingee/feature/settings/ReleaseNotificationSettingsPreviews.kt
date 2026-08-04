package com.cydoniancitizen.bingee.feature.settings

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.cydoniancitizen.bingee.core.credential.TmdbCredentialStatus
import com.cydoniancitizen.bingee.core.designsystem.theme.BingeeTheme
import com.cydoniancitizen.bingee.core.model.NotificationCapabilityStatus
import com.cydoniancitizen.bingee.core.model.ReleaseNotificationLeadTime
import com.cydoniancitizen.bingee.core.model.ReleaseNotificationPreferences

@Preview(showBackground = true)
@Composable
private fun NotificationsDisabledPreview() = PreviewSettings(
    ReleaseNotificationSettingsUiState()
)

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, fontScale = 2f)
@Composable
private fun NotificationsEnabledLargeDarkPreview() = PreviewSettings(
    ReleaseNotificationSettingsUiState(
        preferences = ReleaseNotificationPreferences(
            enabled = true,
            leadTime = ReleaseNotificationLeadTime.SEVEN_DAYS,
            seasonPremieres = false
        ),
        capability = NotificationCapabilityStatus.AVAILABLE
    ),
    darkTheme = true
)

@Preview(showBackground = true)
@Composable
private fun NotificationsBlockedPreview() = PreviewSettings(
    ReleaseNotificationSettingsUiState(
        capability = NotificationCapabilityStatus.CHANNEL_BLOCKED,
        permanentlyDenied = true
    )
)

@Composable
private fun PreviewSettings(notificationState: ReleaseNotificationSettingsUiState, darkTheme: Boolean = false) {
    BingeeTheme(darkTheme = darkTheme) {
        SettingsContent(
            state = SettingsUiState(credentialStatus = TmdbCredentialStatus.NotConfigured),
            onInputChanged = {},
            onSubmit = {},
            onRetry = {},
            onRequestRemoval = {},
            onDismissRemoval = {},
            onConfirmRemoval = {},
            notificationState = notificationState
        )
    }
}
