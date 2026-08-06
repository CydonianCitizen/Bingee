package com.cydoniancitizen.bingee.feature.equivalence

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import com.cydoniancitizen.bingee.core.designsystem.theme.BingeeTheme
import com.cydoniancitizen.bingee.core.model.LinkedMediaIdentity
import com.cydoniancitizen.bingee.core.model.MediaLinkAuditOrigin
import com.cydoniancitizen.bingee.core.model.MediaLinkGroup
import com.cydoniancitizen.bingee.core.model.MediaLinkGroupId
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.core.result.AppError
import com.cydoniancitizen.bingee.core.result.AppResult
import com.cydoniancitizen.bingee.domain.equivalence.MediaCandidatePairKey
import com.cydoniancitizen.bingee.domain.equivalence.MediaEquivalenceCandidate
import com.cydoniancitizen.bingee.domain.equivalence.MediaEquivalenceClassification
import com.cydoniancitizen.bingee.domain.equivalence.MediaEquivalenceEvaluation
import com.cydoniancitizen.bingee.domain.equivalence.MediaEquivalenceSignal
import com.cydoniancitizen.bingee.domain.repository.MediaEquivalenceCandidateRepository
import com.cydoniancitizen.bingee.domain.repository.MediaLinkRepository
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MediaComparisonBottomSheetComposeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val firstIdentity = LinkedMediaIdentity(MediaSource.TMDB, MediaType.MOVIE, "129")
    private val secondIdentity = LinkedMediaIdentity(MediaSource.JIKAN, MediaType.ANIME, "199")

    @Test
    fun comparisonBottomSheet_displaysBothMembersAndEvidenceReasons() {
        val fakeCandidateRepo = FakeCandidateRepository()
        val fakeLinkRepo = FakeLinkRepository()

        val eval = MediaEquivalenceEvaluation(
            first = firstIdentity,
            second = secondIdentity,
            classification = MediaEquivalenceClassification.EXACT_IDENTITY,
            positiveSignals = setOf(MediaEquivalenceSignal.SHARED_IMDB_ID, MediaEquivalenceSignal.EXACT_RELEASE_YEAR),
            negativeSignals = emptySet(),
            explanationReasons = emptyList()
        )
        fakeCandidateRepo.evalMap[MediaCandidatePairKey.of(firstIdentity, secondIdentity)] = eval

        val viewModel = MediaComparisonViewModel(fakeCandidateRepo, fakeLinkRepo)

        composeTestRule.setContent {
            BingeeTheme(darkTheme = true) {
                MediaComparisonBottomSheet(
                    first = firstIdentity,
                    second = secondIdentity,
                    onDismissRequest = {},
                    onLinkSuccess = {},
                    viewModel = viewModel
                )
            }
        }

        composeTestRule.onNodeWithText("Compare versions").assertIsDisplayed()
        composeTestRule.onNodeWithText("Exact shared identity (IMDb)").assertIsDisplayed()
        composeTestRule.onNodeWithText("TMDB (MOVIE)").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(
            "Jikan (Anime) ID 199"
        ).performScrollTo().assertIsDisplayed().performClick()
        composeTestRule.onNodeWithContentDescription(
            "Jikan (Anime) ID 199 selected as preferred version"
        ).assertIsDisplayed()
        assertTrue(fakeLinkRepo.createdLinks.isEmpty())
        composeTestRule.onNodeWithText("• Verified matching IMDb identifier").assertIsDisplayed()
        composeTestRule.onNodeWithText("• Matching release year").assertIsDisplayed()
        repeat(3) { composeTestRule.onNode(hasScrollAction()).performTouchInput { swipeUp() } }
        composeTestRule.onNodeWithText(
            "Linking changes how Bingee presents these titles. It does not delete or combine progress, ratings, or provider data."
        ).assertIsDisplayed()
    }

    @Test
    fun comparisonBottomSheet_confirmLink_executesLinkCreation() {
        val fakeCandidateRepo = FakeCandidateRepository()
        val fakeLinkRepo = FakeLinkRepository()

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
        fakeCandidateRepo.evalMap[MediaCandidatePairKey.of(firstIdentity, secondIdentity)] = eval

        val viewModel = MediaComparisonViewModel(fakeCandidateRepo, fakeLinkRepo)
        var linkSuccessCalled = false

        composeTestRule.setContent {
            MediaComparisonBottomSheet(
                first = firstIdentity,
                second = secondIdentity,
                onDismissRequest = {},
                onLinkSuccess = { linkSuccessCalled = true },
                viewModel = viewModel
            )
        }

        composeTestRule.onNodeWithText("Link versions").performClick()
        composeTestRule.waitForIdle()

        assertTrue(linkSuccessCalled)
        assertEquals(1, fakeLinkRepo.createdLinks.size)
        assertEquals(firstIdentity, fakeLinkRepo.createdLinks.first().preferred)
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

        override fun observeLinkGroup(groupId: MediaLinkGroupId): Flow<MediaLinkGroup?> = flowOf(null)

        override suspend fun createLink(
            first: LinkedMediaIdentity,
            second: LinkedMediaIdentity,
            preferredPresentation: LinkedMediaIdentity,
            origin: MediaLinkAuditOrigin
        ): AppResult<MediaLinkGroup> {
            createdLinks.add(Created(first, second, preferredPresentation))
            val group = MediaLinkGroup(
                groupId = MediaLinkGroupId("test-uuid"),
                first = first,
                second = second,
                preferredPresentation = preferredPresentation,
                createdAt = Instant.now(),
                updatedAt = Instant.now()
            )
            return AppResult.Success(group)
        }

        override suspend fun changePreferredPresentation(
            groupId: MediaLinkGroupId,
            preferredPresentation: LinkedMediaIdentity,
            origin: MediaLinkAuditOrigin
        ): AppResult<MediaLinkGroup> = AppResult.Failure(AppError.LinkError.LinkGroupNotFound)

        override suspend fun unlink(groupId: MediaLinkGroupId, origin: MediaLinkAuditOrigin): AppResult<Unit> =
            AppResult.Success(Unit)
    }
}
