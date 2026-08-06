package com.cydoniancitizen.bingee.feature.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.cydoniancitizen.bingee.core.designsystem.theme.BingeeTheme
import com.cydoniancitizen.bingee.data.settings.AppLanguage
import com.cydoniancitizen.bingee.data.settings.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SettingsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun languageDropdownShowsOnlyEnglishAndItalianoAndNoSystemDefault() {
        var selectedLanguage by mutableStateOf(AppLanguage.ENGLISH)
        composeRule.setContent {
            BingeeTheme {
                SettingsContent(
                    state = SettingsUiState(language = selectedLanguage),
                    onInputChanged = {},
                    onSubmit = {},
                    onRetry = {},
                    onRequestRemoval = {},
                    onDismissRemoval = {},
                    onConfirmRemoval = {},
                    onSetLanguage = { selectedLanguage = it }
                )
            }
        }

        composeRule.onNodeWithText("Language").assertIsDisplayed()
        composeRule.onNodeWithText("English").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("System default").assertDoesNotExist()

        composeRule.onNodeWithText("English").performClick()
        composeRule.onNodeWithText("Italiano").assertIsDisplayed()
        composeRule.onNodeWithText("System default").assertDoesNotExist()

        composeRule.onNodeWithText("Italiano").performClick()
        assertEquals(AppLanguage.ITALIAN, selectedLanguage)
    }

    @Test
    fun themeDropdownShowsSystemDefaultLightAndDarkOptions() {
        var selectedTheme by mutableStateOf(AppTheme.SYSTEM_DEFAULT)
        composeRule.setContent {
            BingeeTheme {
                SettingsContent(
                    state = SettingsUiState(theme = selectedTheme),
                    onInputChanged = {},
                    onSubmit = {},
                    onRetry = {},
                    onRequestRemoval = {},
                    onDismissRemoval = {},
                    onConfirmRemoval = {},
                    onSetTheme = { selectedTheme = it }
                )
            }
        }

        composeRule.onNodeWithText("Appearance").assertIsDisplayed()
        composeRule.onNodeWithText("Theme").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithText("System default").performClick()
        composeRule.onNodeWithText("Light").assertIsDisplayed()
        composeRule.onNodeWithText("Dark").assertIsDisplayed()

        composeRule.onNodeWithText("Dark").performClick()
        assertEquals(AppTheme.DARK, selectedTheme)
    }

    @Test
    fun openSourceSectionDisplaysGitHubAction() {
        composeRule.setContent {
            BingeeTheme {
                SettingsContent(
                    state = SettingsUiState(),
                    onInputChanged = {},
                    onSubmit = {},
                    onRetry = {},
                    onRequestRemoval = {},
                    onDismissRemoval = {},
                    onConfirmRemoval = {}
                )
            }
        }

        composeRule.onNodeWithText("Open Source").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(
            "Bingee is open source. You can help improve the app by reporting issues, suggesting features, or contributing code on GitHub."
        ).assertIsDisplayed()
        composeRule.onNodeWithText("View on GitHub").assertIsDisplayed()

        // Verify click does not crash even without real browser intent handling in unit test context
        composeRule.onNodeWithText("View on GitHub").performClick()
    }
}
