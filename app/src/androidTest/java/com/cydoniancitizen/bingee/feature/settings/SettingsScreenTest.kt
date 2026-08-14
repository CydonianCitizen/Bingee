package com.cydoniancitizen.bingee.feature.settings

import android.content.Context
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
import androidx.test.core.app.ApplicationProvider
import com.cydoniancitizen.bingee.R
import com.cydoniancitizen.bingee.core.designsystem.theme.BingeeTheme
import com.cydoniancitizen.bingee.data.settings.AppLanguage
import com.cydoniancitizen.bingee.data.settings.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SettingsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()

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

        composeRule.onNodeWithText(context.getString(R.string.settings_appearance_title)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.settings_language_title)).assertIsDisplayed()
        composeRule.onNodeWithText(
            context.getString(R.string.settings_language_en)
        ).performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithText(context.getString(R.string.settings_language_en)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.settings_language_it)).assertIsDisplayed()
        composeRule.onNode(
            hasText(context.getString(R.string.settings_theme_system)) and
                hasAnyAncestor(hasText(context.getString(R.string.settings_language_title)))
        ).assertDoesNotExist()

        composeRule.onNodeWithText(context.getString(R.string.settings_language_it)).performClick()
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

        composeRule.onNodeWithText(context.getString(R.string.settings_appearance_title)).assertIsDisplayed()
        composeRule.onNodeWithText(
            context.getString(R.string.settings_theme_title)
        ).performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithText(context.getString(R.string.settings_theme_system)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.settings_theme_light)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.settings_theme_dark)).assertIsDisplayed()

        composeRule.onNodeWithText(context.getString(R.string.settings_theme_dark)).performClick()
        assertEquals(AppTheme.DARK, selectedTheme)
    }
}
