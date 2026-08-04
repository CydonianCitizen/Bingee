package com.cydoniancitizen.bingee.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.room.withTransaction
import com.cydoniancitizen.bingee.core.model.ReleaseNotificationLeadTime
import com.cydoniancitizen.bingee.core.model.ReleaseNotificationPreferences
import com.cydoniancitizen.bingee.data.library.local.BingeeDatabase
import com.cydoniancitizen.bingee.data.library.local.PortablePreferencesEntity
import com.cydoniancitizen.bingee.data.library.local.PortableSnapshotDao
import com.cydoniancitizen.bingee.domain.repository.ReleaseNotificationPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

@Singleton
internal class DataStoreReleaseNotificationPreferences @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val database: BingeeDatabase,
    private val portableDao: PortableSnapshotDao
) : ReleaseNotificationPreferencesRepository {
    override val preferences: Flow<ReleaseNotificationPreferences> = flow {
        ensureLegacyBridge()
        emitAll(
            combine(
                portableDao.observePreferences().filterNotNull(),
                dataStoreValues()
            ) { portable, device ->
                ReleaseNotificationPreferences(
                    enabled = device.enabled,
                    leadTime = ReleaseNotificationLeadTime.entries.first { it.days == portable.notificationLeadDays },
                    movieReleases = portable.notifyMovieReleases,
                    seasonPremieres = portable.notifySeasonPremieres,
                    episodeAirings = portable.notifyEpisodeAirings
                )
            }
        )
    }

    override suspend fun setEnabled(enabled: Boolean) {
        context.bingeePreferences.edit { values -> values[ENABLED] = enabled }
    }

    override suspend fun setLeadTime(leadTime: ReleaseNotificationLeadTime) {
        updatePortable { it.copy(notificationLeadDays = leadTime.days) }
    }

    override suspend fun setMovieReleases(enabled: Boolean) = update(MOVIES, enabled)

    override suspend fun setSeasonPremieres(enabled: Boolean) = update(SEASONS, enabled)

    override suspend fun setEpisodeAirings(enabled: Boolean) = update(EPISODES, enabled)

    private suspend fun updatePortable(update: (PortablePreferencesEntity) -> PortablePreferencesEntity) {
        ensureLegacyBridge()
        database.withTransaction {
            val current = checkNotNull(portableDao.getPreferences())
            portableDao.replacePreferences(update(current))
        }
    }

    private suspend fun ensureLegacyBridge() {
        val legacy = dataStoreValues().first()
        database.withTransaction {
            val current = portableDao.getPreferences()
            if (current == null) {
                portableDao.replacePreferences(legacy.toPortablePreferences())
            } else if (!current.legacyBridgeCompleted) {
                portableDao.replacePreferences(
                    current.copy(
                        notificationLeadDays = legacy.leadDays,
                        notifyMovieReleases = legacy.movieReleases,
                        notifySeasonPremieres = legacy.seasonPremieres,
                        notifyEpisodeAirings = legacy.episodeAirings,
                        legacyBridgeCompleted = true
                    )
                )
            }
        }
    }

    private fun dataStoreValues(): Flow<LegacyValues> = context.bingeePreferences.data
        .catch { failure ->
            if (failure is IOException) emit(emptyPreferences()) else throw failure
        }
        .map { values ->
            LegacyValues(
                enabled = values[ENABLED] ?: false,
                leadDays = values[LEAD_TIME]
                    ?.let { stored -> ReleaseNotificationLeadTime.entries.firstOrNull { it.name == stored } }
                    ?.days ?: ReleaseNotificationLeadTime.ONE_DAY.days,
                movieReleases = values[MOVIES] ?: true,
                seasonPremieres = values[SEASONS] ?: true,
                episodeAirings = values[EPISODES] ?: true
            )
        }

    private suspend fun update(key: androidx.datastore.preferences.core.Preferences.Key<Boolean>, value: Boolean) {
        when (key) {
            MOVIES -> updatePortable { it.copy(notifyMovieReleases = value) }
            SEASONS -> updatePortable { it.copy(notifySeasonPremieres = value) }
            EPISODES -> updatePortable { it.copy(notifyEpisodeAirings = value) }
            else -> error("Unsupported portable notification key")
        }
    }

    private data class LegacyValues(
        val enabled: Boolean,
        val leadDays: Int,
        val movieReleases: Boolean,
        val seasonPremieres: Boolean,
        val episodeAirings: Boolean
    )

    private fun LegacyValues.toPortablePreferences() = PortablePreferencesEntity(
        notificationLeadDays = leadDays,
        notifyMovieReleases = movieReleases,
        notifySeasonPremieres = seasonPremieres,
        notifyEpisodeAirings = episodeAirings,
        legacyBridgeCompleted = true
    )

    private companion object {
        val ENABLED = booleanPreferencesKey("release_notifications_enabled")
        val LEAD_TIME = stringPreferencesKey("release_notifications_lead_time")
        val MOVIES = booleanPreferencesKey("release_notifications_movies")
        val SEASONS = booleanPreferencesKey("release_notifications_seasons")
        val EPISODES = booleanPreferencesKey("release_notifications_episodes")
    }
}
