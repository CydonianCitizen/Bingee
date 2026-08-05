@file:Suppress("ktlint:standard:max-line-length")

package com.cydoniancitizen.bingee.data.imports.tvtime

import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.core.result.AppError
import com.cydoniancitizen.bingee.core.result.AppResult
import com.cydoniancitizen.bingee.data.imports.model.ImportedEpisodeHint
import com.cydoniancitizen.bingee.data.imports.model.ImportedIdentityNamespace
import com.cydoniancitizen.bingee.data.imports.model.ImportedMediaHint
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Conservative, deterministic matcher. Work is sequential by design: one bounded lane is safest for TMDB. */
@Singleton
internal class TvTimeMatcher @Inject constructor(private val gateway: TvTimeTmdbGateway) {
    private val mediaCache = mutableMapOf<String, AppResult<List<TmdbImportCandidate>>>()
    private val seasonCache =
        mutableMapOf<String, AppResult<com.cydoniancitizen.bingee.data.tmdb.series.TmdbSeasonPayload>>()
    private val episodeCache = mutableMapOf<String, AppResult<List<TmdbImportEpisodeCandidate>>>()

    fun clearSession() {
        mediaCache.clear()
        seasonCache.clear()
        episodeCache.clear()
    }

    suspend fun match(
        document: com.cydoniancitizen.bingee.data.imports.model.ImportedSourceDocument
    ): TvTimeMatchReport {
        val coroutineContext = currentCoroutineContext()
        val providerErrors = mutableListOf<AppError>()
        val mediaReviews = buildList {
            document.movies.forEach {
                coroutineContext.ensureActive()
                add(matchMedia(it, mediaCache) { providerErrors += it })
            }
            document.series.forEach {
                coroutineContext.ensureActive()
                add(matchMedia(it, mediaCache) { providerErrors += it })
            }
        }
        val episodeReviews = matchEpisodes(document, mediaReviews, providerErrors)
        return TvTimeMatchReport(
            media = mediaReviews,
            episodes = episodeReviews,
            recoverableError = providerErrors.firstOrNull()
        )
    }

    suspend fun rematchEpisodes(
        document: com.cydoniancitizen.bingee.data.imports.model.ImportedSourceDocument,
        mediaReviews: List<TvTimeMediaReview>
    ): List<TvTimeEpisodeReview> = matchEpisodes(document, mediaReviews, mutableListOf())

    private suspend fun matchEpisodes(
        document: com.cydoniancitizen.bingee.data.imports.model.ImportedSourceDocument,
        mediaReviews: List<TvTimeMediaReview>,
        providerErrors: MutableList<AppError>
    ): List<TvTimeEpisodeReview> {
        val coroutineContext = currentCoroutineContext()
        val parentById = mediaReviews.associateBy { it.source.recordId }
        return buildList {
            document.episodes.filter { it.watch.watched }.forEach { episode ->
                coroutineContext.ensureActive()
                add(matchEpisode(episode, parentById, episodeCache, seasonCache) { providerErrors += it })
            }
        }
    }

    private suspend fun matchMedia(
        source: ImportedMediaHint,
        cache: MutableMap<String, AppResult<List<TmdbImportCandidate>>>,
        onProviderError: (AppError) -> Unit
    ): TvTimeMediaReview {
        val exact = exactMediaCandidates(source, cache)
        exact.error?.let(onProviderError)
        if (exact.error != null) {
            return TvTimeMediaReview(
                source = source,
                confidence = if (exact.candidates.isEmpty()) {
                    TvTimeMatchConfidence.UNMATCHED
                } else {
                    TvTimeMatchConfidence.AMBIGUOUS
                },
                reason = TvTimeMatchReason.PROVIDER_ERROR,
                proposed = null,
                alternatives = exact.candidates.distinctBy { it.externalRef }.sortedWith(candidateOrder())
            )
        }
        if (exact.mediaTypeMismatch) {
            return TvTimeMediaReview(
                source = source,
                confidence = TvTimeMatchConfidence.AMBIGUOUS,
                reason = TvTimeMatchReason.MEDIA_TYPE_MISMATCH,
                proposed = null,
                alternatives = exact.candidates.distinctBy { it.externalRef }.sortedWith(candidateOrder())
            )
        }
        val distinctExact = exact.candidates.distinctBy { it.externalRef }
        if (distinctExact.size == 1) {
            return TvTimeMediaReview(
                source = source,
                confidence = TvTimeMatchConfidence.EXACT,
                reason = TvTimeMatchReason.EXACT_EXTERNAL_ID,
                proposed = distinctExact.single(),
                alternatives = emptyList()
            )
        }
        if (distinctExact.size > 1) {
            return TvTimeMediaReview(
                source = source,
                confidence = TvTimeMatchConfidence.AMBIGUOUS,
                reason = TvTimeMatchReason.CONFLICTING_EXTERNAL_IDS,
                proposed = null,
                alternatives = distinctExact.sortedWith(candidateOrder())
            )
        }

        val search = cachedSuccess(
            cache,
            "search:${source.mediaType}:${source.normalizedTitle}:${source.year ?: "unknown"}"
        ) {
            gateway.searchMedia(source.mediaType, source.title, source.year)
        }
        if (search is AppResult.Failure) {
            onProviderError(search.error)
            return TvTimeMediaReview(
                source = source,
                confidence = TvTimeMatchConfidence.UNMATCHED,
                reason = TvTimeMatchReason.PROVIDER_ERROR,
                proposed = null,
                alternatives = emptyList()
            )
        }
        val searchValues = (search as AppResult.Success).value
        val candidates = searchValues
            .filter { it.mediaType == source.mediaType }
            .distinctBy { it.externalRef }
            .sortedWith(candidateOrder())
        if (candidates.isEmpty() && searchValues.isNotEmpty()) {
            return TvTimeMediaReview(
                source = source,
                confidence = TvTimeMatchConfidence.AMBIGUOUS,
                reason = TvTimeMatchReason.MEDIA_TYPE_MISMATCH,
                proposed = null,
                alternatives = emptyList()
            )
        }
        if (source.mediaType == MediaType.SERIES) {
            return TvTimeMediaReview(
                source = source,
                confidence = if (candidates.isEmpty()) TvTimeMatchConfidence.UNMATCHED else TvTimeMatchConfidence.AMBIGUOUS,
                reason = if (candidates.isEmpty()) TvTimeMatchReason.NO_CANDIDATE else TvTimeMatchReason.SERIES_ID_REQUIRED,
                proposed = null,
                alternatives = candidates
            )
        }
        val exactTitleAndYear = candidates.filter { candidate ->
            normalizeImportedTitle(candidate.title) == source.normalizedTitle && candidate.year == source.year
        }
        return when (exactTitleAndYear.size) {
            1 -> TvTimeMediaReview(
                source = source,
                confidence = TvTimeMatchConfidence.HIGH_CONFIDENCE,
                reason = TvTimeMatchReason.TITLE_AND_YEAR_UNIQUE,
                proposed = exactTitleAndYear.single(),
                alternatives = candidates
            )
            0 -> TvTimeMediaReview(
                source = source,
                confidence = if (candidates.isEmpty()) TvTimeMatchConfidence.UNMATCHED else TvTimeMatchConfidence.AMBIGUOUS,
                reason = if (candidates.isEmpty()) TvTimeMatchReason.NO_CANDIDATE else TvTimeMatchReason.MULTIPLE_CANDIDATES,
                proposed = null,
                alternatives = candidates
            )
            else -> TvTimeMediaReview(
                source = source,
                confidence = TvTimeMatchConfidence.AMBIGUOUS,
                reason = TvTimeMatchReason.MULTIPLE_CANDIDATES,
                proposed = null,
                alternatives = exactTitleAndYear
            )
        }
    }

    private suspend fun matchEpisode(
        source: ImportedEpisodeHint,
        parents: Map<String, TvTimeMediaReview>,
        episodeCache: MutableMap<String, AppResult<List<TmdbImportEpisodeCandidate>>>,
        seasonCache: MutableMap<String, AppResult<com.cydoniancitizen.bingee.data.tmdb.series.TmdbSeasonPayload>>,
        onProviderError: (AppError) -> Unit
    ): TvTimeEpisodeReview {
        val parent = parents[source.parentRecordId]
        val series = parent?.effectiveCandidate()
        if (series == null || series.mediaType != MediaType.SERIES) {
            return TvTimeEpisodeReview(
                source,
                TvTimeMatchConfidence.UNMATCHED,
                TvTimeMatchReason.MISSING_PARENT,
                null,
                emptyList()
            )
        }

        if (source.special || source.specialsSeason) {
            val exact = exactEpisodeCandidates(source, series, episodeCache, onProviderError)
            if (exact.size == 1) {
                return TvTimeEpisodeReview(
                    source,
                    TvTimeMatchConfidence.EXACT,
                    TvTimeMatchReason.EXACT_EPISODE_ID,
                    exact.single(),
                    emptyList()
                )
            }
            if (exact.size > 1) {
                return TvTimeEpisodeReview(
                    source,
                    TvTimeMatchConfidence.AMBIGUOUS,
                    TvTimeMatchReason.CONFLICTING_EXTERNAL_IDS,
                    null,
                    exact
                )
            }
        }

        val seasonKey = "${series.externalRef.externalId}:${source.seasonNumber}"
        val seasonResult = cachedSuccess(seasonCache, seasonKey) {
            gateway.loadSeason(series.externalRef, source.seasonNumber)
        }
        if (seasonResult is AppResult.Failure) {
            if (seasonResult.error != AppError.MissingData) onProviderError(seasonResult.error)
            return TvTimeEpisodeReview(
                source,
                if (seasonResult.error ==
                    AppError.MissingData
                ) {
                    TvTimeMatchConfidence.UNMATCHED
                } else {
                    TvTimeMatchConfidence.UNMATCHED
                },
                if (seasonResult.error ==
                    AppError.MissingData
                ) {
                    TvTimeMatchReason.MISSING_SEASON
                } else {
                    TvTimeMatchReason.PROVIDER_ERROR
                },
                null,
                emptyList()
            )
        }
        val payload = (seasonResult as AppResult.Success).value
        val candidates = payload.episodes
            .filter { it.episodeNumber == source.episodeNumber }
            .map(TmdbImportEpisodeCandidate::from)
            .distinctBy { it.externalRef }
        return when {
            candidates.size == 1 && !source.special && !source.specialsSeason -> TvTimeEpisodeReview(
                source,
                TvTimeMatchConfidence.EXACT,
                TvTimeMatchReason.EXACT_NUMBERING,
                candidates.single(),
                emptyList()
            )
            candidates.size == 1 -> TvTimeEpisodeReview(
                source,
                TvTimeMatchConfidence.AMBIGUOUS,
                TvTimeMatchReason.SPECIAL_REQUIRES_REVIEW,
                candidates.single(),
                candidates
            )
            candidates.size > 1 -> TvTimeEpisodeReview(
                source,
                TvTimeMatchConfidence.AMBIGUOUS,
                TvTimeMatchReason.MULTIPLE_CANDIDATES,
                null,
                candidates
            )
            else -> TvTimeEpisodeReview(
                source,
                TvTimeMatchConfidence.UNMATCHED,
                TvTimeMatchReason.MISSING_EPISODE,
                null,
                emptyList()
            )
        }
    }

    private suspend fun exactEpisodeCandidates(
        source: ImportedEpisodeHint,
        series: TmdbImportCandidate,
        cache: MutableMap<String, AppResult<List<TmdbImportEpisodeCandidate>>>,
        onProviderError: (AppError) -> Unit
    ): List<TmdbImportEpisodeCandidate> {
        val result = mutableListOf<TmdbImportEpisodeCandidate>()
        source.identities
            .filter { it.namespace == ImportedIdentityNamespace.IMDB || it.namespace == ImportedIdentityNamespace.TVDB }
            .forEach { identity ->
                val key = "episode:${identity.namespace}:${identity.value}"
                val response = cachedSuccess(cache, key) {
                    gateway.findEpisodes(identity.value, identity.namespace.name)
                }
                if (response is AppResult.Success) {
                    result += response.value.filter {
                        it.seriesRef == series.externalRef &&
                            it.seasonNumber == source.seasonNumber &&
                            it.episodeNumber == source.episodeNumber
                    }
                } else if (response is AppResult.Failure && response.error != AppError.MissingData) {
                    onProviderError(response.error)
                }
            }
        return result.distinctBy { it.externalRef }
    }

    private suspend fun exactMediaCandidates(
        source: ImportedMediaHint,
        cache: MutableMap<String, AppResult<List<TmdbImportCandidate>>>
    ): ExactMediaResult {
        val candidates = mutableListOf<TmdbImportCandidate>()
        var error: AppError? = null
        var mediaTypeMismatch = false
        source.identities
            .filter { identity ->
                identity.namespace == ImportedIdentityNamespace.IMDB ||
                    source.mediaType == MediaType.SERIES && identity.namespace == ImportedIdentityNamespace.TVDB
            }
            .forEach { identity ->
                val key = "media:${identity.namespace}:${identity.value}:${source.mediaType}"
                when (
                    val result = cachedSuccess(cache, key) {
                        gateway.findMedia(identity.value, identity.namespace.name, source.mediaType)
                    }
                ) {
                    is AppResult.Success -> {
                        mediaTypeMismatch = mediaTypeMismatch || result.value.any { it.mediaType != source.mediaType }
                        candidates += result.value.filter { it.mediaType == source.mediaType }
                    }
                    is AppResult.Failure -> if (result.error != AppError.MissingData) error = result.error
                }
            }
        return ExactMediaResult(candidates, error, mediaTypeMismatch)
    }

    private fun candidateOrder(): Comparator<TmdbImportCandidate> = compareBy<TmdbImportCandidate> {
        it.title.lowercase()
    }
        .thenBy { it.year ?: Int.MAX_VALUE }
        .thenBy { it.externalRef.externalId }

    private suspend fun <T> cachedSuccess(
        cache: MutableMap<String, AppResult<T>>,
        key: String,
        load: suspend () -> AppResult<T>
    ): AppResult<T> {
        cache[key]?.let { return it }
        return load().also { if (it is AppResult.Success) cache[key] = it }
    }

    private data class ExactMediaResult(
        val candidates: List<TmdbImportCandidate>,
        val error: AppError?,
        val mediaTypeMismatch: Boolean
    )
}
