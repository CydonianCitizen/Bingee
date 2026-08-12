package com.cydoniancitizen.bingee.core.navigation

import com.cydoniancitizen.bingee.core.model.MediaType

data class DetailRouteArgs(val mediaType: MediaType, val tmdbId: Long)

object DetailRoute {
    const val MEDIA_TYPE_ARG = "mediaType"
    const val TMDB_ID_ARG = "tmdbId"
    const val TEMPLATE = "details/{$MEDIA_TYPE_ARG}/{$TMDB_ID_ARG}"

    fun create(mediaType: MediaType, tmdbId: Long): String {
        require(tmdbId > 0) { "TMDB ID must be positive" }
        return "details/${mediaType.name}/$tmdbId"
    }

    fun parse(mediaType: String?, tmdbId: String?): DetailRouteArgs? = runCatching {
        val parsedType = MediaType.entries.firstOrNull { it.name == mediaType } ?: return null
        val parsedId = tmdbId?.toLongOrNull()?.takeIf { it > 0 } ?: return null
        DetailRouteArgs(parsedType, parsedId)
    }.getOrNull()
}
