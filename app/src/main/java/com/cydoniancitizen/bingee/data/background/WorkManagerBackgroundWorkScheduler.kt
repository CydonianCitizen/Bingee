package com.cydoniancitizen.bingee.data.background

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.cydoniancitizen.bingee.domain.background.BackgroundWorkScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class WorkManagerBackgroundWorkScheduler @Inject constructor(@ApplicationContext context: Context) :
    BackgroundWorkScheduler {
    private val workManager = WorkManager.getInstance(context)

    override fun ensureCalendarRefresh() {
        workManager.enqueueUniquePeriodicWork(
            CALENDAR_REFRESH_PERIODIC,
            ExistingPeriodicWorkPolicy.UPDATE,
            calendarRefreshRequest()
        )
    }

    override fun reconcileNotificationWork(enabled: Boolean) {
        if (enabled) {
            workManager.enqueueUniquePeriodicWork(
                RELEASE_NOTIFICATION_PERIODIC,
                ExistingPeriodicWorkPolicy.UPDATE,
                notificationEvaluationRequest()
            )
        } else {
            workManager.cancelUniqueWork(RELEASE_NOTIFICATION_PERIODIC)
            workManager.cancelUniqueWork(RELEASE_NOTIFICATION_IMMEDIATE)
        }
    }

    override fun enqueueImmediateNotificationEvaluation() {
        workManager.enqueueUniqueWork(
            RELEASE_NOTIFICATION_IMMEDIATE,
            ExistingWorkPolicy.KEEP,
            immediateNotificationRequest()
        )
    }

    internal companion object {
        const val CALENDAR_REFRESH_PERIODIC = "calendar_refresh_periodic"
        const val RELEASE_NOTIFICATION_PERIODIC = "release_notification_periodic"
        const val RELEASE_NOTIFICATION_IMMEDIATE = "release_notification_immediate"
        val PERIODIC_INTERVAL: Duration = Duration.ofHours(24)
        val INITIAL_BACKOFF: Duration = Duration.ofMinutes(30)

        fun calendarRefreshRequest(): PeriodicWorkRequest =
            PeriodicWorkRequestBuilder<CalendarRefreshWorker>(PERIODIC_INTERVAL)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, INITIAL_BACKOFF)
                .build()

        fun notificationEvaluationRequest(): PeriodicWorkRequest =
            PeriodicWorkRequestBuilder<NotificationEvaluationWorker>(PERIODIC_INTERVAL)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, INITIAL_BACKOFF)
                .build()

        fun immediateNotificationRequest(): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<NotificationEvaluationWorker>()
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, INITIAL_BACKOFF)
                .build()
    }
}
