package com.cydoniancitizen.bingee.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.cydoniancitizen.bingee.R

enum class TopLevelDestination(val route: String, @param:StringRes val labelRes: Int, val icon: ImageVector) {
    HOME(
        route = "home",
        labelRes = R.string.nav_home,
        icon = Icons.Default.Home
    ),
    SEARCH(
        route = "search",
        labelRes = R.string.nav_search,
        icon = Icons.Default.Search
    ),
    SETTINGS(
        route = "settings",
        labelRes = R.string.nav_settings,
        icon = Icons.Default.Settings
    )
}

fun topLevelDestinationForRoute(route: String?): TopLevelDestination? =
    TopLevelDestination.entries.firstOrNull { destination -> destination.route == route }
