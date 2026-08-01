package com.cydoniancitizen.bingee.core.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TopLevelDestinationTest {
    @Test
    fun routesRemainStableAndOrdered() {
        assertEquals(
            listOf("home", "search", "settings"),
            TopLevelDestination.entries.map(TopLevelDestination::route)
        )
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
