package com.cydoniancitizen.bingee.data.background

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkInfo
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkSchedulerIntegrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val scheduler = WorkManagerBackgroundWorkScheduler(context)
    private val workManager = WorkManager.getInstance(context)

    @Test
    fun repeatedPeriodicSchedulingLeavesOneActiveWorkPerStableName() {
        scheduler.ensureCalendarRefresh()
        scheduler.ensureCalendarRefresh()
        scheduler.reconcileNotificationWork(true)
        scheduler.reconcileNotificationWork(true)

        assertEquals(1, activeCount(WorkManagerBackgroundWorkScheduler.CALENDAR_REFRESH_PERIODIC))
        assertEquals(1, activeCount(WorkManagerBackgroundWorkScheduler.RELEASE_NOTIFICATION_PERIODIC))

        scheduler.reconcileNotificationWork(false)
        workManager.getWorkInfosForUniqueWork(
            WorkManagerBackgroundWorkScheduler.RELEASE_NOTIFICATION_PERIODIC
        ).get(5, TimeUnit.SECONDS)
    }

    private fun activeCount(name: String): Int = workManager.getWorkInfosForUniqueWork(name).get(5, TimeUnit.SECONDS)
        .count { it.state != WorkInfo.State.CANCELLED }
}
