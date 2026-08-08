package com.cydoniancitizen.bingee.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal fun SettingsScreen(
    onNavigateToAppearance: () -> Unit = {},
    onNavigateToNotifications: () -> Unit = {},
    onNavigateToDataBackup: () -> Unit = {},
    onNavigateToPrivacy: () -> Unit = {},
    onNavigateToAbout: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    SettingsIndexScreen(
        onNavigateToAppearance = onNavigateToAppearance,
        onNavigateToNotifications = onNavigateToNotifications,
        onNavigateToDataBackup = onNavigateToDataBackup,
        onNavigateToPrivacy = onNavigateToPrivacy,
        onNavigateToAbout = onNavigateToAbout,
        modifier = modifier
    )
}
