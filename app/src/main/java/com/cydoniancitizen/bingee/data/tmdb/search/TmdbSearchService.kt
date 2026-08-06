package com.cydoniancitizen.bingee.data.tmdb.search

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

internal interface TmdbSearchService {
    @GET("3/search/movie")
    suspend fun searchMovies(
        @Header("Authorization") authorization: String,
        @Query("query") query: String,
        @Query("include_adult") includeAdult: Boolean,
        @Query("language") language: String,
        @Query("page") page: Int,
        @Query("primary_release_year") primaryReleaseYear: Int? = null
    ): Response<TmdbMovieSearchResponseDto>

    @GET("3/search/tv")
    suspend fun searchTvSeries(
        @Header("Authorization") authorization: String,
        @Query("query") query: String,
        @Query("include_adult") includeAdult: Boolean,
        @Query("language") language: String,
        @Query("page") page: Int,
        @Query("first_air_date_year") firstAirDateYear: Int? = null
    ): Response<TmdbTvSearchResponseDto>

    @GET("3/find/{external_id}")
    suspend fun findByExternalId(
        @Header("Authorization") authorization: String,
        @Path("external_id") externalId: String,
        @Query("external_source") externalSource: String,
        @Query("language") language: String
    ): Response<TmdbFindResponseDto>

    @GET("3/discover/movie")
    suspend fun discoverMovies(
        @Header("Authorization") authorization: String,
        @Query("region") region: String = "IT",
        @Query("include_adult") includeAdult: Boolean = false,
        @Query("sort_by") sortBy: String = "popularity.desc",
        @Query("vote_count.gte") voteCountGte: Int = 10,
        @Query("language") language: String = "it-IT",
        @Query("page") page: Int = 1
    ): Response<TmdbMovieSearchResponseDto>

    @GET("3/discover/tv")
    suspend fun discoverTvSeries(
        @Header("Authorization") authorization: String,
        @Query("include_adult") includeAdult: Boolean = false,
        @Query("sort_by") sortBy: String = "popularity.desc",
        @Query("vote_count.gte") voteCountGte: Int = 5,
        @Query("language") language: String = "it-IT",
        @Query("page") page: Int = 1
    ): Response<TmdbTvSearchResponseDto>
}
