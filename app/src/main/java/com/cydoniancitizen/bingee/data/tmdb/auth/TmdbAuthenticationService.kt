package com.cydoniancitizen.bingee.data.tmdb.auth

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header

internal interface TmdbAuthenticationService {
    @GET("3/authentication")
    suspend fun validate(@Header("Authorization") authorization: String): Response<TmdbAuthenticationResponse>
}

// Every Gson-mapped field is annotated on purpose: R8 renames unannotated fields in release builds,
// Gson then finds no match, and a valid credential silently reads as "could not be verified".
internal data class TmdbAuthenticationResponse(@SerializedName("success") val success: Boolean?)
