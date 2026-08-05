package com.cydoniancitizen.bingee.data.imports.tvtime

import com.cydoniancitizen.bingee.core.model.Episode
import com.cydoniancitizen.bingee.core.model.MediaDetails
import com.cydoniancitizen.bingee.core.model.Season
import com.cydoniancitizen.bingee.core.result.AppError
import com.cydoniancitizen.bingee.core.result.AppResult
import com.cydoniancitizen.bingee.data.imports.model.ImportWarning
import com.cydoniancitizen.bingee.data.imports.model.ImportedEpisodeHint
import com.cydoniancitizen.bingee.data.imports.model.ImportedMediaHint
import com.cydoniancitizen.bingee.data.imports.model.ImportedSourceIdentity
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

internal data class TvTimeMediaImportSource(
    val title: String,
    val createdAt: Instant?,
    val watchedAt: Instant?,
    val identities: List<ImportedSourceIdentity>
)

internal data class TvTimeEpisodeImportSource(
    val title: String,
    val watchedAt: Instant,
    val identities: List<ImportedSourceIdentity>
)

internal data class TvTimeMediaImportChange(
    val source: TvTimeMediaImportSource,
    val candidate: TmdbImportCandidate,
    val details: MediaDetails,
    val seasons: List<Season>
)

internal data class TvTimeEpisodeImportChange(
    val source: TvTimeEpisodeImportSource,
    val episode: Episode,
    val season: Season
)

internal data class TvTimeImportPlan(
    val profileId: String,
    val confirmedAt: Instant,
    val media: List<TvTimeMediaImportChange>,
    val episodes: List<TvTimeEpisodeImportChange>,
    val skippedRecordIds: List<String>,
    val unmatchedRecordIds: List<String> = emptyList(),
    val invalidRecordCount: Int,
    val unsupported: com.cydoniancitizen.bingee.data.imports.model.ImportedUnsupportedFields,
    val warnings: List<ImportWarning> = emptyList()
)

internal enum class TvTimePlanFailureReason { REVIEW_REQUIRED, PROVIDER_FAILURE, INVALID_CANONICAL_DATA }

internal data class TvTimePlanFailure(val reason: TvTimePlanFailureReason, val error: AppError? = null)

internal sealed interface TvTimeImportPlanResult {
    data class Success(val plan: TvTimeImportPlan) : TvTimeImportPlanResult
    data class Failure(val failure: TvTimePlanFailure) : TvTimeImportPlanResult
}

@Singleton
internal class TvTimeImportPlanBuilder @Inject constructor(private val gateway: TvTimeTmdbGateway) {
    suspend fun build(
        document: com.cydoniancitizen.bingee.data.imports.model.ImportedSourceDocument,
        report: TvTimeMatchReport,
        confirmedAt: Instant
    ): TvTimeImportPlanResult {
        if (report.media.any { it.action != TvTimeReviewAction.SKIP && it.effectiveCandidate() == null }) {
            return TvTimeImportPlanResult.Failure(TvTimePlanFailure(TvTimePlanFailureReason.REVIEW_REQUIRED))
        }
        if (report.episodes.any { it.action != TvTimeReviewAction.SKIP && it.effectiveCandidate() == null }) {
            return TvTimeImportPlanResult.Failure(TvTimePlanFailure(TvTimePlanFailureReason.REVIEW_REQUIRED))
        }

        val mediaChanges = mutableListOf<TvTimeMediaImportChange>()
        val detailsByRef = mutableMapOf<String, TvTimeMediaImportChange>()
        report.media.filter { it.action != TvTimeReviewAction.SKIP }.forEach { review ->
            val candidate = review.effectiveCandidate() ?: return@forEach
            val key = "${candidate.mediaType}:${candidate.externalRef.source}:${candidate.externalRef.externalId}"
            if (detailsByRef.containsKey(key)) {
                return TvTimeImportPlanResult.Failure(TvTimePlanFailure(TvTimePlanFailureReason.INVALID_CANONICAL_DATA))
            }
            when (val loaded = gateway.loadDetails(candidate)) {
                is AppResult.Failure -> return TvTimeImportPlanResult.Failure(
                    TvTimePlanFailure(TvTimePlanFailureReason.PROVIDER_FAILURE, loaded.error)
                )
                is AppResult.Success -> {
                    val payload = loaded.value
                    if (payload.details.externalRef != candidate.externalRef ||
                        payload.details.mediaType != candidate.mediaType
                    ) {
                        return TvTimeImportPlanResult.Failure(
                            TvTimePlanFailure(TvTimePlanFailureReason.INVALID_CANONICAL_DATA)
                        )
                    }
                    val change = TvTimeMediaImportChange(
                        source = review.source.toImportSource(),
                        candidate = candidate,
                        details = payload.details,
                        seasons = payload.seasons
                    )
                    detailsByRef[key] = change
                    mediaChanges += change
                }
            }
        }

        val seasonPayloads = mutableMapOf<String, com.cydoniancitizen.bingee.data.tmdb.series.TmdbSeasonPayload>()
        val episodeChanges = mutableListOf<TvTimeEpisodeImportChange>()
        report.episodes.filter { it.action != TvTimeReviewAction.SKIP }.forEach { review ->
            val candidate = review.effectiveCandidate() ?: return@forEach
            val parent = report.media.firstOrNull { it.source.recordId == review.source.parentRecordId }
                ?.effectiveCandidate()
                ?: return TvTimeImportPlanResult.Failure(
                    TvTimePlanFailure(TvTimePlanFailureReason.REVIEW_REQUIRED)
                )
            val key = "${parent.externalRef.externalId}:${candidate.seasonNumber}"
            val payload = seasonPayloads[key] ?: when (
                val loaded = gateway.loadSeason(parent.externalRef, candidate.seasonNumber)
            ) {
                is AppResult.Failure -> return TvTimeImportPlanResult.Failure(
                    TvTimePlanFailure(TvTimePlanFailureReason.PROVIDER_FAILURE, loaded.error)
                )
                is AppResult.Success -> loaded.value.also { seasonPayloads[key] = it }
            }
            val episode = payload.episodes.singleOrNull { it.externalRef == candidate.externalRef }
                ?: return TvTimeImportPlanResult.Failure(
                    TvTimePlanFailure(TvTimePlanFailureReason.INVALID_CANONICAL_DATA)
                )
            if (episodeChanges.any { it.episode.externalRef == episode.externalRef }) {
                return TvTimeImportPlanResult.Failure(TvTimePlanFailure(TvTimePlanFailureReason.INVALID_CANONICAL_DATA))
            }
            episodeChanges += TvTimeEpisodeImportChange(
                source = review.source.toImportSource(),
                episode = episode,
                season = payload.season
            )
        }

        val skipped = (
            report.media.mapNotNull {
                it.source.recordId.takeIf { _ -> it.action == TvTimeReviewAction.SKIP }
            } +
                report.episodes.mapNotNull { it.source.recordId.takeIf { _ -> it.action == TvTimeReviewAction.SKIP } }
            )
            .distinct()
        return TvTimeImportPlanResult.Success(
            TvTimeImportPlan(
                profileId = document.profileId,
                confirmedAt = confirmedAt,
                media = mediaChanges,
                episodes = episodeChanges,
                skippedRecordIds = skipped,
                unmatchedRecordIds = (
                    report.media.filter {
                        it.confidence == TvTimeMatchConfidence.UNMATCHED &&
                            it.action == TvTimeReviewAction.SKIP
                    }
                        .map { it.source.recordId } +
                        report.episodes.filter {
                            it.confidence == TvTimeMatchConfidence.UNMATCHED &&
                                it.action == TvTimeReviewAction.SKIP
                        }
                            .map { it.source.recordId }
                    ).distinct(),
                invalidRecordCount = document.summary.invalidRecordCount,
                unsupported = document.summary.unsupported,
                warnings = document.warnings
            )
        )
    }
}

private fun ImportedMediaHint.toImportSource(): TvTimeMediaImportSource = TvTimeMediaImportSource(
    title = title,
    createdAt = createdAt?.instant,
    watchedAt = watch?.watchedAt?.instant,
    identities = identities
)

private fun ImportedEpisodeHint.toImportSource(): TvTimeEpisodeImportSource = TvTimeEpisodeImportSource(
    title = title,
    watchedAt = checkNotNull(watch.watchedAt?.instant),
    identities = identities
)
