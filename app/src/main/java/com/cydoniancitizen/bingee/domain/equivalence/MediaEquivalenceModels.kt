package com.cydoniancitizen.bingee.domain.equivalence

import com.cydoniancitizen.bingee.core.model.LinkedMediaIdentity

enum class MediaEquivalenceClassification {
    EXACT_IDENTITY,
    STRONG_POSSIBLE_SAME_WORK,
    AMBIGUOUS,
    RELATED_DISTINCT,
    NOT_EQUIVALENT,
    INVALID_CANDIDATE
}

enum class MediaEquivalenceSignal {
    SHARED_IMDB_ID,
    EXACT_NORMALIZED_TITLE,
    EXACT_ORIGINAL_TITLE,
    EXACT_ENGLISH_TITLE,
    EXACT_JAPANESE_TITLE,
    EXACT_RELEASE_YEAR,
    EXACT_RELEASE_DATE,
    COMPATIBLE_FORMAT,
    COMPATIBLE_MEDIA_TYPE,
    USER_SELECTED_PAIR
}

enum class MediaEquivalenceNegativeSignal {
    CONFLICTING_IMDB_IDS,
    RELEASE_YEAR_MISMATCH,
    RELEASE_DATE_MISMATCH,
    INCOMPATIBLE_FORMAT,
    INCOMPATIBLE_MEDIA_TYPE,
    GRANULARITY_MISMATCH,
    SEQUEL_RELATION,
    PREQUEL_RELATION,
    RECAP_RELATION,
    REMAKE_RELATION,
    OVA_OR_SPECIAL_RELATION,
    SPLIT_COUR_OR_SEASON_RELATION,
    UNKNOWN_REQUIRED_YEAR,
    INSUFFICIENT_INDEPENDENT_SIGNALS,
    ALREADY_LINKED
}

data class MediaCandidatePairKey private constructor(val first: LinkedMediaIdentity, val second: LinkedMediaIdentity) {
    val keyString: String = "${first.source.name}:${first.externalId}|${second.source.name}:${second.externalId}"

    companion object {
        fun of(a: LinkedMediaIdentity, b: LinkedMediaIdentity): MediaCandidatePairKey {
            val (first, second) = if (compareIdentities(a, b) <= 0) a to b else b to a
            return MediaCandidatePairKey(first, second)
        }

        private fun compareIdentities(a: LinkedMediaIdentity, b: LinkedMediaIdentity): Int {
            val sourceCmp = a.source.name.compareTo(b.source.name)
            if (sourceCmp != 0) return sourceCmp
            val typeCmp = a.mediaType.name.compareTo(b.mediaType.name)
            if (typeCmp != 0) return typeCmp
            return a.externalId.compareTo(b.externalId)
        }
    }
}

data class MediaEquivalenceReason(val code: String, val description: String)

data class MediaEquivalenceEvaluation(
    val first: LinkedMediaIdentity,
    val second: LinkedMediaIdentity,
    val classification: MediaEquivalenceClassification,
    val positiveSignals: Set<MediaEquivalenceSignal>,
    val negativeSignals: Set<MediaEquivalenceNegativeSignal>,
    val explanationReasons: List<MediaEquivalenceReason>
) {
    val pairKey: MediaCandidatePairKey = MediaCandidatePairKey.of(first, second)

    init {
        require(first != second) { "Candidate pair must involve two distinct identities" }
    }
}

data class MediaEquivalenceCandidate(val evaluation: MediaEquivalenceEvaluation) {
    val pairKey: MediaCandidatePairKey get() = evaluation.pairKey
}
