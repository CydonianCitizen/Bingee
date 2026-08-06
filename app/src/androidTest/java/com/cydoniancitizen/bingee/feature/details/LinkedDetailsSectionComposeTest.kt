package com.cydoniancitizen.bingee.feature.details

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.cydoniancitizen.bingee.core.model.LinkedMediaIdentity
import com.cydoniancitizen.bingee.core.model.MediaLinkGroup
import com.cydoniancitizen.bingee.core.model.MediaLinkGroupId
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import java.time.Instant
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LinkedDetailsSectionComposeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val tmdbIdentity = LinkedMediaIdentity(MediaSource.TMDB, MediaType.MOVIE, "550")
    private val jikanIdentity = LinkedMediaIdentity(MediaSource.JIKAN, MediaType.ANIME, "199")

    @Test
    fun linkedSectionCard_displaysLinkedStatusAndTriggersActions() {
        val linkGroup = MediaLinkGroup(
            groupId = MediaLinkGroupId("test-group-123"),
            first = tmdbIdentity,
            second = jikanIdentity,
            preferredPresentation = tmdbIdentity,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )

        var openOtherCalled = false
        var unlinkCalled = false

        composeTestRule.setContent {
            LinkedSectionCard(
                linkGroup = linkGroup,
                currentIdentity = tmdbIdentity,
                onOpenOtherMember = { openOtherCalled = true },
                onChangePreferred = {},
                onUnlink = { unlinkCalled = true }
            )
        }

        composeTestRule.onNodeWithText("Linked versions").assertIsDisplayed()
        composeTestRule.onNodeWithText("This version is set as your preferred presentation.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Open Jikan (Anime)").assertIsDisplayed()

        composeTestRule.onNodeWithText("Open Jikan (Anime)").performClick()
        assertTrue(openOtherCalled)

        composeTestRule.onNodeWithText("Separate versions").performClick()
        composeTestRule.onNodeWithText(
            "Separating these versions removes only the link. Your Library entries, progress, and ratings remain unchanged."
        ).assertIsDisplayed()

        composeTestRule.onAllNodesWithText("Separate versions")[1].performClick()
        composeTestRule.waitForIdle()

        assertTrue(unlinkCalled)
    }
}
