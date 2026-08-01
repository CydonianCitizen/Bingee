package com.cydoniancitizen.bingee.data.tmdb.search

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

internal interface TmdbSearchService {
    @GET("3/search/movie")
    suspend fun searchMovies(
        @Header("Authorization") authorization: String,
        @Query("query") query: String,
        @Query("include_adult") includeAdult: Boolean,
        @Query("language") language: String,
        @Query("page") page: Int
    ): Response<TmdbMovieSearchResponseDto>

    @GET("3/search/tv")
    suspend fun searchTvSeries(
        @Header("Authorization") authorization: String,
        @Query("query") query: String,
        @Query("include_adult") includeAdult: Boolean,
        @Query("language") language: String,
        @Query("page") page: Int
    ): Response<TmdbTvSearchResponseDto>
}
