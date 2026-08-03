package com.cydoniancitizen.bingee.data.tmdb.series

import com.google.gson.annotations.SerializedName

internal data class TmdbSeasonDetailsDto(
    val id: Long?,
    @SerializedName("season_number") val seasonNumber: Int?,
    val name: String?,
    val overview: String?,
    @SerializedName("poster_path") val posterPath: String?,
    @SerializedName("air_date") val airDate: String?,
    val episodes: List<TmdbEpisodeDto>?
)

internal data class TmdbEpisodeDto(
    val id: Long?,
    @SerializedName("season_number") val seasonNumber: Int?,
    @SerializedName("episode_number") val episodeNumber: Int?,
    val name: String?,
    val overview: String?,
    @SerializedName("air_date") val airDate: String?,
    val runtime: Int?,
    @SerializedName("still_path") val stillPath: String?
)
