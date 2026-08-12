package com.cydoniancitizen.bingee.data.notification

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.cydoniancitizen.bingee.MainActivity
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType

data class NotificationNavigationTarget(val mediaType: MediaType, val tmdbId: Long) {
    init {
        require(tmdbId > 0) { "TMDB ID must be positive" }
    }
}

internal object NotificationDetailIntent {
    const val ACTION_OPEN_DETAILS = "com.cydoniancitizen.bingee.action.OPEN_DETAILS"
    private const val EXTRA_LEGACY_SOURCE = "notification_source"
    private const val EXTRA_MEDIA_TYPE = "notification_media_type"
    private const val EXTRA_TMDB_ID = "notification_tmdb_id"
    private const val EXTRA_LEGACY_EXTERNAL_ID = "notification_external_id"

    fun pendingIntent(context: Context, target: NotificationNavigationTarget, requestCode: Int): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode,
            intent(context, target),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    fun intent(context: Context, target: NotificationNavigationTarget): Intent =
        Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_DETAILS
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_MEDIA_TYPE, target.mediaType.name)
            putExtra(EXTRA_TMDB_ID, target.tmdbId)
        }

    fun parse(intent: Intent?): NotificationNavigationTarget? {
        try {
            if (intent?.action != ACTION_OPEN_DETAILS) return null
            val mediaType = intent.getStringExtra(EXTRA_MEDIA_TYPE)
                ?.let { value -> MediaType.entries.firstOrNull { it.name == value } }
                ?: return null
            if (mediaType != MediaType.MOVIE && mediaType != MediaType.SERIES) return null
            val tmdbId = intent.getLongExtra(EXTRA_TMDB_ID, 0).takeIf { it > 0 } ?: run {
                val source = intent.getStringExtra(EXTRA_LEGACY_SOURCE)
                    ?.let { value -> MediaSource.entries.firstOrNull { it.name == value } }
                if (source != MediaSource.TMDB) return null
                intent.getStringExtra(EXTRA_LEGACY_EXTERNAL_ID)
                    ?.takeIf { it.all(Char::isDigit) }
                    ?.toLongOrNull()?.takeIf { it > 0 } ?: return null
            }
            return NotificationNavigationTarget(mediaType, tmdbId)
        } catch (_: RuntimeException) {
            return null
        }
    }
}
