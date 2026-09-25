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
    @ColumnInfo(name = "legacy_bridge_completed") val legacyBridgeCompleted: Boolean = false,
    @ColumnInfo(name = "theme", defaultValue = "'SYSTEM_DEFAULT'")
    val theme: String = "SYSTEM_DEFAULT",
    @ColumnInfo(name = "language", defaultValue = "'ENGLISH'")
    val language: String = "ENGLISH",
    @ColumnInfo(name = "hide_episode_spoilers", defaultValue = "0")
    val hideEpisodeSpoilers: Boolean = false,
    @ColumnInfo(name = "watched_movies_display_mode", defaultValue = "'LIST'")
    val watchedMoviesDisplayMode: String = "LIST",
    @ColumnInfo(name = "watched_tv_series_display_mode", defaultValue = "'LIST'")
    val watchedTvSeriesDisplayMode: String = "LIST",
    @ColumnInfo(name = "watch_later_movies_display_mode", defaultValue = "'LIST'")
    val watchLaterMoviesDisplayMode: String = "LIST",
    @ColumnInfo(name = "watch_later_tv_series_display_mode", defaultValue = "'LIST'")
    val watchLaterTvSeriesDisplayMode: String = "LIST",
    @ColumnInfo(name = "favorites_movies_display_mode", defaultValue = "'LIST'")
    val favoritesMoviesDisplayMode: String = "LIST",
    @ColumnInfo(name = "favorites_tv_series_display_mode", defaultValue = "'LIST'")
    val favoritesTvSeriesDisplayMode: String = "LIST",
    @ColumnInfo(name = "legacy_settings_bridge_completed", defaultValue = "0")
    val legacySettingsBridgeCompleted: Boolean = false
) {
    init {
        require(singletonKey == SINGLETON_KEY)
        require(notificationLeadDays in setOf(0, 1, 3, 7))
    }

    companion object {
        const val SINGLETON_KEY = 1
    }
}
