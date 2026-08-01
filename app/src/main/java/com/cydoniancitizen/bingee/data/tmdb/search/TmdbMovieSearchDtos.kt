package com.cydoniancitizen.bingee.data.tmdb.search

import com.google.gson.annotations.SerializedName

internal data class TmdbMovieSearchResponseDto(
    val page: Int?,
    val results: List<TmdbMovieSearchResultDto>?,
    @SerializedName("total_pages") val totalPages: Int?,
    @SerializedName("total_results") val totalResults: Int?
)

internal data class TmdbMovieSearchResultDto(
    val id: Long?,
    val title: String?,
    @SerializedName("original_title") val originalTitle: String?,
    @SerializedName("poster_path") val posterPath: String?,
    @SerializedName("release_date") val releaseDate: String?,
    val overview: String?
)
