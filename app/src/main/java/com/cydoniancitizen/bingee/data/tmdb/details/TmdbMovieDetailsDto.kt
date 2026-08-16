package com.cydoniancitizen.bingee.data.tmdb.details

import com.google.gson.annotations.SerializedName

internal data class TmdbMovieDetailsDto(
    val id: Long?,
    val title: String?,
    @SerializedName("original_title") val originalTitle: String?,
    val overview: String?,
    @SerializedName("poster_path") val posterPath: String?,
    @SerializedName("backdrop_path") val backdropPath: String?,
    @SerializedName("release_date") val releaseDate: String?,
    val genres: List<TmdbGenreDto>?,
    val status: String?,
    val runtime: Int?,
    @SerializedName("original_language") val originalLanguage: String?,
    @SerializedName("imdb_id") val imdbId: String? = null
)

internal data class TmdbGenreDto(val id: Long?, val name: String?)
