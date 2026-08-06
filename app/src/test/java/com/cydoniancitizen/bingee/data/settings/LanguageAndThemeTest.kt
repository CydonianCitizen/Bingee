package com.cydoniancitizen.bingee.data.settings

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LanguageAndThemeTest {

    @Test
    fun appLanguageMapsToApplicationLanguageTags() {
        assertEquals("en", AppLanguage.ENGLISH.languageTag)
        assertEquals("it", AppLanguage.ITALIAN.languageTag)
    }

    @Test
    fun englishLanguageUsesEnglishApplicationLocale() {
        assertEquals("en", AppLanguage.ENGLISH.toApplicationLocales().toLanguageTags())
    }

    @Test
    fun italianLanguageUsesItalianApplicationLocale() {
        assertEquals("it", AppLanguage.ITALIAN.toApplicationLocales().toLanguageTags())
    }

    @Test
    fun persistedLanguageValuesMapSafelyAndMigrateSystemDefaultToEnglish() {
        assertEquals("ENGLISH", AppLanguage.ENGLISH.name)
        assertEquals("ITALIAN", AppLanguage.ITALIAN.name)
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromPreferenceValue("SYSTEM"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromPreferenceValue(null))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromPreferenceValue("UNKNOWN"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromPreferenceValue("ENGLISH"))
        assertEquals(AppLanguage.ITALIAN, AppLanguage.fromPreferenceValue("ITALIAN"))
    }

    @Test
    fun appLanguageMapsToCorrectTmdbLanguageTag() {
        val englishTag = AppLanguage.ENGLISH.toTmdbLanguageTag(Locale.ITALIAN)
        assertEquals("en-US", englishTag)

        val italianTag = AppLanguage.ITALIAN.toTmdbLanguageTag(Locale.ENGLISH)
        assertEquals("it-IT", italianTag)

        val unsupportedTag = AppLanguage.ENGLISH.toTmdbLanguageTag(Locale.GERMAN)
        assertEquals("en-US", unsupportedTag)
    }

    @Test
    fun appThemeValuesAreStable() {
        assertEquals("SYSTEM_DEFAULT", AppTheme.SYSTEM_DEFAULT.name)
        assertEquals("LIGHT", AppTheme.LIGHT.name)
        assertEquals("DARK", AppTheme.DARK.name)
    }
}
