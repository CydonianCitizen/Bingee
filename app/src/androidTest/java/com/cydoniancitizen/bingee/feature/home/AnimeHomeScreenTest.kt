package com.cydoniancitizen.bingee.feature.home

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.cydoniancitizen.bingee.core.designsystem.theme.BingeeTheme
import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.core.model.ReleaseDateCategory
import com.cydoniancitizen.bingee.core.model.groupReleaseEvents
import com.cydoniancitizen.bingee.debug.FakeAnimeData
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AnimeHomeScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun animePremiereHasStableSameDateLabelAndOpensAnimeDetailsWithoutNotificationAction() {
        val today = LocalDate.of(2026, 8, 3)
        val second = FakeAnimeData.animePremiere.copy(
            mediaRef = ExternalMediaRef(MediaSource.JIKAN, "52992"),
            subject = FakeAnimeData.animePremiere.subject.copy(externalId = "52992"),
            title = "Another Synthetic Anime Premiere"
        )
        val groups = groupReleaseEvents(listOf(second, FakeAnimeData.animePremiere), today)
        val opened = AtomicReference<Pair<ExternalMediaRef, MediaType>>()
        setHome(
            HomeUiState(
                content = HomeContentState.Events(groups),
                today = today
            ),
            onOpenDetails = { ref, type -> opened.set(ref to type) }
        )

        composeRule.onNodeWithText("Release calendar")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        assertEquals(1, groups.size)
        assertEquals(ReleaseDateCategory.UPCOMING, groups.single().category)
        assertEquals(
            listOf("Another Synthetic Anime Premiere", FakeAnimeData.longEnglishTitle),
            groups.single().events.map { it.title }
        )
        assertEquals(2, composeRule.onAllNodesWithText("Anime premiere").fetchSemanticsNodes().size)
        assertEquals(2, composeRule.onAllNodesWithText("Upcoming release").fetchSemanticsNodes().size)
        composeRule.onNodeWithText(FakeAnimeData.longEnglishTitle)
            .performScrollTo().performClick()
        composeRule.onNodeWithContentDescription(
            "Open title details for ${FakeAnimeData.longEnglishTitle}"
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Remind me").assertDoesNotExist()

        assertEquals(FakeAnimeData.animePremiere.mediaRef to MediaType.ANIME, opened.get())
    }

    private fun setHome(state: HomeUiState, onOpenDetails: (ExternalMediaRef, MediaType) -> Unit = { _, _ -> }) {
        composeRule.setContent {
            BingeeTheme {
                HomeContent(
                    state = state,
                    onRefresh = {},
                    onRetryLocal = {},
                    onDismissFeedback = {},
                    onOpenSettings = {},
                    onOpenDetails = onOpenDetails
                )
            }
        }
    }
}
