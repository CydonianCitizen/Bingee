package com.cydoniancitizen.bingee.domain.notification

import com.cydoniancitizen.bingee.core.model.ReleaseNotificationLeadTime
import java.time.LocalDate

fun isNotificationDue(eventDate: LocalDate, today: LocalDate, leadTime: ReleaseNotificationLeadTime): Boolean {
    val targetDate = eventDate.minusDays(leadTime.days.toLong())
    return !targetDate.isAfter(today) && !today.isAfter(eventDate)
}
