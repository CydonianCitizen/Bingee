package com.cydoniancitizen.bingee.data.imports.tvtime

import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.data.imports.model.ImportSourceLocation
import com.cydoniancitizen.bingee.data.imports.model.ImportedIdentityNamespace
import com.cydoniancitizen.bingee.data.imports.model.ImportedMediaHint
import com.cydoniancitizen.bingee.data.imports.model.ImportedSourceIdentity
import com.cydoniancitizen.bingee.data.imports.model.ImportedWatchRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TvTimeReviewModelTest {
    @Test
    fun onlyExactAndHighConfidenceDefaultToAccepted() {
        val exact = review(TvTimeMatchConfidence.EXACT, candidate("1"))
        val high = review(TvTimeMatchConfidence.HIGH_CONFIDENCE, candidate("2"))
        val ambiguous = review(TvTimeMatchConfidence.AMBIGUOUS, null)
        val unmatched = review(TvTimeMatchConfidence.UNMATCHED, null)
        val invalid = review(TvTimeMatchConfidence.INVALID, null)

        assertEquals(TvTimeReviewAction.ACCEPT_PROPOSED, exact.action)
        assertEquals(TvTimeReviewAction.ACCEPT_PROPOSED, high.action)
        assertEquals(TvTimeReviewAction.UNDECIDED, ambiguous.action)
        assertEquals(TvTimeReviewAction.UNDECIDED, unmatched.action)
        assertEquals(TvTimeReviewAction.UNDECIDED, invalid.action)
    }

    @Test
    fun selectionReplacementAndSkipAreExplicit() {
        val proposed = candidate("1")
        val replacement = candidate("2")
        val review = review(TvTimeMatchConfidence.HIGH_CONFIDENCE, proposed)

        assertEquals(proposed, review.effectiveCandidate())
        assertEquals(
            replacement,
            review.copy(
                action = TvTimeReviewAction.SELECT_CANDIDATE,
                selectedCandidate = replacement
            ).effectiveCandidate()
        )
        assertNull(review.copy(action = TvTimeReviewAction.SKIP).effectiveCandidate())
    }

    @Test
    fun reportCountsClassificationsAndSkipsDeterministically() {
        val report = TvTimeMatchReport(
            media = listOf(
                review(TvTimeMatchConfidence.EXACT, candidate("1")),
                review(TvTimeMatchConfidence.HIGH_CONFIDENCE, candidate("2")),
                review(TvTimeMatchConfidence.AMBIGUOUS, null),
                review(TvTimeMatchConfidence.UNMATCHED, null).copy(action = TvTimeReviewAction.SKIP)
            ),
            episodes = emptyList()
        )

        assertEquals(1, report.exactCount)
        assertEquals(1, report.highConfidenceCount)
        assertEquals(1, report.needsReviewCount)
        assertEquals(1, report.unmatchedCount)
        assertEquals(1, report.skippedCount)
    }

    private fun review(confidence: TvTimeMatchConfidence, proposed: TmdbImportCandidate?) = TvTimeMediaReview(
        source = ImportedMediaHint(
            recordId = "movie:${proposed?.externalRef?.externalId ?: confidence.name}",
            mediaType = MediaType.MOVIE,
            title = "Synthetic",
            normalizedTitle = "synthetic",
            year = 2020,
            createdAt = null,
            identities = listOf(ImportedSourceIdentity(ImportedIdentityNamespace.TV_TIME, "synthetic")),
            watch = ImportedWatchRecord(false, null, 0, null),
            sourceLocation = ImportSourceLocation(0, 0, "$.synthetic"),
            warnings = emptyList()
        ),
        confidence = confidence,
        reason = when (confidence) {
            TvTimeMatchConfidence.EXACT -> TvTimeMatchReason.EXACT_EXTERNAL_ID
            TvTimeMatchConfidence.HIGH_CONFIDENCE -> TvTimeMatchReason.TITLE_AND_YEAR_UNIQUE
            TvTimeMatchConfidence.AMBIGUOUS -> TvTimeMatchReason.MULTIPLE_CANDIDATES
            TvTimeMatchConfidence.UNMATCHED -> TvTimeMatchReason.NO_CANDIDATE
            TvTimeMatchConfidence.INVALID -> TvTimeMatchReason.INVALID_SOURCE
            TvTimeMatchConfidence.SKIPPED -> TvTimeMatchReason.NO_CANDIDATE
        },
        proposed = proposed,
        alternatives = emptyList()
    )

    private fun candidate(id: String) = TmdbImportCandidate(
        externalRef = ExternalMediaRef(MediaSource.TMDB, id),
        mediaType = MediaType.MOVIE,
        title = "Synthetic $id",
        originalTitle = null,
        year = 2020,
        posterUrl = null,
        overview = null
    )
}
