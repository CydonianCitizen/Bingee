package com.cydoniancitizen.bingee.data.jikan.search

import com.cydoniancitizen.bingee.data.jikan.details.JikanAnimeFullResponseDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

internal interface JikanSearchService {
    @GET("anime")
    suspend fun searchAnime(
        @Query("q") query: String,
        @Query("page") page: Int,
        @Query("sfw") safeForWork: Boolean = true
    ): Response<JikanAnimeSearchResponseDto>

    @GET("anime/{id}/full")
    suspend fun getAnimeFull(@Path("id") id: Int): Response<JikanAnimeFullResponseDto>
}
