package com.cydoniancitizen.bingee.data.tmdb.details

import com.google.gson.annotations.SerializedName

internal data class TmdbTvDetailsDto(
    @SerializedName("id") val id: Long?,
    @SerializedName("name") val name: String?,
    @SerializedName("original_name") val originalName: String?,
    @SerializedName("overview") val overview: String?,
    @SerializedName("poster_path") val posterPath: String?,
    @SerializedName("backdrop_path") val backdropPath: String?,
    @SerializedName("first_air_date") val firstAirDate: String?,
    @SerializedName("genres") val genres: List<TmdbGenreDto>?,
    @SerializedName("status") val status: String?,
    @SerializedName("episode_run_time") val episodeRunTime: List<Int>?,
    @SerializedName("number_of_seasons") val numberOfSeasons: Int?,
    @SerializedName("number_of_episodes") val numberOfEpisodes: Int?,
    @SerializedName("original_language") val originalLanguage: String?,
    @SerializedName("seasons") val seasons: List<TmdbSeasonSummaryDto>? = null
)

internal data class TmdbSeasonSummaryDto(
    @SerializedName("id") val id: Long?,
    @SerializedName("season_number") val seasonNumber: Int?,
    @SerializedName("name") val name: String?,
    @SerializedName("overview") val overview: String?,
    @SerializedName("poster_path") val posterPath: String?,
    @SerializedName("air_date") val airDate: String?,
    @SerializedName("episode_count") val episodeCount: Int?
)
