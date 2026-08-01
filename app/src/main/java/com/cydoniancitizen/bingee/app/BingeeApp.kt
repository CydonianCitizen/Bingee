package com.cydoniancitizen.bingee.app

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.cydoniancitizen.bingee.core.navigation.TopLevelDestination
import com.cydoniancitizen.bingee.core.navigation.topLevelDestinationForRoute

@Composable
internal fun BingeeApp(startupViewModel: StartupViewModel = hiltViewModel()) {
    val startupState by startupViewModel.uiState.collectAsStateWithLifecycle()
    when (val state = startupState) {
        StartupUiState.Checking -> LoadingState(message = stringResource(R.string.startup_checking))
        is StartupUiState.Ready ->
            BingeeNavigation(
                startDestination = startRouteFor(state.destination),
                onOnboardingComplete = startupViewModel::completeOnboarding
            )
    }
}

@Composable
private fun BingeeNavigation(
    startDestination: String,
    onOnboardingComplete: () -> Unit,
    navController: NavHostController = rememberNavController()
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = topLevelDestinationForRoute(backStackEntry?.destination?.route)

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
            onOpenSettings = { navController.navigateTopLevel(TopLevelDestination.SETTINGS) },
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
