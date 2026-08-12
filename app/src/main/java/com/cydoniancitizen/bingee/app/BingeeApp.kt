package com.cydoniancitizen.bingee.app

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.cydoniancitizen.bingee.R
import com.cydoniancitizen.bingee.core.designsystem.component.LoadingState
import com.cydoniancitizen.bingee.core.navigation.AppRoute
import com.cydoniancitizen.bingee.core.navigation.BingeeNavHost
import com.cydoniancitizen.bingee.core.navigation.DetailRoute
import com.cydoniancitizen.bingee.core.navigation.TopLevelDestination
import com.cydoniancitizen.bingee.core.navigation.topLevelDestinationForRoute
import com.cydoniancitizen.bingee.data.notification.NotificationNavigationTarget
import kotlinx.coroutines.flow.StateFlow

@Composable
internal fun BingeeApp(
    startupViewModel: StartupViewModel = hiltViewModel(),
    notificationTarget: StateFlow<NotificationNavigationTarget?>? = null,
    onNotificationTargetConsumed: () -> Unit = {}
) {
    val startupState by startupViewModel.uiState.collectAsStateWithLifecycle()
    val pendingTarget by notificationTarget
        ?.collectAsStateWithLifecycle()
        ?: androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(null) }
    when (val state = startupState) {
        StartupUiState.Checking -> LoadingState(message = stringResource(R.string.startup_checking))
        is StartupUiState.Ready ->
            BingeeNavigation(
                startDestination = startRouteFor(state.destination),
                onOnboardingComplete = startupViewModel::completeOnboarding,
                notificationTarget = pendingTarget,
                onNotificationTargetConsumed = onNotificationTargetConsumed
            )
    }
}

@Composable
private fun BingeeNavigation(
    startDestination: String,
    onOnboardingComplete: () -> Unit,
    notificationTarget: NotificationNavigationTarget?,
    onNotificationTargetConsumed: () -> Unit,
    navController: NavHostController = rememberNavController()
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = topLevelDestinationForRoute(backStackEntry?.destination?.route)

    LaunchedEffect(startDestination, notificationTarget) {
        val target = notificationTarget ?: return@LaunchedEffect
        if (startDestination == AppRoute.ONBOARDING) return@LaunchedEffect
        navController.navigate(DetailRoute.create(target.mediaType, target.tmdbId)) {
            launchSingleTop = true
        }
        onNotificationTargetConsumed()
    }

    Scaffold(
        bottomBar = {
            if (currentDestination != null) {
                NavigationBar {
                    TopLevelDestination.entries.forEach { destination ->
                        val label = stringResource(destination.labelRes)
                        NavigationBarItem(
                            selected = currentDestination == destination,
                            onClick = { navController.navigateTopLevel(destination) },
                            icon = {
                                Icon(
                                    imageVector = destination.icon,
                                    contentDescription = null
                                )
                            },
                            label = { Text(label) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        BingeeNavHost(
            navController = navController,
            startDestination = startDestination,
            onOnboardingFinished = {
                onOnboardingComplete()
                navController.navigate(TopLevelDestination.HOME.route) {
                    popUpTo(AppRoute.ONBOARDING) { inclusive = true }
                    launchSingleTop = true
                }
            },
            onOpenSettings = { navController.navigate(AppRoute.SETTINGS) },
            modifier = Modifier.padding(innerPadding)
        )
    }
}

internal fun startRouteFor(destination: StartupDestination): String = when (destination) {
    StartupDestination.ONBOARDING -> AppRoute.ONBOARDING
    StartupDestination.SHELL -> TopLevelDestination.HOME.route
}

private fun NavHostController.navigateTopLevel(destination: TopLevelDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}
