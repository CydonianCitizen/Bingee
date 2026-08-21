package com.cydoniancitizen.bingee.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.cydoniancitizen.bingee.core.designsystem.theme.BingeeTheme
import com.cydoniancitizen.bingee.core.navigation.TopLevelDestination
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class BingeeBottomBarTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun personalDestinationIsLabelledYourBingeeAndKeepsTheProfileRoute() {
        val selected = AtomicReference<TopLevelDestination>()
        composeRule.setContent {
            BingeeTheme {
                BingeeBottomBar(
                    currentDestination = TopLevelDestination.HOME,
                    onSelect = selected::set
                )
            }
        }

        composeRule.onNodeWithText("Your Bingee").assertIsDisplayed()
        composeRule.onNodeWithText("Profile").assertDoesNotExist()

        composeRule.onNodeWithText("Your Bingee").performClick()

        // The visible vocabulary changed; the route the destination navigates to did not.
        assertEquals(TopLevelDestination.PROFILE, selected.get())
        assertEquals("profile", selected.get().route)
    }

    @Test
    fun selectedStateFollowsTheCurrentDestination() {
        composeRule.setContent {
            BingeeTheme {
                BingeeBottomBar(
                    currentDestination = TopLevelDestination.PROFILE,
                    onSelect = {}
                )
            }
        }

        composeRule.onNodeWithText("Your Bingee").assertIsSelected()
        composeRule.onNodeWithText("Home").assertIsNotSelected()
        composeRule.onNodeWithText("Search").assertIsNotSelected()
    }
}
