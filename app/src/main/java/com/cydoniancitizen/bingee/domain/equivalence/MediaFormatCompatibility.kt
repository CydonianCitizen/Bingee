package com.cydoniancitizen.bingee.domain.equivalence

import com.cydoniancitizen.bingee.core.model.AnimeFormat
import com.cydoniancitizen.bingee.core.model.MediaType

enum class FormatCompatibilityResult {
    COMPATIBLE,
    INCOMPATIBLE,
    GRANULARITY_RISK,
    SPECIAL_OR_OVA_RISK
}

object MediaFormatCompatibility {
    fun evaluate(
        tmdbType: MediaType,
        jikanFormat: AnimeFormat,
        tmdbSeasonCount: Int? = null
    ): FormatCompatibilityResult = when (tmdbType) {
        MediaType.MOVIE -> when (jikanFormat) {
            AnimeFormat.MOVIE -> FormatCompatibilityResult.COMPATIBLE
            AnimeFormat.OVA, AnimeFormat.SPECIAL, AnimeFormat.TV_SPECIAL, AnimeFormat.ONA ->
                FormatCompatibilityResult.SPECIAL_OR_OVA_RISK
            else -> FormatCompatibilityResult.INCOMPATIBLE
        }
        MediaType.SERIES -> when (jikanFormat) {
            AnimeFormat.TV -> {
                if (tmdbSeasonCount != null && tmdbSeasonCount > 1) {
                    FormatCompatibilityResult.GRANULARITY_RISK
                } else {
                    FormatCompatibilityResult.COMPATIBLE
                }
            }
            AnimeFormat.OVA, AnimeFormat.SPECIAL, AnimeFormat.TV_SPECIAL, AnimeFormat.ONA ->
                FormatCompatibilityResult.SPECIAL_OR_OVA_RISK
            else -> FormatCompatibilityResult.INCOMPATIBLE
        }
        MediaType.ANIME -> FormatCompatibilityResult.INCOMPATIBLE
    }
}
