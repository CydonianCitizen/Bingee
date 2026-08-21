package com.cydoniancitizen.bingee.data.tmdb.search

import com.google.gson.annotations.SerializedName

internal data class TmdbMovieSearchResponseDto(
    @SerializedName("page") val page: Int?,
    @SerializedName("results") val results: List<TmdbMovieSearchResultDto>?,
    @SerializedName("total_pages") val totalPages: Int?,
    @SerializedName("total_results") val totalResults: Int?
)

internal data class TmdbMovieSearchResultDto(
    @SerializedName("id") val id: Long?,
    @SerializedName("title") val title: String?,
    @SerializedName("original_title") val originalTitle: String?,
    @SerializedName("poster_path") val posterPath: String?,
    @SerializedName("release_date") val releaseDate: String?,
    @SerializedName("overview") val overview: String?
)
