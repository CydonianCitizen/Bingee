package com.cydoniancitizen.bingee.domain.model

import com.cydoniancitizen.bingee.core.model.AnimeFormat
import com.cydoniancitizen.bingee.core.model.MediaType

import java.util.Locale

/**
 * Canonical classifier mapping Jikan [AnimeFormat] to Bingee content type ([MediaType]).
 *
 * Mapping:
 * - MOVIE -> MediaType.MOVIE
 * - TV, ONA, OVA, SPECIAL, TV_SPECIAL -> MediaType.SERIES
 * - MUSIC, PV, CM, UNKNOWN -> null (unsupported)
 */
object AnimeFormatClassifier {
    fun parseFormat(rawType: String?): AnimeFormat = when (rawType?.trim()?.lowercase(Locale.ROOT)) {
        "tv" -> AnimeFormat.TV
        "movie" -> AnimeFormat.MOVIE
        "ova" -> AnimeFormat.OVA
        "ona" -> AnimeFormat.ONA
        "special" -> AnimeFormat.SPECIAL
        "music" -> AnimeFormat.MUSIC
        "cm" -> AnimeFormat.CM
        "pv" -> AnimeFormat.PV
        "tv special" -> AnimeFormat.TV_SPECIAL
        else -> AnimeFormat.UNKNOWN
    }

    fun toMediaType(format: AnimeFormat): MediaType? = when (format) {
        AnimeFormat.MOVIE -> MediaType.MOVIE
        AnimeFormat.TV,
        AnimeFormat.ONA,
        AnimeFormat.OVA,
        AnimeFormat.SPECIAL,
        AnimeFormat.TV_SPECIAL -> MediaType.SERIES
        AnimeFormat.MUSIC,
        AnimeFormat.PV,
        AnimeFormat.CM,
        AnimeFormat.UNKNOWN -> null
    }
}

fun AnimeFormat.toMediaType(): MediaType? = AnimeFormatClassifier.toMediaType(this)
