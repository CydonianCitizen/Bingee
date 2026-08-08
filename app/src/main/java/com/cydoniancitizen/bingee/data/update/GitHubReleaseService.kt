package com.cydoniancitizen.bingee.data.update

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET

internal data class GitHubReleaseDto(
    @SerializedName("tag_name") val tagName: String? = null,
    @SerializedName("html_url") val htmlUrl: String? = null,
    @SerializedName("draft") val draft: Boolean = false,
    @SerializedName("prerelease") val prerelease: Boolean = false
)

internal interface GitHubReleaseService {
    @GET("repos/CydonianCitizen/Bingee/releases/latest")
    suspend fun getLatestRelease(): GitHubReleaseDto
}
