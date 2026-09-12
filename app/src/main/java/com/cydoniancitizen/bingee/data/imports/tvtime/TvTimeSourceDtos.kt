package com.cydoniancitizen.bingee.data.imports.tvtime

import com.google.gson.annotations.SerializedName

internal data class TvTimeListDto(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("description") val description: String,
    @SerializedName("is_public") val isPublic: Boolean,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("items") val items: List<TvTimeListItemDto>
)

internal data class TvTimeListItemDto(
    @SerializedName("custom_order") val customOrder: Int,
    @SerializedName("name") val name: String,
    @SerializedName("type") val type: String,
    @SerializedName("tvdb_id") val tvdbId: Long?,
    @SerializedName("uuid") val uuid: String?
)

internal data class TvTimeSourceIdsDto(
    @SerializedName("imdb") val imdb: String?,
    @SerializedName("tvdb") val tvdb: Long?
)
