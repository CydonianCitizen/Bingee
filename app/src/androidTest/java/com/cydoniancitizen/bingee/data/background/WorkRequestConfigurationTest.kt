package com.cydoniancitizen.bingee.data.background

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.BackoffPolicy
import androidx.work.NetworkType
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkRequestConfigurationTest {
    @Test
    fun calendarPeriodicWorkIsDailyConnectedAndExponentiallyBackedOff() {
        val spec = WorkManagerBackgroundWorkScheduler.calendarRefreshRequest().workSpec
        assertEquals(Duration.ofHours(24).toMillis(), spec.intervalDuration)
        assertEquals(NetworkType.CONNECTED, spec.constraints.requiredNetworkType)
        assertEquals(BackoffPolicy.EXPONENTIAL, spec.backoffPolicy)
        assertEquals(Duration.ofMinutes(30).toMillis(), spec.backoffDelayDuration)
        assertFalse(spec.expedited)
    }

    @Test
    fun notificationPeriodicAndImmediateWorkRequireNoNetworkOrExactTiming() {
        val periodic = WorkManagerBackgroundWorkScheduler.notificationEvaluationRequest().workSpec
        val immediate = WorkManagerBackgroundWorkScheduler.immediateNotificationRequest().workSpec
        assertEquals(Duration.ofHours(24).toMillis(), periodic.intervalDuration)
        assertEquals(NetworkType.NOT_REQUIRED, periodic.constraints.requiredNetworkType)
        assertEquals(NetworkType.NOT_REQUIRED, immediate.constraints.requiredNetworkType)
        assertEquals(BackoffPolicy.EXPONENTIAL, periodic.backoffPolicy)
        assertEquals(Duration.ofMinutes(30).toMillis(), immediate.backoffDelayDuration)
        assertFalse(periodic.expedited)
        assertFalse(immediate.expedited)
    }

    @Test
    fun uniqueWorkNamesAndPoliciesRemainStable() {
        assertEquals("calendar_refresh_periodic", WorkManagerBackgroundWorkScheduler.CALENDAR_REFRESH_PERIODIC)
        assertEquals("release_notification_periodic", WorkManagerBackgroundWorkScheduler.RELEASE_NOTIFICATION_PERIODIC)
        assertEquals(
            "release_notification_immediate",
            WorkManagerBackgroundWorkScheduler.RELEASE_NOTIFICATION_IMMEDIATE
        )
    }
}
