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

internal data class TvTimeMovieDto(
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("id") val ids: TvTimeSourceIdsDto,
    @SerializedName("is_favorite") val isFavorite: Boolean,
    @SerializedName("is_watched") val isWatched: Boolean,
    @SerializedName("rewatch_count") val rewatchCount: Int,
    @SerializedName("title") val title: String,
    @SerializedName("uuid") val uuid: String,
    @SerializedName("watched_at") val watchedAt: String?,
    @SerializedName("year") val year: Int
)

internal data class TvTimeSeriesDto(
    @SerializedName("_noEpisodeData") val noEpisodeData: Boolean,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("id") val ids: TvTimeSourceIdsDto,
    @SerializedName("is_favorite") val isFavorite: Boolean,
    @SerializedName("seasons") val seasons: List<TvTimeSeasonDto>,
    @SerializedName("status") val status: String,
    @SerializedName("title") val title: String,
    @SerializedName("uuid") val uuid: String
)

internal data class TvTimeSeasonDto(
    @SerializedName("episodes") val episodes: List<TvTimeEpisodeDto>,
    @SerializedName("is_specials") val isSpecials: Boolean,
    @SerializedName("number") val number: Int
)

internal data class TvTimeEpisodeDto(
    @SerializedName("id") val ids: TvTimeSourceIdsDto,
    @SerializedName("is_watched") val isWatched: Boolean,
    @SerializedName("name") val name: String,
    @SerializedName("number") val number: Int,
    @SerializedName("rewatch_count") val rewatchCount: Int,
    @SerializedName("special") val special: Boolean,
    @SerializedName("watched_at") val watchedAt: String?,
    @SerializedName("watched_count") val watchedCount: Int
)
