package com.cydoniancitizen.bingee.data.tmdb.search

import com.google.gson.annotations.SerializedName

internal data class TmdbFindResponseDto(
    @SerializedName("movie_results") val movieResults: List<TmdbFindMovieDto>?,
    @SerializedName("tv_results") val tvResults: List<TmdbFindTvDto>?,
    @SerializedName("tv_episode_results") val episodeResults: List<TmdbFindEpisodeDto>?
)

internal data class TmdbFindMovieDto(
    val id: Long?,
    val title: String?,
    @SerializedName("original_title") val originalTitle: String?,
    @SerializedName("release_date") val releaseDate: String?,
    @SerializedName("poster_path") val posterPath: String?,
    val overview: String?
)

internal data class TmdbFindTvDto(
    val id: Long?,
    val name: String?,
    @SerializedName("original_name") val originalName: String?,
    @SerializedName("first_air_date") val firstAirDate: String?,
    @SerializedName("poster_path") val posterPath: String?,
    val overview: String?
)

internal data class TmdbFindEpisodeDto(
    val id: Long?,
    val name: String?,
    @SerializedName("show_id") val showId: Long?,
    @SerializedName("season_number") val seasonNumber: Int?,
    @SerializedName("episode_number") val episodeNumber: Int?,
    @SerializedName("air_date") val airDate: String?,
    val overview: String?
)
