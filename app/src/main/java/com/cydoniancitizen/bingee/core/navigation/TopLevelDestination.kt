package com.cydoniancitizen.bingee.core.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
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

    // The route keeps its original name so saved navigation state and deep links stay valid; the
    // destination presents itself as Your Bingee, a personal collection rather than an account.
    PROFILE(
        route = "profile",
        labelRes = R.string.profile_title_dashboard,
        icon = Icons.AutoMirrored.Filled.List
    )
}

fun topLevelDestinationForRoute(route: String?): TopLevelDestination? =
    TopLevelDestination.entries.firstOrNull { destination ->
        route == destination.route ||
            (destination == TopLevelDestination.PROFILE && route?.startsWith("profile/") == true)
    }

object AppRoute {
    const val ONBOARDING = "onboarding"
    const val TV_TIME_IMPORT = "tv-time-import"
    const val SETTINGS = "settings"
    const val SETTINGS_APPEARANCE = "settings/appearance"
    const val SETTINGS_NOTIFICATIONS = "settings/notifications"
    const val SETTINGS_DATA_BACKUP = "settings/data-backup"
    const val SETTINGS_PRIVACY = "settings/privacy"
    const val SETTINGS_ABOUT = "settings/about"
    const val NOTIFICATIONS = "notifications"
    const val STATISTICS = "statistics"
    const val PROFILE_COLLECTION = "profile/collection/{collection}"

    fun profileCollection(collection: String): String = "profile/collection/$collection"
}
