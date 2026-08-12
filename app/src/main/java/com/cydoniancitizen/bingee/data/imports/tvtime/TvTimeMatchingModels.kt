@file:Suppress("ktlint:standard:max-line-length")

package com.cydoniancitizen.bingee.data.imports.tvtime

import com.cydoniancitizen.bingee.core.model.Episode
import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.MediaDetails
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.data.imports.model.ImportedEpisodeHint
import com.cydoniancitizen.bingee.data.imports.model.ImportedMediaHint
import com.cydoniancitizen.bingee.data.tmdb.series.TmdbSeasonPayload
import java.time.LocalDate

internal enum class TvTimeMatchConfidence {
    EXACT,
    HIGH_CONFIDENCE,
    AMBIGUOUS,
    UNMATCHED,
    INVALID,
    SKIPPED
}

internal enum class TvTimeMatchReason {
    EXACT_EXTERNAL_ID,
    EXACT_EPISODE_ID,
    EXACT_NUMBERING,
    TITLE_AND_YEAR_UNIQUE,
    SERIES_ID_REQUIRED,
    CONFLICTING_EXTERNAL_IDS,
    MULTIPLE_CANDIDATES,
    NO_CANDIDATE,
    MEDIA_TYPE_MISMATCH,
    MISSING_PARENT,
    MISSING_SEASON,
    MISSING_EPISODE,
    SPECIAL_REQUIRES_REVIEW,
    PROVIDER_ERROR,
    INVALID_SOURCE
}

internal enum class TvTimeReviewAction {
    UNDECIDED,
    ACCEPT_PROPOSED,
    SELECT_CANDIDATE,
    SKIP
}

internal data class TmdbImportCandidate(
    val externalRef: ExternalMediaRef,
    val mediaType: MediaType,
    val title: String,
    val originalTitle: String?,
    val year: Int?,
    val posterUrl: String?,
    val overview: String?
)

internal data class TmdbImportEpisodeCandidate(
    val externalRef: ExternalMediaRef,
    val seriesRef: ExternalMediaRef,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val title: String,
    val airDate: LocalDate?
) {
    companion object {
        fun from(episode: Episode): TmdbImportEpisodeCandidate = TmdbImportEpisodeCandidate(
            externalRef = episode.externalRef,
            seriesRef = episode.seriesRef,
            seasonNumber = episode.seasonNumber,
            episodeNumber = episode.episodeNumber,
            title = episode.title,
            airDate = episode.airDate
        )
    }
}

internal data class TvTimeMediaReview(
    val source: ImportedMediaHint,
    val confidence: TvTimeMatchConfidence,
    val reason: TvTimeMatchReason,
    val proposed: TmdbImportCandidate?,
    val alternatives: List<TmdbImportCandidate>,
    val action: TvTimeReviewAction = when (confidence) {
        TvTimeMatchConfidence.EXACT,
        TvTimeMatchConfidence.HIGH_CONFIDENCE -> TvTimeReviewAction.ACCEPT_PROPOSED
        else -> TvTimeReviewAction.UNDECIDED
    },
    val selectedCandidate: TmdbImportCandidate? = null
) {
    fun effectiveCandidate(): TmdbImportCandidate? = when (action) {
        TvTimeReviewAction.ACCEPT_PROPOSED -> proposed
        TvTimeReviewAction.SELECT_CANDIDATE -> selectedCandidate
        else -> null
    }
}

internal data class TvTimeEpisodeReview(
    val source: ImportedEpisodeHint,
    val confidence: TvTimeMatchConfidence,
    val reason: TvTimeMatchReason,
    val proposed: TmdbImportEpisodeCandidate?,
    val alternatives: List<TmdbImportEpisodeCandidate>,
    val action: TvTimeReviewAction = when (confidence) {
        TvTimeMatchConfidence.EXACT,
        TvTimeMatchConfidence.HIGH_CONFIDENCE -> TvTimeReviewAction.ACCEPT_PROPOSED
        else -> TvTimeReviewAction.UNDECIDED
    },
    val selectedCandidate: TmdbImportEpisodeCandidate? = null
) {
    fun effectiveCandidate(): TmdbImportEpisodeCandidate? = when (action) {
        TvTimeReviewAction.ACCEPT_PROPOSED -> proposed
        TvTimeReviewAction.SELECT_CANDIDATE -> selectedCandidate
        else -> null
    }
}

internal data class TvTimeMatchReport(
    val media: List<TvTimeMediaReview>,
    val episodes: List<TvTimeEpisodeReview>,
    val recoverableError: com.cydoniancitizen.bingee.core.result.AppError? = null
) {
    val exactCount: Int get() = media.count { it.confidence == TvTimeMatchConfidence.EXACT } +
        episodes.count { it.confidence == TvTimeMatchConfidence.EXACT }
    val highConfidenceCount: Int get() = media.count { it.confidence == TvTimeMatchConfidence.HIGH_CONFIDENCE } +
        episodes.count { it.confidence == TvTimeMatchConfidence.HIGH_CONFIDENCE }
    val needsReviewCount: Int get() = media.count { it.confidence == TvTimeMatchConfidence.AMBIGUOUS } +
        episodes.count { it.confidence == TvTimeMatchConfidence.AMBIGUOUS }
    val unmatchedCount: Int get() = media.count { it.confidence == TvTimeMatchConfidence.UNMATCHED } +
        episodes.count { it.confidence == TvTimeMatchConfidence.UNMATCHED }
    val skippedCount: Int get() = media.count { it.action == TvTimeReviewAction.SKIP } +
        episodes.count { it.action == TvTimeReviewAction.SKIP }
}

internal interface TvTimeTmdbGateway {
    suspend fun findMedia(
        identity: String,
        namespace: String,
        mediaType: MediaType
    ): com.cydoniancitizen.bingee.core.result.AppResult<List<TmdbImportCandidate>>

    suspend fun findEpisodes(
        identity: String,
        namespace: String
    ): com.cydoniancitizen.bingee.core.result.AppResult<List<TmdbImportEpisodeCandidate>>

    suspend fun searchMedia(
        mediaType: MediaType,
        title: String,
        year: Int?
    ): com.cydoniancitizen.bingee.core.result.AppResult<List<TmdbImportCandidate>>

    suspend fun loadDetails(
        candidate: TmdbImportCandidate
    ): com.cydoniancitizen.bingee.core.result.AppResult<com.cydoniancitizen.bingee.data.tmdb.details.TmdbMediaDetailsPayload>

    suspend fun loadSeason(
        seriesTmdbId: Long,
        seasonNumber: Int
    ): com.cydoniancitizen.bingee.core.result.AppResult<TmdbSeasonPayload>
}
