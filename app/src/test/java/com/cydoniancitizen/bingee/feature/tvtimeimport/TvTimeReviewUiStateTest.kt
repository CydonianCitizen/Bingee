package com.cydoniancitizen.bingee.feature.tvtimeimport

import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.data.imports.model.ImportSourceLocation
import com.cydoniancitizen.bingee.data.imports.model.ImportedEpisodeHint
import com.cydoniancitizen.bingee.data.imports.model.ImportedIdentityNamespace
import com.cydoniancitizen.bingee.data.imports.model.ImportedMediaHint
import com.cydoniancitizen.bingee.data.imports.model.ImportedSourceIdentity
import com.cydoniancitizen.bingee.data.imports.model.ImportedWatchRecord
import com.cydoniancitizen.bingee.data.imports.tvtime.TvTimeEpisodeReview
import com.cydoniancitizen.bingee.data.imports.tvtime.TvTimeMatchConfidence
import com.cydoniancitizen.bingee.data.imports.tvtime.TvTimeMatchReason
import com.cydoniancitizen.bingee.data.imports.tvtime.TvTimeMatchReport
import com.cydoniancitizen.bingee.data.imports.tvtime.TvTimeMediaReview
import com.cydoniancitizen.bingee.data.imports.tvtime.TvTimeReviewAction
import java.util.concurrent.Executors
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class TvTimeReviewUiStateTest {
    @Test
    fun largeReviewIsNotDerivedByStateConstructionAndRunsOnComputeDispatcher() = runTest {
        val review = report().episodes.first()
        var reads = 0
        val episodes = object : AbstractList<TvTimeEpisodeReview>() {
            override val size = 100_000
            override fun get(index: Int): TvTimeEpisodeReview {
                assertEquals("review-compute", Thread.currentThread().name.substringBefore(" @"))
                reads++
                return review
            }
        }
        val state = TvTimeImportUiState(matchReport = report().copy(episodes = episodes))
        assertNull(state.reviewState)
        assertEquals(0, reads)

        Executors.newSingleThreadExecutor { Runnable -> Thread(Runnable, "review-compute") }
            .asCoroutineDispatcher().use { dispatcher ->
                val projected = flowOf(state).projectReview(dispatcher).first()
                assertEquals(100_004, projected.reviewState!!.filterCounts.getValue(TvTimeMatchFilter.ALL))
                assertEquals(100_000, projected.reviewState.visibleEpisodeGroups.sumOf { it.reviews.size })
                val editable = MutableStateFlow(state)
                editable.updateReviewReport(dispatcher) { report ->
                    report.copy(episodes = report.episodes.map { it.copy(action = TvTimeReviewAction.SKIP) })
                }
                assertEquals(100_001, editable.value.matchReport!!.skippedCount)
            }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun pendingEditPreservesNewFilterAndCannotResurrectResetArchive() = runTest {
        val source = MutableStateFlow(TvTimeImportUiState(matchReport = report()))
        val dispatcher = StandardTestDispatcher(testScheduler)
        val edit = launch(UnconfinedTestDispatcher(testScheduler)) {
            source.updateReviewReport(dispatcher) { it.copy(episodes = emptyList()) }
        }
        source.value = source.value.copy(selectedFilter = TvTimeMatchFilter.SKIPPED)
        runCurrent()
        edit.join()
        assertEquals(TvTimeMatchFilter.SKIPPED, source.value.selectedFilter)
        assertEquals(emptyList<TvTimeEpisodeReview>(), source.value.matchReport!!.episodes)

        launch(UnconfinedTestDispatcher(testScheduler)) {
            source.updateReviewReport(dispatcher) { it.copy(media = emptyList()) }
        }
        source.value = TvTimeImportUiState()
        runCurrent()
        assertEquals(TvTimeImportUiState(), source.value)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun rapidFilterChangesPublishMatchingRowsForLatestFilterAndResetClearsReview() = runTest {
        val source = MutableStateFlow(TvTimeImportUiState(matchReport = report()))
        val emitted = mutableListOf<TvTimeImportUiState>()
        val job = backgroundScope.launch {
            source.projectReview(StandardTestDispatcher(testScheduler)).collect { emitted += it }
        }
        runCurrent()
        source.value = source.value.copy(selectedFilter = TvTimeMatchFilter.EXACT)
        source.value = source.value.copy(selectedFilter = TvTimeMatchFilter.SKIPPED)
        runCurrent()

        val latest = emitted.last()
        assertEquals(TvTimeMatchFilter.SKIPPED, latest.selectedFilter)
        assertEquals(listOf("m-skip"), latest.reviewState!!.visibleMedia.map { it.source.recordId })
        assertEquals(
            listOf("z-2-1"),
            latest.reviewState.visibleEpisodeGroups.flatMap {
                it.reviews
            }.map { it.source.recordId }
        )
        source.value = TvTimeImportUiState()
        runCurrent()
        assertNull(emitted.last().reviewState)
        job.cancel()
    }

    @Test
    fun derivationKeepsCountsVisibleRowsGroupingAndOrdering() {
        val report = report()

        val state = deriveTvTimeReviewUiState(report, invalidRecordCount = 2, TvTimeMatchFilter.ALL)

        assertEquals(10, state.filterCounts.getValue(TvTimeMatchFilter.ALL))
        assertEquals(2, state.filterCounts.getValue(TvTimeMatchFilter.EXACT))
        assertEquals(2, state.filterCounts.getValue(TvTimeMatchFilter.HIGH_CONFIDENCE))
        assertEquals(2, state.filterCounts.getValue(TvTimeMatchFilter.NEEDS_REVIEW))
        assertEquals(2, state.filterCounts.getValue(TvTimeMatchFilter.SKIPPED))
        assertEquals(report.media.map { it.source.recordId }, state.visibleMedia.map { it.source.recordId })
        assertEquals(
            listOf("Alpha:1", "Alpha:2", "Zulu:2"),
            state.visibleEpisodeGroups.map { "${it.seriesTitle}:${it.seasonNumber}" }
        )
        assertEquals(
            listOf("z-2-2", "z-2-1"),
            state.visibleEpisodeGroups.last().reviews.map { it.source.recordId }
        )
        assertEquals(true, state.showInvalidRecordSummary)
    }

    @Test
    fun filterChangeDerivesOnlyMatchingRowsAndGroups() {
        val state = deriveTvTimeReviewUiState(report(), 2, TvTimeMatchFilter.NEEDS_REVIEW)

        assertEquals(listOf("m-review"), state.visibleMedia.map { it.source.recordId })
        assertEquals(1, state.visibleEpisodeGroups.size)
        assertEquals(1, state.visibleEpisodeGroups.sumOf { it.reviews.size })
        assertEquals(false, state.showInvalidRecordSummary)
    }

    @Test
    fun unrelatedUiStateDoesNotAlterDerivedReviewState() {
        val report = report()
        val derived = deriveTvTimeReviewUiState(report, 2, TvTimeMatchFilter.ALL)
        val state = TvTimeImportUiState(
            matchReport = report,
            reviewState = derived,
            manualCandidates = mapOf("m-review" to emptyList())
        )

        assertSame(derived, state.reviewState)
    }

    private fun report() = TvTimeMatchReport(
        media = listOf(
            media("parent-z", "Zulu", TvTimeMatchConfidence.EXACT),
            media("parent-a", "Alpha", TvTimeMatchConfidence.HIGH_CONFIDENCE),
            media("m-review", "Review", TvTimeMatchConfidence.AMBIGUOUS),
            media("m-skip", "Skipped", TvTimeMatchConfidence.UNMATCHED, TvTimeReviewAction.SKIP)
        ),
        episodes = listOf(
            episode("z-2-2", "parent-z", 2, 2, TvTimeMatchConfidence.HIGH_CONFIDENCE),
            episode("a-2-1", "parent-a", 2, 1, TvTimeMatchConfidence.AMBIGUOUS),
            episode("a-1-1", "parent-a", 1, 1, TvTimeMatchConfidence.EXACT),
            episode("z-2-1", "parent-z", 2, 1, TvTimeMatchConfidence.UNMATCHED, TvTimeReviewAction.SKIP)
        )
    )

    private fun media(
        id: String,
        title: String,
        confidence: TvTimeMatchConfidence,
        action: TvTimeReviewAction = TvTimeReviewAction.UNDECIDED
    ) = TvTimeMediaReview(
        source = ImportedMediaHint(
            recordId = id,
            mediaType = MediaType.SERIES,
            title = title,
            normalizedTitle = title.lowercase(),
            year = 2020,
            createdAt = null,
            identities = listOf(ImportedSourceIdentity(ImportedIdentityNamespace.TV_TIME, id)),
            watch = null,
            sourceLocation = ImportSourceLocation(0, 0, "$.$id"),
            warnings = emptyList()
        ),
        confidence = confidence,
        reason = TvTimeMatchReason.NO_CANDIDATE,
        proposed = null,
        alternatives = emptyList(),
        action = action
    )

    private fun episode(
        id: String,
        parentId: String,
        season: Int,
        episode: Int,
        confidence: TvTimeMatchConfidence,
        action: TvTimeReviewAction = TvTimeReviewAction.UNDECIDED
    ) = TvTimeEpisodeReview(
        source = ImportedEpisodeHint(
            recordId = id,
            parentRecordId = parentId,
            parentIdentities = listOf(ImportedSourceIdentity(ImportedIdentityNamespace.TV_TIME, parentId)),
            seasonNumber = season,
            episodeNumber = episode,
            title = id,
            normalizedTitle = id,
            special = false,
            specialsSeason = false,
            identities = emptyList(),
            watch = ImportedWatchRecord(false, null, null, null),
            sourceLocation = ImportSourceLocation(0, 0, "$.$id"),
            warnings = emptyList()
        ),
        confidence = confidence,
        reason = TvTimeMatchReason.NO_CANDIDATE,
        proposed = null,
        alternatives = emptyList(),
        action = action
    )
}
