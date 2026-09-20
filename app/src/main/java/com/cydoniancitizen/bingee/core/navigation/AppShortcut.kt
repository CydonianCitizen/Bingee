package com.cydoniancitizen.bingee.core.navigation

import android.content.Intent

/** Launcher shortcuts declared in `res/xml/shortcuts.xml`; each names its destination in one extra. */
internal object AppShortcut {
    const val ACTION_OPEN = "com.cydoniancitizen.bingee.action.OPEN_SHORTCUT"
    const val EXTRA_DESTINATION = "destination"

    fun route(intent: Intent?): String? {
        if (intent?.action != ACTION_OPEN) return null
        return when (intent.getStringExtra(EXTRA_DESTINATION)) {
            "search" -> TopLevelDestination.SEARCH.route
            "watching" -> AppRoute.profileCollection("watching")
            "watch_later" -> AppRoute.profileCollection("watch_later")
            else -> null
        }
    }
}
