package com.cydoniancitizen.bingee.data.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.cydoniancitizen.bingee.R
import com.cydoniancitizen.bingee.core.model.NotificationCapabilityStatus
import com.cydoniancitizen.bingee.domain.notification.ReleaseNotificationCapability
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class AndroidReleaseNotificationCapability @Inject constructor(
    @param:ApplicationContext private val context: Context
) : ReleaseNotificationCapability {
    private val manager: NotificationManager
        get() = context.getSystemService(NotificationManager::class.java)

    override fun ensureChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.notification_channel_description)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
        }
        manager.createNotificationChannel(channel)
    }

    override fun status(): NotificationCapabilityStatus {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return NotificationCapabilityStatus.PERMISSION_DENIED
        }
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            return NotificationCapabilityStatus.SYSTEM_BLOCKED
        }
        val channel = manager.getNotificationChannel(CHANNEL_ID)
        if (channel?.importance == NotificationManager.IMPORTANCE_NONE) {
            return NotificationCapabilityStatus.CHANNEL_BLOCKED
        }
        return NotificationCapabilityStatus.AVAILABLE
    }

    override fun openSystemSettings() {
        context.startActivity(
            settingsIntent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    internal companion object {
        const val CHANNEL_ID = "release_updates"

        fun settingsIntent(context: Context): Intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
    }
}
