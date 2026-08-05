package com.cydoniancitizen.bingee.core.navigation

import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class DetailRouteArgs(val reference: ExternalMediaRef, val mediaType: MediaType)

object DetailRoute {
    const val SOURCE_ARG = "source"
    const val MEDIA_TYPE_ARG = "mediaType"
    const val EXTERNAL_ID_ARG = "externalId"
    const val TEMPLATE = "details/{$SOURCE_ARG}/{$MEDIA_TYPE_ARG}/{$EXTERNAL_ID_ARG}"

    fun create(reference: ExternalMediaRef, mediaType: MediaType): String {
        val externalId = reference.externalId.trim()
        require(externalId.isNotEmpty()) { "External media ID must not be blank" }
        require(isProviderMediaTypeValid(reference.source, mediaType)) {
            "Provider and media type do not match"
        }
        require(externalId.toLongOrNull()?.let { it > 0 } == true) {
            "Provider media ID must be positive"
        }
        return "details/${reference.source.name}/${mediaType.name}/${externalId.encoded()}"
    }

    fun parse(source: String?, mediaType: String?, externalId: String?): DetailRouteArgs? = runCatching {
        val parsedSource = MediaSource.entries.firstOrNull { it.name == source } ?: return null
        val parsedType = MediaType.entries.firstOrNull { it.name == mediaType } ?: return null
        val parsedId = externalId?.decoded()?.trim()?.takeIf(String::isNotEmpty) ?: return null
        if (!isProviderMediaTypeValid(parsedSource, parsedType) ||
            parsedId.toLongOrNull()?.let { it > 0 } != true
        ) {
            return null
        }
        DetailRouteArgs(ExternalMediaRef(parsedSource, parsedId), parsedType)
    }.getOrNull()

    private fun isProviderMediaTypeValid(source: MediaSource, mediaType: MediaType): Boolean = when (source) {
        MediaSource.TMDB -> mediaType == MediaType.MOVIE || mediaType == MediaType.SERIES
        MediaSource.JIKAN -> mediaType == MediaType.ANIME
    }
}

private fun String.encoded(): String = URLEncoder.encode(this, StandardCharsets.UTF_8.name())

private fun String.decoded(): String = URLDecoder.decode(this, StandardCharsets.UTF_8.name())
