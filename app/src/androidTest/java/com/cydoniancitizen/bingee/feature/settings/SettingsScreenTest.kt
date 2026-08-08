package com.cydoniancitizen.bingee.feature.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
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
                AppearanceLanguageSettingsContent(
                    theme = AppTheme.SYSTEM_DEFAULT,
                    language = selectedLanguage,
                    onSetTheme = {},
                    onSetLanguage = { selectedLanguage = it },
                    onBack = {}
                )
            }
        }

        composeRule.onNodeWithText("Appearance & Language").assertIsDisplayed()
        composeRule.onNodeWithText("Language").assertIsDisplayed()
        composeRule.onNodeWithText("English").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithText("English").performClick()
        composeRule.onNodeWithText("Italiano").assertIsDisplayed()
        composeRule.onNode(hasText("System default") and hasAnyAncestor(hasText("Language"))).assertDoesNotExist()

        composeRule.onNodeWithText("Italiano").performClick()
        assertEquals(AppLanguage.ITALIAN, selectedLanguage)
    }

    @Test
    fun themeDropdownShowsSystemDefaultLightAndDarkOptions() {
        var selectedTheme by mutableStateOf(AppTheme.SYSTEM_DEFAULT)
        composeRule.setContent {
            BingeeTheme {
                AppearanceLanguageSettingsContent(
                    theme = selectedTheme,
                    language = AppLanguage.ENGLISH,
                    onSetTheme = { selectedTheme = it },
                    onSetLanguage = {},
                    onBack = {}
                )
            }
        }

        composeRule.onNodeWithText("Appearance & Language").assertIsDisplayed()
        composeRule.onNodeWithText("Theme").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithText("System default").performClick()
        composeRule.onNodeWithText("Light").assertIsDisplayed()
        composeRule.onNodeWithText("Dark").assertIsDisplayed()

        composeRule.onNodeWithText("Dark").performClick()
        assertEquals(AppTheme.DARK, selectedTheme)
    }
}
