package com.cydoniancitizen.bingee.app

import com.cydoniancitizen.bingee.core.navigation.AppRoute
import com.cydoniancitizen.bingee.core.navigation.TopLevelDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class StartupNavigationTest {
    @Test
    fun onboardingRouteIsUniqueAndOutsideBottomNavigation() {
        val topLevelRoutes = TopLevelDestination.entries.map(TopLevelDestination::route)

        assertFalse(AppRoute.ONBOARDING in topLevelRoutes)
        assertEquals(topLevelRoutes.size + 1, (topLevelRoutes + AppRoute.ONBOARDING).toSet().size)
    }

    @Test
    fun startupAndOfflineDestinationsAreDeterministic() {
        assertEquals(AppRoute.ONBOARDING, startRouteFor(StartupDestination.ONBOARDING))
        assertEquals(
            TopLevelDestination.HOME.route,
            startRouteFor(StartupDestination.SHELL)
        )
    }
}
