package com.cydoniancitizen.bingee.core.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
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
    PROFILE(
        route = "profile",
        labelRes = R.string.nav_profile,
        icon = Icons.Default.Person
    )
}

fun topLevelDestinationForRoute(route: String?): TopLevelDestination? =
    TopLevelDestination.entries.firstOrNull { destination -> destination.route == route }

object AppRoute {
    const val ONBOARDING = "onboarding"
    const val TV_TIME_IMPORT = "tv-time-import"
    const val SETTINGS = "settings"
    const val NOTIFICATIONS = "notifications"
    const val STATISTICS = "statistics"
}
