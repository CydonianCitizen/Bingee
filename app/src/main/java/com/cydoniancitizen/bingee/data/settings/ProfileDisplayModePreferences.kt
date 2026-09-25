package com.cydoniancitizen.bingee.data.settings

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ProfileCollection { WATCHED, WATCH_LATER, FAVORITES }
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
    }
}

interface ProfileDisplayModePreferences {
    fun observeDisplayModes(): Flow<ProfileDisplayModes>
    suspend fun setDisplayMode(collection: ProfileCollection, category: ProfileCategory, mode: ProfileViewMode)
}

@Singleton
internal class PortableProfileDisplayModePreferences @Inject constructor(
    private val preferences: PortableUserPreferencesStore
) : ProfileDisplayModePreferences {

    override fun observeDisplayModes(): Flow<ProfileDisplayModes> = preferences.observe()
        .map { stored ->
            ProfileDisplayModes(
                watchedMovies = parseViewMode(stored.watchedMoviesDisplayMode),
                watchedTvSeries = parseViewMode(stored.watchedTvSeriesDisplayMode),
                watchLaterMovies = parseViewMode(stored.watchLaterMoviesDisplayMode),
                watchLaterTvSeries = parseViewMode(stored.watchLaterTvSeriesDisplayMode),
                favoritesMovies = parseViewMode(stored.favoritesMoviesDisplayMode),
                favoritesTvSeries = parseViewMode(stored.favoritesTvSeriesDisplayMode)
            )
        }

    override suspend fun setDisplayMode(
        collection: ProfileCollection,
        category: ProfileCategory,
        mode: ProfileViewMode
    ) {
        preferences.update { stored ->
            val field = when (collection) {
                ProfileCollection.WATCHED -> when (category) {
                    ProfileCategory.MOVIES -> stored.copy(watchedMoviesDisplayMode = mode.name)
                    ProfileCategory.TV_SERIES -> stored.copy(watchedTvSeriesDisplayMode = mode.name)
                }
                ProfileCollection.WATCH_LATER -> when (category) {
                    ProfileCategory.MOVIES -> stored.copy(watchLaterMoviesDisplayMode = mode.name)
                    ProfileCategory.TV_SERIES -> stored.copy(watchLaterTvSeriesDisplayMode = mode.name)
                }
                ProfileCollection.FAVORITES -> when (category) {
                    ProfileCategory.MOVIES -> stored.copy(favoritesMoviesDisplayMode = mode.name)
                    ProfileCategory.TV_SERIES -> stored.copy(favoritesTvSeriesDisplayMode = mode.name)
                }
            }
            field
        }
    }

    private fun parseViewMode(value: String): ProfileViewMode = try {
        ProfileViewMode.valueOf(value)
    } catch (_: Exception) {
        ProfileViewMode.LIST
    }
}
