package com.cydoniancitizen.bingee.core.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.cydoniancitizen.bingee.R
import com.cydoniancitizen.bingee.core.designsystem.component.ErrorState
import com.cydoniancitizen.bingee.core.designsystem.theme.BingeeDimensions
import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.data.settings.ProfileCollection
import com.cydoniancitizen.bingee.feature.details.MediaDetailsScreen
import com.cydoniancitizen.bingee.feature.home.HomeScreen
import com.cydoniancitizen.bingee.feature.notifications.NotificationsScreen
import com.cydoniancitizen.bingee.feature.onboarding.OnboardingRoute
import com.cydoniancitizen.bingee.feature.profile.ProfileCollectionShortcut
import com.cydoniancitizen.bingee.feature.profile.ProfileScreen
import com.cydoniancitizen.bingee.feature.profile.StatisticsScreen
import com.cydoniancitizen.bingee.feature.search.SearchScreen
import com.cydoniancitizen.bingee.feature.settings.SettingsIndexScreen
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
                    navController.openDetails(reference, mediaType)
                }
            )
        }
        composable(TopLevelDestination.SEARCH.route) {
            SearchScreen(
                onOpenSettings = onOpenSettings,
                onOpenDetails = { reference, mediaType ->
                    navController.openDetails(reference, mediaType)
                }
            )
        }
        composable(TopLevelDestination.PROFILE.route) {
            ProfileScreen(
                onOpenSettings = { navController.navigate(AppRoute.SETTINGS) },
                onOpenStatistics = { navController.navigate(AppRoute.STATISTICS) },
                onOpenDetails = { reference, mediaType ->
                    navController.openDetails(reference, mediaType)
                },
                onNavigateToSearch = {
                    navController.navigate(TopLevelDestination.SEARCH.route) {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onOpenCollection = { shortcut ->
                    navController.navigate(AppRoute.profileCollection(shortcut.name.lowercase()))
                }
            )
        }
        composable(
            route = AppRoute.PROFILE_COLLECTION,
            arguments = listOf(navArgument("collection") { type = NavType.StringType })
        ) { entry ->
            val shortcut = entry.arguments?.getString("collection")
                ?.let { value -> ProfileCollectionShortcut.entries.firstOrNull { it.name.equals(value, true) } }
            if (shortcut == null) {
                InvalidDetailRoute(onBack = navController::popBackStack)
            } else {
                ProfileScreen(
                    onOpenSettings = { navController.navigate(AppRoute.SETTINGS) },
                    onOpenStatistics = { navController.navigate(AppRoute.STATISTICS) },
                    onOpenDetails = { reference, mediaType -> navController.openDetails(reference, mediaType) },
                    onNavigateToSearch = {
                        navController.navigate(TopLevelDestination.SEARCH.route) {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    collectionFilter = when (shortcut) {
                        ProfileCollectionShortcut.WATCHING -> null
                        ProfileCollectionShortcut.WATCHED -> ProfileCollection.WATCHED
                        ProfileCollectionShortcut.WATCH_LATER -> ProfileCollection.WATCH_LATER
                        ProfileCollectionShortcut.FAVORITES -> ProfileCollection.FAVORITES
                        ProfileCollectionShortcut.ABANDONED -> null
                    },
                    abandonedCollection = shortcut == ProfileCollectionShortcut.ABANDONED,
                    watchingCollection = shortcut == ProfileCollectionShortcut.WATCHING,
                    onNavigateBack = navController::popBackStack
                )
            }
        }
        composable(AppRoute.STATISTICS) { entry ->
            val profileEntry = remember(entry) {
                navController.getBackStackEntry(TopLevelDestination.PROFILE.route)
            }
            StatisticsScreen(
                onBack = navController::popBackStack,
                onOpenDetails = { reference, mediaType ->
                    navController.openDetails(reference, mediaType)
                },
                viewModel = hiltViewModel(profileEntry)
            )
        }
        composable(AppRoute.SETTINGS) {
            SettingsIndexScreen(
                onNavigateToAppearance = { navController.navigate(AppRoute.SETTINGS_APPEARANCE) },
                onNavigateToNotifications = { navController.navigate(AppRoute.SETTINGS_NOTIFICATIONS) },
                onNavigateToDataBackup = { navController.navigate(AppRoute.SETTINGS_DATA_BACKUP) },
                onNavigateToPrivacy = { navController.navigate(AppRoute.SETTINGS_PRIVACY) },
                onNavigateToAbout = { navController.navigate(AppRoute.SETTINGS_ABOUT) }
            )
        }
        composable(AppRoute.SETTINGS_APPEARANCE) {
            com.cydoniancitizen.bingee.feature.settings.AppearanceLanguageSettingsScreen(
                onBack = navController::popBackStack
            )
        }
        composable(AppRoute.SETTINGS_NOTIFICATIONS) {
            com.cydoniancitizen.bingee.feature.settings.NotificationSettingsScreen(onBack = navController::popBackStack)
        }
        composable(AppRoute.SETTINGS_DATA_BACKUP) {
            com.cydoniancitizen.bingee.feature.settings.DataBackupSettingsScreen(
                onBack = navController::popBackStack,
                onOpenTvTimeImport = { navController.navigate(AppRoute.TV_TIME_IMPORT) }
            )
        }
        composable(AppRoute.SETTINGS_PRIVACY) {
            com.cydoniancitizen.bingee.feature.settings.PrivacySettingsScreen(onBack = navController::popBackStack)
        }
        composable(AppRoute.SETTINGS_ABOUT) {
            com.cydoniancitizen.bingee.feature.settings.AboutSettingsScreen(onBack = navController::popBackStack)
        }
        composable(AppRoute.NOTIFICATIONS) {
            NotificationsScreen(
                onBack = navController::popBackStack,
                onOpenDetails = { reference, mediaType ->
                    navController.openDetails(reference, mediaType)
                }
            )
        }
        composable(AppRoute.TV_TIME_IMPORT) {
            TvTimeImportScreen(onBack = navController::popBackStack)
        }
        composable(
            route = DetailRoute.TEMPLATE,
            arguments = listOf(
                navArgument(DetailRoute.MEDIA_TYPE_ARG) { type = NavType.StringType },
                navArgument(DetailRoute.TMDB_ID_ARG) { type = NavType.StringType }
            )
        ) { entry ->
            val args = DetailRoute.parse(
                entry.arguments?.getString(DetailRoute.MEDIA_TYPE_ARG),
                entry.arguments?.getString(DetailRoute.TMDB_ID_ARG)
            )
            if (args == null) {
                InvalidDetailRoute(onBack = navController::popBackStack)
            } else {
                MediaDetailsScreen(
                    onBack = navController::popBackStack,
                    onOpenSettings = onOpenSettings
                )
            }
        }
    }
}

private fun NavHostController.openDetails(reference: ExternalMediaRef, mediaType: MediaType) {
    val tmdbId = reference.takeIf { it.source == MediaSource.TMDB }
        ?.externalId?.toLongOrNull()?.takeIf { it > 0 } ?: return
    navigate(DetailRoute.create(mediaType, tmdbId))
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
