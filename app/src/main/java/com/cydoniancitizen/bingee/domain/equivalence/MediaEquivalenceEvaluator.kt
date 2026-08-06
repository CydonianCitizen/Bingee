package com.cydoniancitizen.bingee.domain.equivalence

import com.cydoniancitizen.bingee.core.model.AnimeFormat
import com.cydoniancitizen.bingee.core.model.LinkedMediaIdentity
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import java.time.LocalDate
import kotlin.math.abs

data class CandidateMediaProjection(
    val identity: LinkedMediaIdentity,
    val title: String,
    val originalTitle: String? = null,
    val englishTitle: String? = null,
    val japaneseTitle: String? = null,
    val releaseYear: Int? = null,
    val releaseDate: LocalDate? = null,
    val animeFormat: AnimeFormat? = null,
    val tmdbSeasonCount: Int? = null,
    val imdbId: String? = null,
    val relationTypes: Set<String> = emptySet(),
    val isAlreadyLinked: Boolean = false,
    val isLibraryMember: Boolean = true
)

object MediaEquivalenceEvaluator {

    fun evaluate(first: CandidateMediaProjection, second: CandidateMediaProjection): MediaEquivalenceEvaluation {
        val (p1, p2) = if (first.identity.source == MediaSource.TMDB) first to second else second to first
        val id1 = p1.identity
        val id2 = p2.identity

        val positiveSignals = mutableSetOf<MediaEquivalenceSignal>()
        val negativeSignals = mutableSetOf<MediaEquivalenceNegativeSignal>()
        val reasons = mutableListOf<MediaEquivalenceReason>()

        // 1. Validate candidate pair constraints
        if (id1 == id2) {
            return MediaEquivalenceEvaluation(
                id1,
                id2,
                MediaEquivalenceClassification.INVALID_CANDIDATE,
                emptySet(),
                emptySet(),
                listOf(MediaEquivalenceReason("SAME_IDENTITY", "Identical provider identity"))
            )
        }

        if (id1.source == id2.source) {
            return MediaEquivalenceEvaluation(
                id1,
                id2,
                MediaEquivalenceClassification.INVALID_CANDIDATE,
                emptySet(),
                emptySet(),
                listOf(MediaEquivalenceReason("SAME_PROVIDER", "Same-provider candidates are not supported"))
            )
        }

        if (p1.isAlreadyLinked || p2.isAlreadyLinked) {
            negativeSignals.add(MediaEquivalenceNegativeSignal.ALREADY_LINKED)
            return MediaEquivalenceEvaluation(
                id1,
                id2,
                MediaEquivalenceClassification.INVALID_CANDIDATE,
                positiveSignals,
                negativeSignals,
                listOf(MediaEquivalenceReason("ALREADY_LINKED", "One or both members are in an active link group"))
            )
        }

        // 2. Format & Granularity Compatibility
        val formatResult = if (id1.source == MediaSource.TMDB && p2.animeFormat != null) {
            MediaFormatCompatibility.evaluate(id1.mediaType, p2.animeFormat, p1.tmdbSeasonCount)
        } else {
            FormatCompatibilityResult.COMPATIBLE
        }

        when (formatResult) {
            FormatCompatibilityResult.COMPATIBLE -> {
                positiveSignals.add(MediaEquivalenceSignal.COMPATIBLE_MEDIA_TYPE)
                positiveSignals.add(MediaEquivalenceSignal.COMPATIBLE_FORMAT)
            }
            FormatCompatibilityResult.INCOMPATIBLE -> {
                negativeSignals.add(MediaEquivalenceNegativeSignal.INCOMPATIBLE_FORMAT)
                negativeSignals.add(MediaEquivalenceNegativeSignal.INCOMPATIBLE_MEDIA_TYPE)
                reasons.add(MediaEquivalenceReason("INCOMPATIBLE_FORMAT", "Incompatible media types or formats"))
            }
            FormatCompatibilityResult.GRANULARITY_RISK -> {
                negativeSignals.add(MediaEquivalenceNegativeSignal.GRANULARITY_MISMATCH)
                reasons.add(
                    MediaEquivalenceReason("GRANULARITY_MISMATCH", "Multi-season show compared to single entry")
                )
            }
            FormatCompatibilityResult.SPECIAL_OR_OVA_RISK -> {
                negativeSignals.add(MediaEquivalenceNegativeSignal.OVA_OR_SPECIAL_RELATION)
                reasons.add(MediaEquivalenceReason("SPECIAL_OR_OVA_RISK", "OVA, Special, or ONA format risk"))
            }
        }

        // 3. Provider Relations (Negative Signals)
        val allRelations = (p1.relationTypes + p2.relationTypes).map { it.lowercase() }
        if (allRelations.any { it.contains("sequel") }) {
            negativeSignals.add(MediaEquivalenceNegativeSignal.SEQUEL_RELATION)
            reasons.add(MediaEquivalenceReason("SEQUEL_RELATION", "Documented sequel relationship"))
        }
        if (allRelations.any { it.contains("prequel") }) {
            negativeSignals.add(MediaEquivalenceNegativeSignal.PREQUEL_RELATION)
            reasons.add(MediaEquivalenceReason("PREQUEL_RELATION", "Documented prequel relationship"))
        }
        if (allRelations.any { it.contains("summary") || it.contains("recap") }) {
            negativeSignals.add(MediaEquivalenceNegativeSignal.RECAP_RELATION)
            reasons.add(MediaEquivalenceReason("RECAP_RELATION", "Summary or recap relationship"))
        }
        if (allRelations.any {
                it.contains("alternative") || it.contains("remake") || it.contains("spinoff") ||
                    it.contains("spin-off") ||
                    it.contains("side story")
            }
        ) {
            negativeSignals.add(MediaEquivalenceNegativeSignal.REMAKE_RELATION)
            reasons.add(MediaEquivalenceReason("REMAKE_RELATION", "Alternative, spin-off, or remake relationship"))
        }

        // 4. IMDb Identity Check
        val imdb1 = p1.imdbId?.takeIf { it.isNotBlank() }
        val imdb2 = p2.imdbId?.takeIf { it.isNotBlank() }

        val hasSharedImdb = imdb1 != null && imdb2 != null && imdb1.equals(imdb2, ignoreCase = true)
        val hasConflictingImdb = imdb1 != null && imdb2 != null && !imdb1.equals(imdb2, ignoreCase = true)

        if (hasConflictingImdb) {
            negativeSignals.add(MediaEquivalenceNegativeSignal.CONFLICTING_IMDB_IDS)
            reasons.add(MediaEquivalenceReason("CONFLICTING_IMDB_IDS", "Mismatched IMDb identifiers"))
        } else if (hasSharedImdb) {
            positiveSignals.add(MediaEquivalenceSignal.SHARED_IMDB_ID)
            reasons.add(MediaEquivalenceReason("SHARED_IMDB_ID", "Matching IMDb title identifier ($imdb1)"))
        }

        // 5. Release Year & Date Check
        val y1 = p1.releaseYear ?: p1.releaseDate?.year
        val y2 = p2.releaseYear ?: p2.releaseDate?.year

        if (y1 != null && y2 != null) {
            val diff = abs(y1 - y2)
            if (diff == 0) {
                positiveSignals.add(MediaEquivalenceSignal.EXACT_RELEASE_YEAR)
            } else if (diff > 1) {
                negativeSignals.add(MediaEquivalenceNegativeSignal.RELEASE_YEAR_MISMATCH)
                reasons.add(MediaEquivalenceReason("RELEASE_YEAR_MISMATCH", "Release year mismatch ($y1 vs $y2)"))
            }
        } else {
            negativeSignals.add(MediaEquivalenceNegativeSignal.UNKNOWN_REQUIRED_YEAR)
            reasons.add(MediaEquivalenceReason("UNKNOWN_REQUIRED_YEAR", "Missing required release year"))
        }

        if (p1.releaseDate != null && p2.releaseDate != null) {
            if (p1.releaseDate == p2.releaseDate) {
                positiveSignals.add(MediaEquivalenceSignal.EXACT_RELEASE_DATE)
            } else {
                negativeSignals.add(MediaEquivalenceNegativeSignal.RELEASE_DATE_MISMATCH)
            }
        }

        // 6. Title Variant Matching
        val normTitle1 = MediaEquivalenceNormalizer.normalizeTitle(p1.title)
        val normTitle2 = MediaEquivalenceNormalizer.normalizeTitle(p2.title)

        val normOrig1 = MediaEquivalenceNormalizer.normalizeTitle(p1.originalTitle)
        val normOrig2 = MediaEquivalenceNormalizer.normalizeTitle(p2.originalTitle)

        val normEng1 = MediaEquivalenceNormalizer.normalizeTitle(p1.englishTitle)
        val normEng2 = MediaEquivalenceNormalizer.normalizeTitle(p2.englishTitle)

        val normJap1 = MediaEquivalenceNormalizer.normalizeTitle(p1.japaneseTitle)
        val normJap2 = MediaEquivalenceNormalizer.normalizeTitle(p2.japaneseTitle)

        val titles1 = setOfNotNull(normTitle1, normOrig1, normEng1, normJap1).filter { it.isNotBlank() }.toSet()
        val titles2 = setOfNotNull(normTitle2, normOrig2, normEng2, normJap2).filter { it.isNotBlank() }.toSet()

        val primaryTitleMatch = normTitle1.isNotBlank() && normTitle2.isNotBlank() && normTitle1 == normTitle2
        val sharedTitles = titles1.intersect(titles2)

        if (primaryTitleMatch) {
            positiveSignals.add(MediaEquivalenceSignal.EXACT_NORMALIZED_TITLE)
        }

        if (normOrig1.isNotBlank() &&
            (normOrig1 == normTitle2 || normOrig1 == normOrig2 || normOrig1 == normEng2 || normOrig1 == normJap2)
        ) {
            positiveSignals.add(MediaEquivalenceSignal.EXACT_ORIGINAL_TITLE)
        }
        if (normEng1.isNotBlank() &&
            (normEng1 == normTitle2 || normEng1 == normOrig2 || normEng1 == normEng2 || normEng1 == normJap2)
        ) {
            positiveSignals.add(MediaEquivalenceSignal.EXACT_ENGLISH_TITLE)
        }
        if (normEng2.isNotBlank() && (normEng2 == normTitle1 || normEng2 == normOrig1)) {
            positiveSignals.add(MediaEquivalenceSignal.EXACT_ENGLISH_TITLE)
        }
        if (normJap1.isNotBlank() && (normJap1 == normTitle2 || normJap1 == normOrig2 || normJap1 == normJap2)) {
            positiveSignals.add(MediaEquivalenceSignal.EXACT_JAPANESE_TITLE)
        }
        if (normJap2.isNotBlank() && (normJap2 == normTitle1 || normJap2 == normOrig1)) {
            positiveSignals.add(MediaEquivalenceSignal.EXACT_JAPANESE_TITLE)
        }

        if (sharedTitles.isNotEmpty()) {
            positiveSignals.add(MediaEquivalenceSignal.EXACT_NORMALIZED_TITLE)
        }

        // 7. Classification Determination
        val classification: MediaEquivalenceClassification = when {
            // EXACT IDENTITY: Shared IMDb ID + compatible format + no conflicting signals
            hasSharedImdb && formatResult == FormatCompatibilityResult.COMPATIBLE && !hasConflictingImdb -> {
                MediaEquivalenceClassification.EXACT_IDENTITY
            }

            // Conflicting IMDb IDs -> NOT_EQUIVALENT
            hasConflictingImdb -> {
                MediaEquivalenceClassification.NOT_EQUIVALENT
            }

            // Negative relation or granularity mismatch -> RELATED_DISTINCT
            negativeSignals.any {
                it == MediaEquivalenceNegativeSignal.SEQUEL_RELATION ||
                    it == MediaEquivalenceNegativeSignal.PREQUEL_RELATION ||
                    it == MediaEquivalenceNegativeSignal.RECAP_RELATION ||
                    it == MediaEquivalenceNegativeSignal.REMAKE_RELATION ||
                    it == MediaEquivalenceNegativeSignal.GRANULARITY_MISMATCH
            } -> {
                MediaEquivalenceClassification.RELATED_DISTINCT
            }

            // Format mismatch or year mismatch (>1 year) -> NOT_EQUIVALENT
            negativeSignals.any {
                it == MediaEquivalenceNegativeSignal.INCOMPATIBLE_FORMAT ||
                    it == MediaEquivalenceNegativeSignal.INCOMPATIBLE_MEDIA_TYPE ||
                    it == MediaEquivalenceNegativeSignal.RELEASE_YEAR_MISMATCH
            } -> {
                MediaEquivalenceClassification.NOT_EQUIVALENT
            }

            // STRONG POSSIBLE SAME WORK:
            // - Primary/variant title agreement
            // - Exact release year agreement
            // - Compatible format
            // - At least ONE independent corroborating signal beyond primary title equality
            //   (e.g., EXACT_ORIGINAL_TITLE, EXACT_ENGLISH_TITLE, EXACT_JAPANESE_TITLE, EXACT_RELEASE_DATE)
            primaryTitleMatch &&
                positiveSignals.contains(MediaEquivalenceSignal.EXACT_RELEASE_YEAR) &&
                formatResult == FormatCompatibilityResult.COMPATIBLE &&
                negativeSignals.isEmpty() &&
                (
                    positiveSignals.contains(MediaEquivalenceSignal.EXACT_ORIGINAL_TITLE) ||
                        positiveSignals.contains(MediaEquivalenceSignal.EXACT_ENGLISH_TITLE) ||
                        positiveSignals.contains(MediaEquivalenceSignal.EXACT_JAPANESE_TITLE) ||
                        positiveSignals.contains(MediaEquivalenceSignal.EXACT_RELEASE_DATE)
                    ) -> {
                MediaEquivalenceClassification.STRONG_POSSIBLE_SAME_WORK
            }

            // Check if title matches but lacks required corroborating signals or year
            primaryTitleMatch || sharedTitles.isNotEmpty() -> {
                if (negativeSignals.contains(MediaEquivalenceNegativeSignal.UNKNOWN_REQUIRED_YEAR)) {
                    MediaEquivalenceClassification.AMBIGUOUS
                } else if (!positiveSignals.contains(MediaEquivalenceSignal.EXACT_RELEASE_YEAR)) {
                    MediaEquivalenceClassification.AMBIGUOUS
                } else {
                    negativeSignals.add(MediaEquivalenceNegativeSignal.INSUFFICIENT_INDEPENDENT_SIGNALS)
                    MediaEquivalenceClassification.AMBIGUOUS
                }
            }

            else -> {
                MediaEquivalenceClassification.NOT_EQUIVALENT
            }
        }

        return MediaEquivalenceEvaluation(
            first = id1,
            second = id2,
            classification = classification,
            positiveSignals = positiveSignals,
            negativeSignals = negativeSignals,
            explanationReasons = reasons
        )
    }
}
