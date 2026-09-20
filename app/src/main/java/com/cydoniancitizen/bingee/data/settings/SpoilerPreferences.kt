package com.cydoniancitizen.bingee.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Whether unwatched episodes hide their still and title. A display choice of this device, not backed up. */
interface SpoilerPreferences {
    fun observeHideSpoilers(): Flow<Boolean>
    suspend fun setHideSpoilers(hide: Boolean)
}

@Singleton
internal class DataStoreSpoilerPreferences @Inject constructor(@param:ApplicationContext private val context: Context) :
    SpoilerPreferences {
    override fun observeHideSpoilers(): Flow<Boolean> = context.bingeePreferenceData
        .map { it[KEY_HIDE_SPOILERS] ?: false }

    override suspend fun setHideSpoilers(hide: Boolean) {
        context.bingeePreferences.edit { it[KEY_HIDE_SPOILERS] = hide }
    }

    private companion object {
        val KEY_HIDE_SPOILERS = booleanPreferencesKey("hide_episode_spoilers")
    }
}
