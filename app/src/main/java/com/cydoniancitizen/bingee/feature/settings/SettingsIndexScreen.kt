package com.cydoniancitizen.bingee.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsIndexScreen(
    onBack: () -> Unit,
    onNavigateToAppearance: () -> Unit,
    onNavigateToNotifications: () -> Unit,
    onNavigateToDataBackup: () -> Unit,
    onNavigateToPrivacy: () -> Unit,
    onNavigateToAbout: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.nav_settings),
                        modifier = Modifier.semantics { heading() }
                    )
                },
                // Settings is a secondary destination, so it owes the user a visible Up action and
                // not only the system Back gesture.
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.detail_back)
                        )
                    }
                },
                // The application shell already pads its content by the status bar inset, so the
                // bar must not apply it a second time.
                windowInsets = WindowInsets(0, 0, 0, 0)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(BingeeDimensions.screenPadding),
            verticalArrangement = Arrangement.spacedBy(BingeeDimensions.contentSpacing)
        ) {
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
}

@Composable
private fun SettingsNavigationCard(icon: ImageVector, title: String, description: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
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
