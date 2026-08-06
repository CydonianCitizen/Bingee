package com.cydoniancitizen.bingee.data.equivalence

import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.LinkedMediaIdentity
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.result.AppError
import com.cydoniancitizen.bingee.core.result.AppResult
import com.cydoniancitizen.bingee.data.library.local.ExternalRefEntity
import com.cydoniancitizen.bingee.data.library.local.FullCandidateData
import com.cydoniancitizen.bingee.data.library.local.MediaEquivalenceCandidateDao
import com.cydoniancitizen.bingee.domain.equivalence.CandidateMediaProjection
import com.cydoniancitizen.bingee.domain.equivalence.MediaCandidatePairKey
import com.cydoniancitizen.bingee.domain.equivalence.MediaEquivalenceCandidate
import com.cydoniancitizen.bingee.domain.equivalence.MediaEquivalenceClassification
import com.cydoniancitizen.bingee.domain.equivalence.MediaEquivalenceEvaluation
import com.cydoniancitizen.bingee.domain.equivalence.MediaEquivalenceEvaluator
import com.cydoniancitizen.bingee.domain.equivalence.MediaEquivalenceNormalizer
import com.cydoniancitizen.bingee.domain.repository.MediaEquivalenceCandidateRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

@Singleton
internal class RoomMediaEquivalenceCandidateRepository @Inject constructor(
    private val dao: MediaEquivalenceCandidateDao
) : MediaEquivalenceCandidateRepository {

    override fun observeLibraryCandidates(): Flow<List<MediaEquivalenceCandidate>> = combine(
        dao.observeAllCandidatesData(),
        dao.observeActiveLinkMemberMediaIds()
    ) { allData, linkedIdsList ->
        val linkedIds = linkedIdsList.toSet()
        val projections = allData.map { it.toCandidateProjection(linkedIds) }
        val libraryProjections = projections.filter { it.isLibraryMember && !it.isAlreadyLinked }

        val plausiblePairs = generatePlausiblePairs(libraryProjections)
        val candidates = plausiblePairs.mapNotNull { (p1, p2) ->
            val eval = MediaEquivalenceEvaluator.evaluate(p1, p2)
            if (eval.classification == MediaEquivalenceClassification.EXACT_IDENTITY ||
                eval.classification == MediaEquivalenceClassification.STRONG_POSSIBLE_SAME_WORK
            ) {
                MediaEquivalenceCandidate(eval)
            } else {
                null
            }
        }

        sortCandidates(candidates)
    }

    override fun observeCandidatesForMedia(identity: LinkedMediaIdentity): Flow<List<MediaEquivalenceCandidate>> {
        return combine(
            dao.observeAllCandidatesData(),
            dao.observeActiveLinkMemberMediaIds()
        ) { allData, linkedIdsList ->
            val linkedIds = linkedIdsList.toSet()
            val projections = allData.map { it.toCandidateProjection(linkedIds) }
            val target = projections.find { it.identity == identity } ?: return@combine emptyList()
            if (target.isAlreadyLinked) return@combine emptyList()

            val oppositeProjections = projections.filter { p ->
                p.identity.source != target.identity.source &&
                    !p.isAlreadyLinked &&
                    (p.isLibraryMember || (target.imdbId != null && target.imdbId == p.imdbId))
            }

            val candidates = oppositeProjections.mapNotNull { opposite ->
                val eval = MediaEquivalenceEvaluator.evaluate(target, opposite)
                if (eval.classification == MediaEquivalenceClassification.EXACT_IDENTITY ||
                    eval.classification == MediaEquivalenceClassification.STRONG_POSSIBLE_SAME_WORK
                ) {
                    MediaEquivalenceCandidate(eval)
                } else {
                    null
                }
            }

            sortCandidates(candidates)
        }
    }

    override suspend fun evaluatePair(
        first: LinkedMediaIdentity,
        second: LinkedMediaIdentity
    ): AppResult<MediaEquivalenceEvaluation> {
        if (first == second) return AppResult.Failure(AppError.InvalidInput)
        if (first.source == second.source) return AppResult.Failure(AppError.InvalidInput)

        val allData = dao.getAllCandidatesData()
        val linkedIds = dao.getActiveLinkMemberMediaIds().toSet()

        val projections = allData.map { it.toCandidateProjection(linkedIds) }
        val p1 = projections.find { it.identity == first } ?: return AppResult.Failure(AppError.LinkError.MediaNotFound)
        val p2 =
            projections.find { it.identity == second } ?: return AppResult.Failure(AppError.LinkError.MediaNotFound)

        val eval = MediaEquivalenceEvaluator.evaluate(p1, p2)
        return AppResult.Success(eval)
    }

    private fun generatePlausiblePairs(
        projections: List<CandidateMediaProjection>
    ): List<Pair<CandidateMediaProjection, CandidateMediaProjection>> {
        val tmdbItems = projections.filter { it.identity.source == MediaSource.TMDB }
        val jikanItems = projections.filter { it.identity.source == MediaSource.JIKAN }

        if (tmdbItems.isEmpty() || jikanItems.isEmpty()) return emptyList()

        val pairs = mutableMapOf<MediaCandidatePairKey, Pair<CandidateMediaProjection, CandidateMediaProjection>>()

        // Index 1: By IMDb ID
        val jikanByImdb = mutableMapOf<String, MutableList<CandidateMediaProjection>>()
        for (j in jikanItems) {
            val imdb = j.imdbId?.lowercase()
            if (!imdb.isNullOrBlank()) {
                jikanByImdb.getOrPut(imdb) { mutableListOf() }.add(j)
            }
        }
        for (t in tmdbItems) {
            val imdb = t.imdbId?.lowercase()
            if (!imdb.isNullOrBlank()) {
                jikanByImdb[imdb]?.forEach { j ->
                    val pairKey = MediaCandidatePairKey.of(t.identity, j.identity)
                    pairs[pairKey] = t to j
                }
            }
        }

        // Index 2: By normalized title
        val jikanByTitle = mutableMapOf<String, MutableList<CandidateMediaProjection>>()
        for (j in jikanItems) {
            val titles = setOfNotNull(
                MediaEquivalenceNormalizer.normalizeTitle(j.title),
                MediaEquivalenceNormalizer.normalizeTitle(j.englishTitle),
                MediaEquivalenceNormalizer.normalizeTitle(j.japaneseTitle)
            ).filter { it.isNotBlank() }
            for (title in titles) {
                jikanByTitle.getOrPut(title) { mutableListOf() }.add(j)
            }
        }
        for (t in tmdbItems) {
            val titles = setOfNotNull(
                MediaEquivalenceNormalizer.normalizeTitle(t.title),
                MediaEquivalenceNormalizer.normalizeTitle(t.originalTitle)
            ).filter { it.isNotBlank() }
            for (title in titles) {
                jikanByTitle[title]?.forEach { j ->
                    val pairKey = MediaCandidatePairKey.of(t.identity, j.identity)
                    pairs[pairKey] = t to j
                }
            }
        }

        return pairs.values.toList()
    }

    private fun sortCandidates(candidates: List<MediaEquivalenceCandidate>): List<MediaEquivalenceCandidate> =
        candidates.sortedWith(
            compareBy<MediaEquivalenceCandidate> { candidate ->
                when (candidate.evaluation.classification) {
                    MediaEquivalenceClassification.EXACT_IDENTITY -> 0
                    MediaEquivalenceClassification.STRONG_POSSIBLE_SAME_WORK -> 1
                    else -> 2
                }
            }.thenBy { candidate ->
                candidate.pairKey.keyString
            }
        )

    private fun FullCandidateData.toCandidateProjection(linkedMemberIds: Set<Long>): CandidateMediaProjection {
        val primaryRef = externalRefs.find { it.source == MediaSource.TMDB || it.source == MediaSource.JIKAN }
            ?: ExternalRefEntity(raw.localMediaId, MediaSource.TMDB, raw.localMediaId.toString())

        val imdbRef = externalRefs.find { it.source == MediaSource.IMDB }?.externalId

        val identity = LinkedMediaIdentity(
            source = primaryRef.source,
            mediaType = raw.mediaType,
            externalId = primaryRef.externalId
        )

        val year = raw.animeYear ?: raw.releaseDate?.year ?: raw.animeStartDate?.year

        return CandidateMediaProjection(
            identity = identity,
            title = raw.title,
            originalTitle = raw.originalTitle,
            englishTitle = raw.englishTitle,
            japaneseTitle = raw.japaneseTitle,
            releaseYear = year,
            releaseDate = raw.releaseDate ?: raw.animeStartDate,
            animeFormat = raw.animeFormat,
            tmdbSeasonCount = raw.numberOfSeasons,
            imdbId = imdbRef,
            relationTypes = animeRelations.map { it.relationType }.toSet(),
            isAlreadyLinked = linkedMemberIds.contains(raw.localMediaId),
            isLibraryMember = raw.isLibraryMember
        )
    }
}
