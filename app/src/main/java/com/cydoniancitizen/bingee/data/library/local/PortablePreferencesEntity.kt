package com.cydoniancitizen.bingee.data.library.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "portable_preferences")
internal data class PortablePreferencesEntity(
    @PrimaryKey
    @ColumnInfo(name = "singleton_key") val singletonKey: Int = SINGLETON_KEY,
    @ColumnInfo(name = "notification_lead_days") val notificationLeadDays: Int = 1,
    @ColumnInfo(name = "notify_movie_releases") val notifyMovieReleases: Boolean = true,
    @ColumnInfo(name = "notify_season_premieres") val notifySeasonPremieres: Boolean = true,
    @ColumnInfo(name = "notify_episode_airings") val notifyEpisodeAirings: Boolean = true,
    @ColumnInfo(name = "legacy_bridge_completed") val legacyBridgeCompleted: Boolean = false
) {
    init {
        require(singletonKey == SINGLETON_KEY)
        require(notificationLeadDays in setOf(0, 1, 3, 7))
    }

    companion object {
        const val SINGLETON_KEY = 1
    }
}
