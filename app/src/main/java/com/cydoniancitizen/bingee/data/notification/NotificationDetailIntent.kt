package com.cydoniancitizen.bingee.data.notification

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.cydoniancitizen.bingee.MainActivity
import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType

data class NotificationNavigationTarget(val reference: ExternalMediaRef, val mediaType: MediaType)

internal object NotificationDetailIntent {
    const val ACTION_OPEN_DETAILS = "com.cydoniancitizen.bingee.action.OPEN_DETAILS"
    private const val EXTRA_SOURCE = "notification_source"
    private const val EXTRA_MEDIA_TYPE = "notification_media_type"
    private const val EXTRA_EXTERNAL_ID = "notification_external_id"

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
            putExtra(EXTRA_SOURCE, target.reference.source.name)
            putExtra(EXTRA_MEDIA_TYPE, target.mediaType.name)
            putExtra(EXTRA_EXTERNAL_ID, target.reference.externalId)
        }

    fun parse(intent: Intent?): NotificationNavigationTarget? {
        if (intent?.action != ACTION_OPEN_DETAILS) return null
        val source = intent.getStringExtra(EXTRA_SOURCE)
            ?.let { value -> MediaSource.entries.firstOrNull { it.name == value } }
            ?: return null
        val mediaType = intent.getStringExtra(EXTRA_MEDIA_TYPE)
            ?.let { value -> MediaType.entries.firstOrNull { it.name == value } }
            ?: return null
        if (source != MediaSource.TMDB) return null
        if (mediaType != MediaType.MOVIE && mediaType != MediaType.SERIES) return null
        val externalId = intent.getStringExtra(EXTRA_EXTERNAL_ID)
            ?.takeIf(String::isNotEmpty)
            ?: return null
        if (!externalId.all { it in '0'..'9' } || externalId.toLongOrNull()?.let { it > 0 } != true) return null
        return NotificationNavigationTarget(ExternalMediaRef(source, externalId), mediaType)
    }
}
