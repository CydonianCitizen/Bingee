package com.cydoniancitizen.bingee.feature.details

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.cydoniancitizen.bingee.core.designsystem.theme.BingeeTheme
import com.cydoniancitizen.bingee.core.model.AnimeRelation
import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.core.navigation.DetailRoute
import com.cydoniancitizen.bingee.debug.FakeAnimeData
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AnimeRelationScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun relatedEntriesAreClickableAndRemainProviderQualified() {
        val opened = AtomicReference<AnimeRelation>()
        composeRule.setContent {
            BingeeTheme {
                AnimeDetailsContent(
                    state = AnimeDetailsUiState(
                        content = AnimeDetailContentState.Content(FakeAnimeData.cachedDetails),
                        isInLibrary = true
                    ),
                    onBack = {},
                    onRefresh = {},
                    onRetry = {},
                    onToggleLibrary = {},
                    onIncrement = {},
                    onDecrement = {},
                    onSetCount = {},
                    onComplete = {},
                    onIncomplete = {},
                    onOpenRelated = opened::set,
                    onSelectRating = {},
                    onSaveRating = {},
                    onRemoveRating = {},
                    onDismissRatingError = {}
                )
            }
        }

        composeRule.onNodeWithText("Related anime").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Sequel · Synthetic Anime Sequel")
            .performScrollTo().performClick()
        assertEquals(FakeAnimeData.cachedDetails.details.relations.first(), opened.get())
    }

    @Test
    fun relationCopyExplainsNoSharedMembershipProgressOrRatingAndRouteKeepsSource() {
        val relation = FakeAnimeData.cachedDetails.details.relations.first()
        composeRule.setContent {
            BingeeTheme {
                AnimeDetailsContent(
                    state = AnimeDetailsUiState(
                        content = AnimeDetailContentState.Content(FakeAnimeData.cachedDetails)
                    ),
                    onBack = {},
                    onRefresh = {},
                    onRetry = {},
                    onToggleLibrary = {},
                    onIncrement = {},
                    onDecrement = {},
                    onSetCount = {},
                    onComplete = {},
                    onIncomplete = {},
                    onOpenRelated = {},
                    onSelectRating = {},
                    onSaveRating = {},
                    onRemoveRating = {},
                    onDismissRatingError = {}
                )
            }
        }

        composeRule.onNodeWithText(
            "Related entries keep separate Library membership, progress, and ratings."
        ).performScrollTo().assertIsDisplayed()

        val route = DetailRoute.create(relation.animeRef, MediaType.ANIME)
        assertEquals("details/JIKAN/ANIME/${relation.animeRef.externalId}", route)
        val parsed = DetailRoute.parse("JIKAN", "ANIME", relation.animeRef.externalId)
        assertEquals(relation.animeRef, parsed?.reference)
        assertEquals(MediaType.ANIME, parsed?.mediaType)
        assertNotEquals(
            ExternalMediaRef(MediaSource.TMDB, relation.animeRef.externalId),
            relation.animeRef
        )
        assertTrue(route.contains("JIKAN"))
    }
}
