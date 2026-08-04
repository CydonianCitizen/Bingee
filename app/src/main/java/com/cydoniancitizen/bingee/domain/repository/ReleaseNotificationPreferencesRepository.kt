package com.cydoniancitizen.bingee.domain.repository

import com.cydoniancitizen.bingee.core.model.ReleaseNotificationLeadTime
import com.cydoniancitizen.bingee.core.model.ReleaseNotificationPreferences
import kotlinx.coroutines.flow.Flow

interface ReleaseNotificationPreferencesRepository {
    val preferences: Flow<ReleaseNotificationPreferences>

    suspend fun setEnabled(enabled: Boolean)

    suspend fun setLeadTime(leadTime: ReleaseNotificationLeadTime)

    suspend fun setMovieReleases(enabled: Boolean)

    suspend fun setSeasonPremieres(enabled: Boolean)

    suspend fun setEpisodeAirings(enabled: Boolean)
}
