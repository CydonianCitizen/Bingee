package com.cydoniancitizen.bingee.feature.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.cydoniancitizen.bingee.core.designsystem.theme.BingeeTheme
import org.junit.Rule
import org.junit.Test

class PrivacySettingsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun privacyScreenDisplaysPrivacyInformationAndCredentialEditor() {
        composeRule.setContent {
            BingeeTheme {
                PrivacySettingsContent(
                    state = PrivacyUiState(),
                    onInputChanged = {},
                    onSubmit = {},
                    onRetry = {},
                    onRequestRemoval = {},
                    onDismissRemoval = {},
                    onConfirmRemoval = {},
                    onBack = {}
                )
            }
        }

        composeRule.onNodeWithText("Privacy").assertIsDisplayed()
        composeRule.onNodeWithText(
            "Bingee does not require an account. Personal tracking data is stored locally on your device. TMDB provides movie and TV metadata."
        )
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("TMDB configuration").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("This product uses the TMDB API but is not endorsed or certified by TMDB.")
            .performScrollTo()
            .assertIsDisplayed()
    }
}
