package com.cydoniancitizen.bingee.data.tmdb.auth

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header

internal interface TmdbAuthenticationService {
    @GET("3/authentication")
    suspend fun validate(@Header("Authorization") authorization: String): Response<TmdbAuthenticationResponse>
}

internal data class TmdbAuthenticationResponse(val success: Boolean?)
