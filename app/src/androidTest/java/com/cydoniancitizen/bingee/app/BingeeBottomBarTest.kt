package com.cydoniancitizen.bingee.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.WindowInsets
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.WindowInsetsCompat
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

    @Test
    fun wideWindowRailOffersTheSameDestinations() {
        val selected = AtomicReference<TopLevelDestination>()
        composeRule.setContent {
            BingeeTheme {
                BingeeNavigationRail(currentDestination = TopLevelDestination.HOME, onSelect = selected::set)
            }
        }

        composeRule.onNodeWithText("Home").assertIsSelected()
        composeRule.onNodeWithText("Your Bingee").performClick()
        assertEquals(TopLevelDestination.PROFILE, selected.get())
    }

    @Test
    fun wideWindowWithoutRailKeepsTheStartInsetForDetailsAndSettings() {
        val inset = setShell(width = 700.dp, destination = null)

        composeRule.onNodeWithText("Home").assertDoesNotExist()
        assertEquals(inset.value, contentLeft().value, TOLERANCE)
    }

    @Test
    fun wideWindowRailPadsTheStartInsetOnce() {
        setShell(width = 700.dp, destination = TopLevelDestination.HOME)

        // The rail absorbs the cutout; the content starts where the rail ends, with no second inset.
        val railEnd = composeRule.onNodeWithText("Home").getUnclippedBoundsInRoot().right
        assertEquals(railEnd.value, contentLeft().value, TOLERANCE)
    }

    @Test
    fun compactWindowKeepsTheStartInsetBesideTheBottomBar() {
        val inset = setShell(width = 400.dp, destination = TopLevelDestination.HOME)

        composeRule.onNodeWithText("Your Bingee").assertIsDisplayed()
        assertEquals(inset.value, contentLeft().value, TOLERANCE)
    }

    /** Renders the shell at [width] behind a start-edge cutout and returns that cutout in dp. */
    private fun setShell(width: Dp, destination: TopLevelDestination?): Dp {
        var inset = 0.dp
        composeRule.setContent {
            inset = with(LocalDensity.current) { CUTOUT_PX.toDp() }
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.WindowInsets(
                    WindowInsetsCompat.Builder()
                        .setInsets(WindowInsetsCompat.Type.displayCutout(), Insets.of(CUTOUT_PX, 0, 0, 0))
                        .build()
                )
            ) {
                BingeeTheme {
                    Box(Modifier.wrapContentWidth(Alignment.Start, unbounded = true).requiredWidth(width)) {
                        BingeeShell(destination, onSelect = {}) { padding ->
                            Box(Modifier.padding(padding).fillMaxSize().testTag(CONTENT_TAG))
                        }
                    }
                }
            }
        }
        composeRule.waitForIdle()
        return inset
    }

    private fun contentLeft(): Dp = composeRule.onNodeWithTag(CONTENT_TAG).getUnclippedBoundsInRoot().left

    private companion object {
        const val CUTOUT_PX = 105
        const val CONTENT_TAG = "shell-content"
        const val TOLERANCE = 0.5f
    }
}
