package com.cydoniancitizen.bingee.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.cydoniancitizen.bingee.R
import com.cydoniancitizen.bingee.core.designsystem.component.PlaceholderScreen
import com.cydoniancitizen.bingee.core.designsystem.theme.BingeeTheme

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(
        titleRes = R.string.nav_settings,
        modifier = modifier,
    )
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    BingeeTheme {
        SettingsScreen()
    }
}
