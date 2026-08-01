package com.cydoniancitizen.bingee.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.cydoniancitizen.bingee.feature.home.HomeScreen
import com.cydoniancitizen.bingee.feature.onboarding.OnboardingRoute
import com.cydoniancitizen.bingee.feature.search.SearchScreen
import com.cydoniancitizen.bingee.feature.settings.SettingsScreen

@Composable
fun BingeeNavHost(
    navController: NavHostController,
    startDestination: String,
    onOnboardingFinished: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable(AppRoute.ONBOARDING) {
            OnboardingRoute(
                onConfigured = onOnboardingFinished,
                onContinueOffline = onOnboardingFinished
            )
        }
        composable(TopLevelDestination.HOME.route) {
            HomeScreen()
        }
        composable(TopLevelDestination.SEARCH.route) {
            SearchScreen(onOpenSettings = onOpenSettings)
        }
        composable(TopLevelDestination.SETTINGS.route) {
            SettingsScreen()
        }
    }
}
