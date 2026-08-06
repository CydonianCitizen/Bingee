package com.cydoniancitizen.bingee.data.jikan.details

import com.cydoniancitizen.bingee.data.jikan.search.JikanAiredDto
import com.cydoniancitizen.bingee.data.jikan.search.JikanImagesDto
import com.google.gson.annotations.SerializedName

internal data class JikanAnimeFullResponseDto(val data: JikanAnimeFullDto?)

internal data class JikanAnimeFullDto(
    @SerializedName("mal_id") val malId: Int?,
    val title: String?,
    @SerializedName("title_english") val titleEnglish: String?,
    @SerializedName("title_japanese") val titleJapanese: String?,
    val synopsis: String?,
    val images: JikanImagesDto?,
    val type: String?,
    val status: String?,
    val episodes: Int?,
    val duration: String?,
    val aired: JikanAiredDto?,
    val season: String?,
    val year: Int?,
    val score: Double?,
    val relations: List<JikanRelationDto>?,
    val external: List<JikanExternalLinkDto>? = null
)

internal data class JikanExternalLinkDto(val name: String?, val url: String?)

internal data class JikanRelationDto(val relation: String?, val entry: List<JikanRelationEntryDto>?)

internal data class JikanRelationEntryDto(
    @SerializedName("mal_id") val malId: Int?,
    val type: String?,
    val name: String?
)
