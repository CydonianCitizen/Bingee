package com.cydoniancitizen.bingee.data.jikan.search

import com.google.gson.annotations.SerializedName

internal data class JikanAnimeSearchResponseDto(
    val data: List<JikanAnimeSearchResultDto>?,
    val pagination: JikanPaginationDto?
)

internal data class JikanPaginationDto(
    @SerializedName("last_visible_page") val lastVisiblePage: Int?,
    @SerializedName("has_next_page") val hasNextPage: Boolean?
)

internal data class JikanAnimeSearchResultDto(
    @SerializedName("mal_id") val malId: Int?,
    val title: String?,
    @SerializedName("title_english") val titleEnglish: String?,
    @SerializedName("title_japanese") val titleJapanese: String?,
    val synopsis: String?,
    val images: JikanImagesDto?,
    val aired: JikanAiredDto?
)

internal data class JikanImagesDto(val jpg: JikanJpgImageDto?)

internal data class JikanJpgImageDto(
    @SerializedName("large_image_url") val largeImageUrl: String?,
    @SerializedName("image_url") val imageUrl: String?
)

internal data class JikanAiredDto(val from: String?, val to: String? = null)
