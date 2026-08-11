package com.cydoniancitizen.bingee.feature.settings

import com.cydoniancitizen.bingee.data.settings.AppLanguage
import com.cydoniancitizen.bingee.data.settings.AppTheme
import com.cydoniancitizen.bingee.data.settings.AppearancePreferences
import com.cydoniancitizen.bingee.data.settings.toTmdbLanguageTag
import com.cydoniancitizen.bingee.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppearanceLanguageViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun loadsAndSavesLanguageAndTheme() = runTest(mainDispatcherRule.dispatcher) {
        val preferences = FakeAppearancePreferences(AppTheme.DARK, AppLanguage.ITALIAN)
        val viewModel = AppearanceLanguageViewModel(preferences)
        advanceUntilIdle()

        assertEquals(AppTheme.DARK, viewModel.uiState.value.theme)
        assertEquals(AppLanguage.ITALIAN, viewModel.uiState.value.language)

        viewModel.setTheme(AppTheme.LIGHT)
        viewModel.setLanguage(AppLanguage.ENGLISH)
        advanceUntilIdle()

        assertEquals(AppTheme.LIGHT, preferences.theme.value)
        assertEquals(AppLanguage.ENGLISH, preferences.language.value)
        assertTrue(preferences.themeWrites.contains(AppTheme.LIGHT))
        assertTrue(preferences.languageWrites.contains(AppLanguage.ENGLISH))
    }

    @Test
    fun legacySystemLanguageMapsToEnglishWithoutWriteSideEffect() {
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromPreferenceValue("SYSTEM"))
    }

    private class FakeAppearancePreferences(theme: AppTheme, language: AppLanguage) : AppearancePreferences {
        val theme = MutableStateFlow(theme)
        val language = MutableStateFlow(language)
        val themeWrites = mutableListOf<AppTheme>()
        val languageWrites = mutableListOf<AppLanguage>()

        override fun observeTheme(): Flow<AppTheme> = theme

        override suspend fun setTheme(theme: AppTheme) {
            themeWrites += theme
            this.theme.value = theme
        }

        override fun observeLanguage(): Flow<AppLanguage> = language

        override suspend fun setLanguage(language: AppLanguage) {
            languageWrites += language
            this.language.value = language
        }

        override suspend fun getEffectiveTmdbLanguage(): String = language.value.toTmdbLanguageTag()
    }
}
