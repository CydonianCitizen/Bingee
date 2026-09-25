package com.cydoniancitizen.bingee.data.settings

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Whether unwatched episodes hide their still and title. */
interface SpoilerPreferences {
    fun observeHideSpoilers(): Flow<Boolean>
    suspend fun setHideSpoilers(hide: Boolean)
}

@Singleton
internal class PortableSpoilerPreferences @Inject constructor(private val preferences: PortableUserPreferencesStore) :
    SpoilerPreferences {
    override fun observeHideSpoilers(): Flow<Boolean> = preferences.observe().map { it.hideEpisodeSpoilers }

    override suspend fun setHideSpoilers(hide: Boolean) {
        preferences.update { it.copy(hideEpisodeSpoilers = hide) }
    }
}
