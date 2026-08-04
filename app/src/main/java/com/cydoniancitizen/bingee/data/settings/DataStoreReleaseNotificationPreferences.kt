package com.cydoniancitizen.bingee.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.cydoniancitizen.bingee.core.model.ReleaseNotificationLeadTime
import com.cydoniancitizen.bingee.core.model.ReleaseNotificationPreferences
import com.cydoniancitizen.bingee.domain.repository.ReleaseNotificationPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

@Singleton
internal class DataStoreReleaseNotificationPreferences @Inject constructor(
    @param:ApplicationContext private val context: Context
) : ReleaseNotificationPreferencesRepository {
    override val preferences: Flow<ReleaseNotificationPreferences> = context.bingeePreferences.data
        .catch { failure ->
            if (failure is IOException) emit(emptyPreferences()) else throw failure
        }
        .map { values ->
            ReleaseNotificationPreferences(
                enabled = values[ENABLED] ?: false,
                leadTime = values[LEAD_TIME]
                    ?.let { stored -> ReleaseNotificationLeadTime.entries.firstOrNull { it.name == stored } }
                    ?: ReleaseNotificationLeadTime.ONE_DAY,
                movieReleases = values[MOVIES] ?: true,
                seasonPremieres = values[SEASONS] ?: true,
                episodeAirings = values[EPISODES] ?: true
            )
        }

    override suspend fun setEnabled(enabled: Boolean) = update(ENABLED, enabled)

    override suspend fun setLeadTime(leadTime: ReleaseNotificationLeadTime) {
        context.bingeePreferences.edit { it[LEAD_TIME] = leadTime.name }
    }

    override suspend fun setMovieReleases(enabled: Boolean) = update(MOVIES, enabled)

    override suspend fun setSeasonPremieres(enabled: Boolean) = update(SEASONS, enabled)

    override suspend fun setEpisodeAirings(enabled: Boolean) = update(EPISODES, enabled)

    private suspend fun update(key: androidx.datastore.preferences.core.Preferences.Key<Boolean>, value: Boolean) {
        context.bingeePreferences.edit { it[key] = value }
    }

    private companion object {
        val ENABLED = booleanPreferencesKey("release_notifications_enabled")
        val LEAD_TIME = stringPreferencesKey("release_notifications_lead_time")
        val MOVIES = booleanPreferencesKey("release_notifications_movies")
        val SEASONS = booleanPreferencesKey("release_notifications_seasons")
        val EPISODES = booleanPreferencesKey("release_notifications_episodes")
    }
}
