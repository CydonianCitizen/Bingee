package com.cydoniancitizen.bingee.feature.equivalence

import com.cydoniancitizen.bingee.core.model.LinkedMediaIdentity
import com.cydoniancitizen.bingee.core.model.MediaLinkAuditOrigin
import com.cydoniancitizen.bingee.core.model.MediaLinkGroup
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.core.result.AppError
import com.cydoniancitizen.bingee.core.result.AppResult
import com.cydoniancitizen.bingee.domain.equivalence.CandidateMediaProjection
import com.cydoniancitizen.bingee.domain.equivalence.MediaCandidatePairKey
import com.cydoniancitizen.bingee.domain.equivalence.MediaEquivalenceCandidate
import com.cydoniancitizen.bingee.domain.equivalence.MediaEquivalenceClassification
import com.cydoniancitizen.bingee.domain.equivalence.MediaEquivalenceEvaluation
import com.cydoniancitizen.bingee.domain.equivalence.MediaEquivalenceSignal
import com.cydoniancitizen.bingee.domain.repository.MediaEquivalenceCandidateRepository
import com.cydoniancitizen.bingee.domain.repository.MediaLinkRepository
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MediaComparisonViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val fakeCandidateRepository = FakeCandidateRepository()
    private val fakeLinkRepository = FakeLinkRepository()

    private lateinit var viewModel: MediaComparisonViewModel

    private val firstIdentity = LinkedMediaIdentity(MediaSource.TMDB, MediaType.MOVIE, "129")
    private val secondIdentity = LinkedMediaIdentity(MediaSource.JIKAN, MediaType.ANIME, "199")

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = MediaComparisonViewModel(fakeCandidateRepository, fakeLinkRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun loadComparison_populatesEvaluationAndDefaultPreferred() = runTest {
        val eval = MediaEquivalenceEvaluation(
            first = firstIdentity,
            second = secondIdentity,
            classification = MediaEquivalenceClassification.EXACT_IDENTITY,
            positiveSignals = setOf(MediaEquivalenceSignal.SHARED_IMDB_ID),
            negativeSignals = emptySet(),
            explanationReasons = emptyList()
        )
        fakeCandidateRepository.evalMap[MediaCandidatePairKey.of(firstIdentity, secondIdentity)] = eval

        viewModel.loadComparison(firstIdentity, secondIdentity)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(firstIdentity, state.firstIdentity)
        assertEquals(secondIdentity, state.secondIdentity)
        assertEquals(eval, state.evaluation)
        assertEquals(firstIdentity, state.selectedPreferred)
        assertFalse(state.isStale)
    }

    @Test
    fun selectPreferred_updatesSelectedPreferred() = runTest {
        viewModel.loadComparison(firstIdentity, secondIdentity)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.selectPreferred(secondIdentity)

        assertEquals(secondIdentity, viewModel.uiState.value.selectedPreferred)
    }

    @Test
    fun confirmLink_createsLinkSuccessfully() = runTest {
        val eval = MediaEquivalenceEvaluation(
            first = firstIdentity,
            second = secondIdentity,
            classification = MediaEquivalenceClassification.STRONG_POSSIBLE_SAME_WORK,
            positiveSignals = setOf(
                MediaEquivalenceSignal.EXACT_NORMALIZED_TITLE,
                MediaEquivalenceSignal.EXACT_RELEASE_YEAR
            ),
            negativeSignals = emptySet(),
            explanationReasons = emptyList()
        )
        fakeCandidateRepository.evalMap[MediaCandidatePairKey.of(firstIdentity, secondIdentity)] = eval

        viewModel.loadComparison(firstIdentity, secondIdentity)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.selectPreferred(secondIdentity)
        viewModel.confirmLink()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.linkSuccess)
        assertEquals(1, fakeLinkRepository.createdLinks.size)
        val created = fakeLinkRepository.createdLinks.first()
        assertEquals(secondIdentity, created.preferred)
    }

    @Test
    fun confirmLink_rejectsStaleCandidate() = runTest {
        val eval = MediaEquivalenceEvaluation(
            first = firstIdentity,
            second = secondIdentity,
            classification = MediaEquivalenceClassification.AMBIGUOUS,
            positiveSignals = emptySet(),
            negativeSignals = emptySet(),
            explanationReasons = emptyList()
        )
        fakeCandidateRepository.evalMap[MediaCandidatePairKey.of(firstIdentity, secondIdentity)] = eval

        viewModel.loadComparison(firstIdentity, secondIdentity)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.confirmLink()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isStale)
        assertFalse(viewModel.uiState.value.linkSuccess)
        assertEquals(0, fakeLinkRepository.createdLinks.size)
    }

    private class FakeCandidateRepository : MediaEquivalenceCandidateRepository {
        val evalMap = mutableMapOf<MediaCandidatePairKey, MediaEquivalenceEvaluation>()

        override fun observeLibraryCandidates(): Flow<List<MediaEquivalenceCandidate>> = flowOf(emptyList())

        override fun observeCandidatesForMedia(identity: LinkedMediaIdentity): Flow<List<MediaEquivalenceCandidate>> =
            flowOf(emptyList())

        override suspend fun evaluatePair(
            first: LinkedMediaIdentity,
            second: LinkedMediaIdentity
        ): AppResult<MediaEquivalenceEvaluation> {
            val key = MediaCandidatePairKey.of(first, second)
            val eval = evalMap[key] ?: MediaEquivalenceEvaluation(
                first = first,
                second = second,
                classification = MediaEquivalenceClassification.INVALID_CANDIDATE,
                positiveSignals = emptySet(),
                negativeSignals = emptySet(),
                explanationReasons = emptyList()
            )
            return AppResult.Success(eval)
        }
    }

    private class FakeLinkRepository : MediaLinkRepository {
        data class Created(
            val first: LinkedMediaIdentity,
            val second: LinkedMediaIdentity,
            val preferred: LinkedMediaIdentity
        )
        val createdLinks = mutableListOf<Created>()

        override fun observeLinkForMedia(identity: LinkedMediaIdentity): Flow<MediaLinkGroup?> = flowOf(null)

        override fun observeLinkGroup(
            groupId: com.cydoniancitizen.bingee.core.model.MediaLinkGroupId
        ): Flow<MediaLinkGroup?> = flowOf(null)

        override suspend fun createLink(
            firstMedia: LinkedMediaIdentity,
            secondMedia: LinkedMediaIdentity,
            preferredPresentation: LinkedMediaIdentity,
            origin: MediaLinkAuditOrigin
        ): AppResult<MediaLinkGroup> {
            createdLinks.add(Created(firstMedia, secondMedia, preferredPresentation))
            val group = MediaLinkGroup(
                groupId = com.cydoniancitizen.bingee.core.model.MediaLinkGroupId("test-uuid"),
                first = firstMedia,
                second = secondMedia,
                preferredPresentation = preferredPresentation,
                createdAt = Instant.now(),
                updatedAt = Instant.now()
            )
            return AppResult.Success(group)
        }

        override suspend fun changePreferredPresentation(
            groupId: com.cydoniancitizen.bingee.core.model.MediaLinkGroupId,
            preferredPresentation: LinkedMediaIdentity,
            origin: MediaLinkAuditOrigin
        ): AppResult<MediaLinkGroup> = AppResult.Failure(AppError.LinkError.LinkGroupNotFound)

        override suspend fun unlink(
            groupId: com.cydoniancitizen.bingee.core.model.MediaLinkGroupId,
            origin: MediaLinkAuditOrigin
        ): AppResult<Unit> = AppResult.Success(Unit)
    }
}
