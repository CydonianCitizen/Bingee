package com.cydoniancitizen.bingee.data.tmdb.details

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

internal interface TmdbDetailsService {
    @GET("3/movie/{movie_id}")
    suspend fun movieDetails(
        @Header("Authorization") authorization: String,
        @Path("movie_id") movieId: Long,
        @Query("language") language: String
    ): Response<TmdbMovieDetailsDto>

    @GET("3/tv/{series_id}")
    suspend fun tvDetails(
        @Header("Authorization") authorization: String,
        @Path("series_id") seriesId: Long,
        @Query("language") language: String
    ): Response<TmdbTvDetailsDto>
}
