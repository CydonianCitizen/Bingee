package com.cydoniancitizen.bingee.data.notification

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.cydoniancitizen.bingee.R
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.ReleaseEvent
import com.cydoniancitizen.bingee.core.result.AppError
import com.cydoniancitizen.bingee.core.result.AppResult
import com.cydoniancitizen.bingee.domain.notification.ReleaseNotificationCapability
import com.cydoniancitizen.bingee.domain.notification.ReleaseNotificationContent
import com.cydoniancitizen.bingee.domain.notification.ReleaseNotifier
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class AndroidReleaseNotifier @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val capability: ReleaseNotificationCapability
) : ReleaseNotifier {
    override fun post(event: ReleaseEvent, notificationId: Int, content: ReleaseNotificationContent): AppResult<Unit> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return AppResult.Failure(AppError.NotificationDeliveryFailure)
        }
        return try {
            capability.ensureChannel()
            val pendingIntent = NotificationDetailIntent.pendingIntent(
                context = context,
                target = NotificationNavigationTarget(
                    mediaType = event.mediaType,
                    tmdbId = event.mediaRef.takeIf { it.source == MediaSource.TMDB }
                        ?.externalId?.toLongOrNull()?.takeIf { it > 0 }
                        ?: return AppResult.Failure(AppError.InvalidInput)
                ),
                requestCode = notificationId
            )
            val notification = NotificationCompat.Builder(
                context,
                AndroidReleaseNotificationCapability.CHANNEL_ID
            )
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(content.title)
                .setContentText(content.body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(content.body))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setOnlyAlertOnce(true)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()
            NotificationManagerCompat.from(context).notify(notificationId, notification)
            AppResult.Success(Unit)
        } catch (_: SecurityException) {
            AppResult.Failure(AppError.NotificationDeliveryFailure)
        } catch (_: RuntimeException) {
            AppResult.Failure(AppError.NotificationDeliveryFailure)
        }
    }
}
