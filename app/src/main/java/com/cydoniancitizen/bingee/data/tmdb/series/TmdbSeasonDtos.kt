package com.cydoniancitizen.bingee.data.tmdb.series

import com.google.gson.annotations.SerializedName

internal data class TmdbSeasonDetailsDto(
    @SerializedName("id") val id: Long?,
    @SerializedName("season_number") val seasonNumber: Int?,
    @SerializedName("name") val name: String?,
    @SerializedName("overview") val overview: String?,
    @SerializedName("poster_path") val posterPath: String?,
    @SerializedName("air_date") val airDate: String?,
    @SerializedName("episodes") val episodes: List<TmdbEpisodeDto>?
)

internal data class TmdbEpisodeDto(
    @SerializedName("id") val id: Long?,
    @SerializedName("season_number") val seasonNumber: Int?,
    @SerializedName("episode_number") val episodeNumber: Int?,
    @SerializedName("name") val name: String?,
    @SerializedName("overview") val overview: String?,
    @SerializedName("air_date") val airDate: String?,
    @SerializedName("runtime") val runtime: Int?,
    @SerializedName("still_path") val stillPath: String?
)
