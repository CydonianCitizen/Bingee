package com.cydoniancitizen.bingee.core.navigation

import com.cydoniancitizen.bingee.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TopLevelDestinationTest {
    @Test
    fun routesRemainStableAndOrdered() {
        assertEquals(
            listOf("home", "search", "profile"),
            TopLevelDestination.entries.map(TopLevelDestination::route)
        )
    }

    @Test
    fun personalDestinationPresentsItselfAsYourBingeeOnTheUnchangedProfileRoute() {
        val personal = TopLevelDestination.PROFILE

        // The user-facing vocabulary is the dashboard title; the route stays "profile" so saved
        // navigation state, notification targets, and collection subroutes keep resolving.
        assertEquals(R.string.profile_title_dashboard, personal.labelRes)
        assertEquals("profile", personal.route)
    }

    @Test
    fun routesAreUnique() {
        val routes = TopLevelDestination.entries.map(TopLevelDestination::route)

        assertEquals(routes.size, routes.toSet().size)
        assertTrue(routes.all(String::isNotBlank))
    }

    @Test
    fun routeLookupMapsKnownRoutesAndRejectsUnknownRoutes() {
        assertEquals(
            TopLevelDestination.SEARCH,
            topLevelDestinationForRoute(TopLevelDestination.SEARCH.route)
        )
        assertNull(topLevelDestinationForRoute("details"))
        assertNull(topLevelDestinationForRoute(null))
    }

    @Test
    fun selectedStateMatchesOnlyKnownCurrentRoute() {
        val currentDestination =
            topLevelDestinationForRoute(TopLevelDestination.HOME.route)

        assertEquals(
            TopLevelDestination.HOME,
            currentDestination
        )
        assertTrue(
            TopLevelDestination.entries
                .filterNot { it == TopLevelDestination.HOME }
                .none { it == currentDestination }
        )
        assertNull(topLevelDestinationForRoute("details"))
    }
}
