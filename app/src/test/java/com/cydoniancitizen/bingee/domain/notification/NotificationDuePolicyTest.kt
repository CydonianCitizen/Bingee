package com.cydoniancitizen.bingee.domain.notification

import com.cydoniancitizen.bingee.core.model.ReleaseNotificationLeadTime
import java.time.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationDuePolicyTest {
    private val today = LocalDate.of(2026, 8, 4)

    @Test
    fun everyLeadTimeStartsOnTargetAndRemainsDueThroughEventDate() {
        ReleaseNotificationLeadTime.entries.forEach { lead ->
            val eventDate = today.plusDays(lead.days.toLong())
            assertTrue(isNotificationDue(eventDate, today, lead))
            assertTrue(isNotificationDue(eventDate, eventDate, lead))
            assertFalse(isNotificationDue(eventDate, today.minusDays(1), lead))
            assertFalse(isNotificationDue(eventDate, eventDate.plusDays(1), lead))
        }
    }

    @Test
    fun delayedWorkerCanNotifyBeforeOrOnEventButNeverAfter() {
        val eventDate = today.plusDays(7)
        assertTrue(isNotificationDue(eventDate, today.plusDays(4), ReleaseNotificationLeadTime.SEVEN_DAYS))
        assertTrue(isNotificationDue(eventDate, eventDate, ReleaseNotificationLeadTime.SEVEN_DAYS))
        assertFalse(isNotificationDue(eventDate, eventDate.plusDays(1), ReleaseNotificationLeadTime.SEVEN_DAYS))
        assertFalse(isNotificationDue(eventDate, today, ReleaseNotificationLeadTime.THREE_DAYS))
    }
}
