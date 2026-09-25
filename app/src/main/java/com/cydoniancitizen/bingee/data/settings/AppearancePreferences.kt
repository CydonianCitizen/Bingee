package com.cydoniancitizen.bingee.data.settings

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

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
internal class PortableAppearancePreferences @Inject constructor(
    private val preferences: PortableUserPreferencesStore
) : AppearancePreferences {

    override fun observeTheme(): Flow<AppTheme> = preferences.observe().map { parseTheme(it.theme) }

    override suspend fun setTheme(theme: AppTheme) {
        preferences.update { it.copy(theme = theme.name) }
    }

    override fun observeLanguage(): Flow<AppLanguage> = preferences.observe().map { parseLanguage(it.language) }

    override suspend fun setLanguage(language: AppLanguage) {
        preferences.update { it.copy(language = language.name) }
    }

    override suspend fun getEffectiveTmdbLanguage(): String = observeLanguage().first().toTmdbLanguageTag()

    private fun parseTheme(value: String): AppTheme = try {
        AppTheme.valueOf(value)
    } catch (_: Exception) {
        AppTheme.SYSTEM_DEFAULT
    }

    private fun parseLanguage(value: String): AppLanguage = AppLanguage.fromPreferenceValue(value)
}
