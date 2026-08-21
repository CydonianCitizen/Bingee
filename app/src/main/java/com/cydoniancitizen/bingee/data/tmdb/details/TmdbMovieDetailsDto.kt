package com.cydoniancitizen.bingee.data.tmdb.details

import com.google.gson.annotations.SerializedName

internal data class TmdbMovieDetailsDto(
    @SerializedName("id") val id: Long?,
    @SerializedName("title") val title: String?,
    @SerializedName("original_title") val originalTitle: String?,
    @SerializedName("overview") val overview: String?,
    @SerializedName("poster_path") val posterPath: String?,
    @SerializedName("backdrop_path") val backdropPath: String?,
    @SerializedName("release_date") val releaseDate: String?,
    @SerializedName("genres") val genres: List<TmdbGenreDto>?,
    @SerializedName("status") val status: String?,
    @SerializedName("runtime") val runtime: Int?,
    @SerializedName("original_language") val originalLanguage: String?,
    @SerializedName("imdb_id") val imdbId: String? = null
)

internal data class TmdbGenreDto(@SerializedName("id") val id: Long?, @SerializedName("name") val name: String?)
