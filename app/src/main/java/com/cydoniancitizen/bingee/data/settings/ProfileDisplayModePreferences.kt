package com.cydoniancitizen.bingee.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

enum class ProfileCollection { WATCHED, WATCH_LATER, FAVORITES, STATISTICS }
enum class ProfileCategory { MOVIES, TV_SERIES }
enum class ProfileViewMode { LIST, GRID }

data class ProfileDisplayModes(
    val watchedMovies: ProfileViewMode = ProfileViewMode.LIST,
    val watchedTvSeries: ProfileViewMode = ProfileViewMode.LIST,
    val watchLaterMovies: ProfileViewMode = ProfileViewMode.LIST,
    val watchLaterTvSeries: ProfileViewMode = ProfileViewMode.LIST,
    val favoritesMovies: ProfileViewMode = ProfileViewMode.LIST,
    val favoritesTvSeries: ProfileViewMode = ProfileViewMode.LIST
) {
    fun getMode(collection: ProfileCollection, category: ProfileCategory): ProfileViewMode = when (collection) {
        ProfileCollection.WATCHED -> when (category) {
            ProfileCategory.MOVIES -> watchedMovies
            ProfileCategory.TV_SERIES -> watchedTvSeries
        }
        ProfileCollection.WATCH_LATER -> when (category) {
            ProfileCategory.MOVIES -> watchLaterMovies
            ProfileCategory.TV_SERIES -> watchLaterTvSeries
        }
        ProfileCollection.FAVORITES -> when (category) {
            ProfileCategory.MOVIES -> favoritesMovies
            ProfileCategory.TV_SERIES -> favoritesTvSeries
        }
        ProfileCollection.STATISTICS -> ProfileViewMode.LIST
    }
}

interface ProfileDisplayModePreferences {
    fun observeDisplayModes(): Flow<ProfileDisplayModes>
    suspend fun setDisplayMode(collection: ProfileCollection, category: ProfileCategory, mode: ProfileViewMode)
}

@Singleton
internal class DataStoreProfileDisplayModePreferences @Inject constructor(
    @param:ApplicationContext private val context: Context
) : ProfileDisplayModePreferences {

    override fun observeDisplayModes(): Flow<ProfileDisplayModes> = context.bingeePreferences.data
        .catch { failure ->
            if (failure is IOException) {
                emit(androidx.datastore.preferences.core.emptyPreferences())
            } else {
                throw failure
            }
        }
        .map { prefs ->
            ProfileDisplayModes(
                watchedMovies = parseViewMode(prefs[KEY_WATCHED_MOVIES]),
                watchedTvSeries = parseViewMode(prefs[KEY_WATCHED_TV]),
                watchLaterMovies = parseViewMode(prefs[KEY_WATCH_LATER_MOVIES]),
                watchLaterTvSeries = parseViewMode(prefs[KEY_WATCH_LATER_TV]),
                favoritesMovies = parseViewMode(prefs[KEY_FAVORITES_MOVIES]),
                favoritesTvSeries = parseViewMode(prefs[KEY_FAVORITES_TV])
            )
        }

    override suspend fun setDisplayMode(
        collection: ProfileCollection,
        category: ProfileCategory,
        mode: ProfileViewMode
    ) {
        if (collection == ProfileCollection.STATISTICS) return
        val key = when (collection) {
            ProfileCollection.WATCHED -> when (category) {
                ProfileCategory.MOVIES -> KEY_WATCHED_MOVIES
                ProfileCategory.TV_SERIES -> KEY_WATCHED_TV
            }
            ProfileCollection.WATCH_LATER -> when (category) {
                ProfileCategory.MOVIES -> KEY_WATCH_LATER_MOVIES
                ProfileCategory.TV_SERIES -> KEY_WATCH_LATER_TV
            }
            ProfileCollection.FAVORITES -> when (category) {
                ProfileCategory.MOVIES -> KEY_FAVORITES_MOVIES
                ProfileCategory.TV_SERIES -> KEY_FAVORITES_TV
            }
            ProfileCollection.STATISTICS -> return
        }
        context.bingeePreferences.edit { prefs ->
            prefs[key] = mode.name
        }
    }

    private fun parseViewMode(value: String?): ProfileViewMode = try {
        value?.let { ProfileViewMode.valueOf(it) } ?: ProfileViewMode.LIST
    } catch (_: Exception) {
        ProfileViewMode.LIST
    }

    private companion object {
        val KEY_WATCHED_MOVIES = stringPreferencesKey("profile_view_watched_movies")
        val KEY_WATCHED_TV = stringPreferencesKey("profile_view_watched_tv")
        val KEY_WATCH_LATER_MOVIES = stringPreferencesKey("profile_view_watch_later_movies")
        val KEY_WATCH_LATER_TV = stringPreferencesKey("profile_view_watch_later_tv")
        val KEY_FAVORITES_MOVIES = stringPreferencesKey("profile_view_favorites_movies")
        val KEY_FAVORITES_TV = stringPreferencesKey("profile_view_favorites_tv")
    }
}
