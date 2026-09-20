package com.cydoniancitizen.bingee.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

enum class AppTheme { SYSTEM_DEFAULT, LIGHT, DARK }
enum class AppLanguage(val languageTag: String) {
    ENGLISH("en"),
    ITALIAN("it");

    companion object {
        internal fun fromPreferenceValue(value: String?): AppLanguage = when (value) {
            "ITALIAN" -> ITALIAN
            else -> ENGLISH
        }
    }
}

interface AppearancePreferences {
    fun observeTheme(): Flow<AppTheme>
    suspend fun setTheme(theme: AppTheme)

    fun observeLanguage(): Flow<AppLanguage>
    suspend fun setLanguage(language: AppLanguage)

    suspend fun getEffectiveTmdbLanguage(): String
}

fun AppLanguage.toTmdbLanguageTag(): String = when (this) {
    AppLanguage.ENGLISH -> "en-US"
    AppLanguage.ITALIAN -> "it-IT"
}

@Singleton
internal class DataStoreAppearancePreferences @Inject constructor(
    @param:ApplicationContext private val context: Context
) : AppearancePreferences {

    override fun observeTheme(): Flow<AppTheme> = context.bingeePreferenceData
        .map { prefs ->
            parseTheme(prefs[KEY_THEME])
        }

    override suspend fun setTheme(theme: AppTheme) {
        context.bingeePreferences.edit { prefs ->
            prefs[KEY_THEME] = theme.name
        }
    }

    override fun observeLanguage(): Flow<AppLanguage> = context.bingeePreferenceData
        .map { prefs ->
            val raw = prefs[KEY_LANGUAGE]
            parseLanguage(raw)
        }

    override suspend fun setLanguage(language: AppLanguage) {
        context.bingeePreferences.edit { prefs ->
            prefs[KEY_LANGUAGE] = language.name
        }
    }

    override suspend fun getEffectiveTmdbLanguage(): String = observeLanguage().first().toTmdbLanguageTag()

    private fun parseTheme(value: String?): AppTheme = try {
        value?.let { AppTheme.valueOf(it) } ?: AppTheme.SYSTEM_DEFAULT
    } catch (_: Exception) {
        AppTheme.SYSTEM_DEFAULT
    }

    private fun parseLanguage(value: String?): AppLanguage = AppLanguage.fromPreferenceValue(value)

    private companion object {
        val KEY_THEME = stringPreferencesKey("app_theme")
        val KEY_LANGUAGE = stringPreferencesKey("app_language")
    }
}
