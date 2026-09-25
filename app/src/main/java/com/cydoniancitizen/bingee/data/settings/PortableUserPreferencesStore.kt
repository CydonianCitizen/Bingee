package com.cydoniancitizen.bingee.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.room.withTransaction
import com.cydoniancitizen.bingee.data.library.local.BingeeDatabase
import com.cydoniancitizen.bingee.data.library.local.PortablePreferencesEntity
import com.cydoniancitizen.bingee.data.library.local.PortableSnapshotDao
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow

/** Room is the source of truth so a backup restore can replace every portable preference atomically. */
@Singleton
internal class PortableUserPreferencesStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val database: BingeeDatabase,
    private val portableDao: PortableSnapshotDao
) {
    fun observe(): Flow<PortablePreferencesEntity> = flow {
        ensureLegacyBridge()
        emitAll(portableDao.observePreferences().filterNotNull())
    }

    suspend fun current(): PortablePreferencesEntity {
        ensureLegacyBridge()
        return checkNotNull(portableDao.getPreferences())
    }

    suspend fun update(transform: (PortablePreferencesEntity) -> PortablePreferencesEntity) {
        ensureLegacyBridge()
        database.withTransaction {
            val current = checkNotNull(portableDao.getPreferences())
            portableDao.replacePreferences(transform(current).copy(legacySettingsBridgeCompleted = true))
        }
    }

    suspend fun ensureLegacyBridge() {
        if (portableDao.getPreferences()?.legacySettingsBridgeCompleted == true) return
        val legacy = readLegacySettings()
        database.withTransaction {
            val current = portableDao.getPreferences() ?: PortablePreferencesEntity()
            if (!current.legacySettingsBridgeCompleted) {
                portableDao.replacePreferences(
                    current.copy(
                        theme = legacy.theme.name,
                        language = legacy.language.name,
                        hideEpisodeSpoilers = legacy.hideEpisodeSpoilers,
                        watchedMoviesDisplayMode = legacy.displayModes.watchedMovies.name,
                        watchedTvSeriesDisplayMode = legacy.displayModes.watchedTvSeries.name,
                        watchLaterMoviesDisplayMode = legacy.displayModes.watchLaterMovies.name,
                        watchLaterTvSeriesDisplayMode = legacy.displayModes.watchLaterTvSeries.name,
                        favoritesMoviesDisplayMode = legacy.displayModes.favoritesMovies.name,
                        favoritesTvSeriesDisplayMode = legacy.displayModes.favoritesTvSeries.name,
                        legacySettingsBridgeCompleted = true
                    )
                )
            }
        }
    }

    private suspend fun readLegacySettings(): LegacySettings {
        val values = context.bingeePreferenceData.first()
        return LegacySettings(
            theme = values[KEY_THEME]?.let { runCatching { AppTheme.valueOf(it) }.getOrNull() }
                ?: AppTheme.SYSTEM_DEFAULT,
            language = AppLanguage.fromPreferenceValue(values[KEY_LANGUAGE]),
            hideEpisodeSpoilers = values[KEY_HIDE_SPOILERS] ?: false,
            displayModes = ProfileDisplayModes(
                watchedMovies = viewMode(values[KEY_WATCHED_MOVIES]),
                watchedTvSeries = viewMode(values[KEY_WATCHED_TV]),
                watchLaterMovies = viewMode(values[KEY_WATCH_LATER_MOVIES]),
                watchLaterTvSeries = viewMode(values[KEY_WATCH_LATER_TV]),
                favoritesMovies = viewMode(values[KEY_FAVORITES_MOVIES]),
                favoritesTvSeries = viewMode(values[KEY_FAVORITES_TV])
            )
        )
    }

    private fun viewMode(value: String?): ProfileViewMode = try {
        value?.let(ProfileViewMode::valueOf) ?: ProfileViewMode.LIST
    } catch (_: Exception) {
        ProfileViewMode.LIST
    }

    private data class LegacySettings(
        val theme: AppTheme,
        val language: AppLanguage,
        val hideEpisodeSpoilers: Boolean,
        val displayModes: ProfileDisplayModes
    )

    private companion object {
        val KEY_THEME = stringPreferencesKey("app_theme")
        val KEY_LANGUAGE = stringPreferencesKey("app_language")
        val KEY_HIDE_SPOILERS = booleanPreferencesKey("hide_episode_spoilers")
        val KEY_WATCHED_MOVIES = stringPreferencesKey("profile_view_watched_movies")
        val KEY_WATCHED_TV = stringPreferencesKey("profile_view_watched_tv")
        val KEY_WATCH_LATER_MOVIES = stringPreferencesKey("profile_view_watch_later_movies")
        val KEY_WATCH_LATER_TV = stringPreferencesKey("profile_view_watch_later_tv")
        val KEY_FAVORITES_MOVIES = stringPreferencesKey("profile_view_favorites_movies")
        val KEY_FAVORITES_TV = stringPreferencesKey("profile_view_favorites_tv")
    }
}
