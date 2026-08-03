package com.cydoniancitizen.bingee.data.tmdb.series

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

internal interface TmdbSeasonService {
    @GET("3/tv/{series_id}/season/{season_number}")
    suspend fun seasonDetails(
        @Header("Authorization") authorization: String,
        @Path("series_id") seriesId: Long,
        @Path("season_number") seasonNumber: Int,
        @Query("language") language: String
    ): Response<TmdbSeasonDetailsDto>
}
