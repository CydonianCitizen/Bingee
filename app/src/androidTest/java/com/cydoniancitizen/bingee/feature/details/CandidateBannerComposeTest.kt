package com.cydoniancitizen.bingee.feature.details

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.cydoniancitizen.bingee.core.model.LinkedMediaIdentity
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.domain.equivalence.MediaEquivalenceCandidate
import com.cydoniancitizen.bingee.domain.equivalence.MediaEquivalenceClassification
import com.cydoniancitizen.bingee.domain.equivalence.MediaEquivalenceEvaluation
import com.cydoniancitizen.bingee.domain.equivalence.MediaEquivalenceSignal
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CandidateBannerComposeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val tmdbIdentity = LinkedMediaIdentity(MediaSource.TMDB, MediaType.MOVIE, "550")
    private val jikanIdentity = LinkedMediaIdentity(MediaSource.JIKAN, MediaType.ANIME, "199")

    @Test
    fun candidateBannerCard_displaysSuggestionAndTriggersCompare() {
        val candidate = MediaEquivalenceCandidate(
            evaluation = MediaEquivalenceEvaluation(
                first = tmdbIdentity,
                second = jikanIdentity,
                classification = MediaEquivalenceClassification.EXACT_IDENTITY,
                positiveSignals = setOf(MediaEquivalenceSignal.SHARED_IMDB_ID),
                negativeSignals = emptySet(),
                explanationReasons = emptyList()
            )
        )

        var compareClicked = false

        composeTestRule.setContent {
            CandidateBannerCard(
                candidate = candidate,
                onCompare = { compareClicked = true }
            )
        }

        composeTestRule.onNodeWithText("Possible duplicate").assertIsDisplayed()
        composeTestRule.onNodeWithText("Matching IMDb ID (Jikan (Anime))").assertIsDisplayed()
        composeTestRule.onNodeWithText("Compare versions").assertIsDisplayed()

        composeTestRule.onNodeWithText("Compare versions").performClick()
        assertTrue(compareClicked)
    }
}
