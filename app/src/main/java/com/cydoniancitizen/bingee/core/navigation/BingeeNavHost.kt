package com.cydoniancitizen.bingee.core.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.cydoniancitizen.bingee.R
import com.cydoniancitizen.bingee.core.designsystem.component.ErrorState
import com.cydoniancitizen.bingee.core.designsystem.theme.BingeeDimensions
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.feature.details.AnimeDetailsScreen
import com.cydoniancitizen.bingee.feature.details.MediaDetailsScreen
import com.cydoniancitizen.bingee.feature.home.HomeScreen
import com.cydoniancitizen.bingee.feature.notifications.NotificationsScreen
import com.cydoniancitizen.bingee.feature.onboarding.OnboardingRoute
import com.cydoniancitizen.bingee.feature.profile.ProfileScreen
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
                onOpenNotifications = { navController.navigate(AppRoute.NOTIFICATIONS) },
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
        composable(TopLevelDestination.PROFILE.route) {
            ProfileScreen(
                onOpenSettings = { navController.navigate(AppRoute.SETTINGS) },
                onOpenStatistics = { navController.navigate(AppRoute.STATISTICS) },
                onOpenDetails = { reference, mediaType ->
                    navController.navigate(DetailRoute.create(reference, mediaType))
                },
                onNavigateToSearch = {
                    navController.navigate(TopLevelDestination.SEARCH.route) {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
        composable(AppRoute.STATISTICS) {
            com.cydoniancitizen.bingee.feature.profile.StatisticsScreen(
                onBack = navController::popBackStack,
                onOpenDetails = { reference, mediaType ->
                    navController.navigate(DetailRoute.create(reference, mediaType))
                }
            )
        }
        composable(AppRoute.SETTINGS) {
            SettingsScreen(onOpenTvTimeImport = { navController.navigate(AppRoute.TV_TIME_IMPORT) })
        }
        composable(AppRoute.NOTIFICATIONS) {
            NotificationsScreen(onBack = navController::popBackStack)
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
        ) { entry ->
            val args = DetailRoute.parse(
                entry.arguments?.getString(DetailRoute.SOURCE_ARG),
                entry.arguments?.getString(DetailRoute.MEDIA_TYPE_ARG),
                entry.arguments?.getString(DetailRoute.EXTERNAL_ID_ARG)
            )
            when {
                args == null -> InvalidDetailRoute(onBack = navController::popBackStack)
                args.reference.source == MediaSource.JIKAN -> {
                    AnimeDetailsScreen(
                        onBack = navController::popBackStack,
                        onOpenRelated = { relation ->
                            navController.navigate(DetailRoute.create(relation.animeRef, MediaType.ANIME))
                        }
                    )
                }
                else -> {
                    MediaDetailsScreen(
                        onBack = navController::popBackStack,
                        onOpenSettings = onOpenSettings
                    )
                }
            }
        }
    }
}

@Composable
private fun InvalidDetailRoute(onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(BingeeDimensions.screenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(BingeeDimensions.elementSpacing)
    ) {
        ErrorState(
            title = stringResource(R.string.detail_error_title),
            message = stringResource(R.string.error_invalid_input)
        )
        Button(onClick = onBack) {
            Text(stringResource(R.string.detail_back))
        }
    }
}
