package com.cydoniancitizen.bingee.data.settings

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
    fun persistedLanguageValuesMapSafelyAndNormalizeSystemDefaultWithoutSideEffect() {
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
        assertEquals("en-US", AppLanguage.ENGLISH.toTmdbLanguageTag())
        assertEquals("it-IT", AppLanguage.ITALIAN.toTmdbLanguageTag())
    }

    @Test
    fun appThemeValuesAreStable() {
        assertEquals("SYSTEM_DEFAULT", AppTheme.SYSTEM_DEFAULT.name)
        assertEquals("LIGHT", AppTheme.LIGHT.name)
        assertEquals("DARK", AppTheme.DARK.name)
    }
}
