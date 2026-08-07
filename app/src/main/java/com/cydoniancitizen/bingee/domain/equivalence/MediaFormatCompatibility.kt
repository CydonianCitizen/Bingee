package com.cydoniancitizen.bingee.domain.equivalence

import com.cydoniancitizen.bingee.core.model.AnimeFormat
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.domain.model.toMediaType

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
    ): FormatCompatibilityResult {
        val classified = jikanFormat.toMediaType()
        return when (tmdbType) {
            MediaType.MOVIE -> when {
                classified == MediaType.MOVIE -> FormatCompatibilityResult.COMPATIBLE
                classified == MediaType.SERIES && jikanFormat != AnimeFormat.TV ->
                    FormatCompatibilityResult.SPECIAL_OR_OVA_RISK
                else -> FormatCompatibilityResult.INCOMPATIBLE
            }
            MediaType.SERIES -> when {
                jikanFormat == AnimeFormat.TV -> {
                    if (tmdbSeasonCount != null && tmdbSeasonCount > 1) {
                        FormatCompatibilityResult.GRANULARITY_RISK
                    } else {
                        FormatCompatibilityResult.COMPATIBLE
                    }
                }
                classified == MediaType.SERIES -> FormatCompatibilityResult.SPECIAL_OR_OVA_RISK
                else -> FormatCompatibilityResult.INCOMPATIBLE
            }
            MediaType.ANIME -> FormatCompatibilityResult.INCOMPATIBLE
        }
    }
}
