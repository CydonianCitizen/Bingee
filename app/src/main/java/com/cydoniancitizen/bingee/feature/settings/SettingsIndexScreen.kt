package com.cydoniancitizen.bingee.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.cydoniancitizen.bingee.R
import com.cydoniancitizen.bingee.core.designsystem.theme.BingeeDimensions

@Composable
internal fun SettingsIndexScreen(
    onNavigateToAppearance: () -> Unit,
    onNavigateToNotifications: () -> Unit,
    onNavigateToDataBackup: () -> Unit,
    onNavigateToPrivacy: () -> Unit,
    onNavigateToAbout: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
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

        SettingsNavigationCard(
            icon = Icons.Default.Settings,
            title = stringResource(R.string.settings_nav_appearance),
            description = stringResource(R.string.settings_nav_appearance_desc),
            onClick = onNavigateToAppearance
        )

        SettingsNavigationCard(
            icon = Icons.Default.Notifications,
            title = stringResource(R.string.settings_nav_notifications),
            description = stringResource(R.string.settings_nav_notifications_desc),
            onClick = onNavigateToNotifications
        )

        SettingsNavigationCard(
            icon = Icons.Default.Build,
            title = stringResource(R.string.settings_nav_data_backup),
            description = stringResource(R.string.settings_nav_data_backup_desc),
            onClick = onNavigateToDataBackup
        )

        SettingsNavigationCard(
            icon = Icons.Default.Lock,
            title = stringResource(R.string.settings_nav_privacy),
            description = stringResource(R.string.settings_nav_privacy_desc),
            onClick = onNavigateToPrivacy
        )

        SettingsNavigationCard(
            icon = Icons.Default.Info,
            title = stringResource(R.string.settings_nav_about),
            description = stringResource(R.string.settings_nav_about_desc),
            onClick = onNavigateToAbout
        )
    }
}

@Composable
private fun SettingsNavigationCard(icon: ImageVector, title: String, description: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
