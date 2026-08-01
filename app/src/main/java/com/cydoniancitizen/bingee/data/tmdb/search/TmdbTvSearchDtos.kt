package com.cydoniancitizen.bingee.data.tmdb.search

import com.google.gson.annotations.SerializedName

internal data class TmdbTvSearchResponseDto(
    val page: Int?,
    val results: List<TmdbTvSearchResultDto>?,
    @SerializedName("total_pages") val totalPages: Int?,
    @SerializedName("total_results") val totalResults: Int?
)

internal data class TmdbTvSearchResultDto(
    val id: Long?,
    val name: String?,
    @SerializedName("original_name") val originalName: String?,
    @SerializedName("poster_path") val posterPath: String?,
    @SerializedName("first_air_date") val firstAirDate: String?,
    val overview: String?
)
