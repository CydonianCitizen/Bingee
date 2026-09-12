package com.cydoniancitizen.bingee.app

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
        // Centred: this state is drawn before the shell exists, so nothing insets it from the status bar.
        StartupUiState.Checking -> LoadingState(
            message = stringResource(R.string.startup_checking),
            modifier = Modifier.fillMaxSize().wrapContentSize()
        )
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

    // Wide windows (tablets, landscape, large multi-window panes) move the destinations to a side rail, so
    // content keeps its full height instead of sharing it with a phone-style bottom bar.
    BoxWithConstraints {
        val useRail = maxWidth >= WIDE_WINDOW_MIN_WIDTH
        Row {
            if (useRail && currentDestination != null) {
                BingeeNavigationRail(currentDestination, navController::navigateTopLevel)
            }
            Scaffold(
                modifier = Modifier.weight(1f),
                // The rail already pads the start edge for the cutout and system bars; applying it again here
                // doubles the gap between the rail and the content.
                contentWindowInsets = if (useRail) {
                    ScaffoldDefaults.contentWindowInsets.only(WindowInsetsSides.Vertical + WindowInsetsSides.End)
                } else {
                    ScaffoldDefaults.contentWindowInsets
                },
                bottomBar = {
                    if (!useRail && currentDestination != null) {
                        BingeeBottomBar(
                            currentDestination = currentDestination,
                            onSelect = navController::navigateTopLevel
                        )
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
    }
}

/** Material's medium window size class starts here; below it the bottom bar stays. */
private val WIDE_WINDOW_MIN_WIDTH = 600.dp

@Composable
internal fun BingeeNavigationRail(currentDestination: TopLevelDestination, onSelect: (TopLevelDestination) -> Unit) {
    NavigationRail {
        TopLevelDestination.entries.forEach { destination ->
            NavigationRailItem(
                selected = currentDestination == destination,
                onClick = { onSelect(destination) },
                // The visible label names the destination, so the icon adds nothing to announce.
                icon = { Icon(imageVector = destination.icon, contentDescription = null) },
                label = { Text(stringResource(destination.labelRes)) }
            )
        }
    }
}

@Composable
internal fun BingeeBottomBar(currentDestination: TopLevelDestination, onSelect: (TopLevelDestination) -> Unit) {
    NavigationBar {
        TopLevelDestination.entries.forEach { destination ->
            val label = stringResource(destination.labelRes)
            NavigationBarItem(
                selected = currentDestination == destination,
                onClick = { onSelect(destination) },
                // The visible label names the destination, so the icon adds nothing to announce.
                icon = { Icon(imageVector = destination.icon, contentDescription = null) },
                label = { Text(label) }
            )
        }
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
