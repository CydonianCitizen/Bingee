package com.cydoniancitizen.bingee.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.cydoniancitizen.bingee.feature.details.MediaDetailsScreen
import com.cydoniancitizen.bingee.feature.home.HomeScreen
import com.cydoniancitizen.bingee.feature.library.LibraryScreen
import com.cydoniancitizen.bingee.feature.onboarding.OnboardingRoute
import com.cydoniancitizen.bingee.feature.search.SearchScreen
import com.cydoniancitizen.bingee.feature.settings.SettingsScreen
import com.cydoniancitizen.bingee.feature.tvtimeimport.TvTimeImportScreen

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
            HomeScreen(
                onOpenSettings = onOpenSettings,
                onOpenDetails = { reference, mediaType ->
                    navController.navigate(DetailRoute.create(reference, mediaType))
                }
            )
        }
        composable(TopLevelDestination.SEARCH.route) {
            SearchScreen(
                onOpenSettings = onOpenSettings,
                onOpenDetails = { reference, mediaType ->
                    navController.navigate(DetailRoute.create(reference, mediaType))
                }
            )
        }
        composable(TopLevelDestination.LIBRARY.route) {
            LibraryScreen(
                onOpenDetails = { reference, mediaType ->
                    navController.navigate(DetailRoute.create(reference, mediaType))
                }
            )
        }
        composable(TopLevelDestination.SETTINGS.route) {
            SettingsScreen(onOpenTvTimeImport = { navController.navigate(AppRoute.TV_TIME_IMPORT) })
        }
        composable(AppRoute.TV_TIME_IMPORT) {
            TvTimeImportScreen(onBack = navController::popBackStack)
        }
        composable(
            route = DetailRoute.TEMPLATE,
            arguments = listOf(
                navArgument(DetailRoute.SOURCE_ARG) { type = NavType.StringType },
                navArgument(DetailRoute.MEDIA_TYPE_ARG) { type = NavType.StringType },
                navArgument(DetailRoute.EXTERNAL_ID_ARG) { type = NavType.StringType }
            )
        ) {
            MediaDetailsScreen(
                onBack = navController::popBackStack,
                onOpenSettings = onOpenSettings
            )
        }
    }
}
