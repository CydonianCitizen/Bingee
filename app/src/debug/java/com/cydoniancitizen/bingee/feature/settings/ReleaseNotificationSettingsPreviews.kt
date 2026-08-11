package com.cydoniancitizen.bingee.feature.settings

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.cydoniancitizen.bingee.core.designsystem.theme.BingeeTheme
import com.cydoniancitizen.bingee.core.model.NotificationCapabilityStatus
import com.cydoniancitizen.bingee.core.model.ReleaseNotificationLeadTime
import com.cydoniancitizen.bingee.core.model.ReleaseNotificationPreferences

@Preview(showBackground = true)
@Composable
private fun NotificationsDisabledPreview() = PreviewNotifications(
    ReleaseNotificationSettingsUiState()
)

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, fontScale = 2f)
@Composable
private fun NotificationsEnabledLargeDarkPreview() = PreviewNotifications(
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
private fun NotificationsBlockedPreview() = PreviewNotifications(
    ReleaseNotificationSettingsUiState(
        capability = NotificationCapabilityStatus.CHANNEL_BLOCKED,
        permanentlyDenied = true
    )
)

@Composable
private fun PreviewNotifications(notificationState: ReleaseNotificationSettingsUiState, darkTheme: Boolean = false) {
    BingeeTheme(darkTheme = darkTheme) {
        NotificationSettingsContent(
            state = notificationState,
            onBack = {},
            onNotificationEnabledChanged = {},
            onLeadTimeChanged = {},
            onMovieReleasesChanged = {},
            onSeasonPremieresChanged = {},
            onEpisodeAiringsChanged = {},
            onOpenSystemSettings = {},
            onDismissError = {}
        )
    }
}
