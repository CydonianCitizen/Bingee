package com.cydoniancitizen.bingee.data.notification

import com.cydoniancitizen.bingee.core.model.NotificationCapabilityStatus
import com.cydoniancitizen.bingee.core.model.NotificationDelivery
import com.cydoniancitizen.bingee.core.model.NotificationDeliveryIdentity
import com.cydoniancitizen.bingee.core.model.NotificationDispatchSummary
import com.cydoniancitizen.bingee.core.result.AppResult
import com.cydoniancitizen.bingee.domain.notification.NotificationDispatchCoordinator
import com.cydoniancitizen.bingee.domain.notification.ReleaseNotificationCapability
import com.cydoniancitizen.bingee.domain.notification.ReleaseNotificationContentMapper
import com.cydoniancitizen.bingee.domain.notification.ReleaseNotifier
import com.cydoniancitizen.bingee.domain.notification.isNotificationDue
import com.cydoniancitizen.bingee.domain.repository.NotificationDeliveryRepository
import com.cydoniancitizen.bingee.domain.repository.ReleaseCalendarRepository
import com.cydoniancitizen.bingee.domain.repository.ReleaseNotificationPreferencesRepository
import java.time.Clock
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

@Singleton
internal class DefaultNotificationDispatchCoordinator @Inject constructor(
    private val preferencesRepository: ReleaseNotificationPreferencesRepository,
    private val capability: ReleaseNotificationCapability,
    private val calendarRepository: ReleaseCalendarRepository,
    private val deliveryRepository: NotificationDeliveryRepository,
    private val contentMapper: ReleaseNotificationContentMapper,
    private val notifier: ReleaseNotifier,
    private val clock: Clock
) : NotificationDispatchCoordinator {
    override suspend fun dispatch(): NotificationDispatchSummary {
        val preferences = try {
            preferencesRepository.preferences.first()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return NotificationDispatchSummary(failed = 1, transientFailure = true)
        }
        if (!preferences.enabled) return NotificationDispatchSummary()

        val capabilityStatus = capability.status()
        if (capabilityStatus != NotificationCapabilityStatus.AVAILABLE) {
            return NotificationDispatchSummary(capability = capabilityStatus)
        }

        val today = LocalDate.now(clock)
        if (deliveryRepository.prune(today.minusDays(RETENTION_DAYS.toLong())) is AppResult.Failure) {
            return NotificationDispatchSummary(failed = 1, transientFailure = true)
        }
        val events = when (
            val result = calendarRepository.getEvents(
                fromDate = today,
                throughDate = today.plusDays(MAX_LEAD_DAYS.toLong()),
                limit = MAX_CANDIDATES
            )
        ) {
            is AppResult.Success -> result.value
            is AppResult.Failure ->
                return NotificationDispatchSummary(failed = 1, transientFailure = true)
        }

        var posted = 0
        var delivered = 0
        var skippedCategory = 0
        var failed = 0
        var transientFailure = false
        for (event in events) {
            if (!preferences.includes(event.subject.eventType)) {
                skippedCategory++
                continue
            }
            if (!isNotificationDue(event.eventDate, today, preferences.leadTime)) continue

            val identity = NotificationDeliveryIdentity(
                source = event.subject.source,
                subjectType = event.subject.subjectType,
                subjectExternalId = event.subject.externalId,
                eventType = event.subject.eventType,
                eventDate = event.eventDate,
                leadDays = preferences.leadTime.days
            )
            when (val contains = deliveryRepository.contains(identity)) {
                is AppResult.Success -> if (contains.value) {
                    delivered++
                    continue
                }
                is AppResult.Failure -> {
                    failed++
                    transientFailure = true
                    continue
                }
            }

            val notificationId = deterministicNotificationId(identity)
            val content = contentMapper.map(
                event,
                ChronoUnit.DAYS.between(today, event.eventDate).toInt()
            )
            if (notifier.post(event, notificationId, content) is AppResult.Failure) {
                failed++
                transientFailure = true
                continue
            }
            posted++
            val record = deliveryRepository.record(
                NotificationDelivery(identity, notificationId, clock.instant())
            )
            if (record is AppResult.Failure) {
                failed++
                transientFailure = true
            }
        }
        return NotificationDispatchSummary(
            candidates = events.size,
            posted = posted,
            alreadyDelivered = delivered,
            skippedByCategory = skippedCategory,
            failed = failed,
            capability = capabilityStatus,
            transientFailure = transientFailure
        )
    }

    internal companion object {
        const val MAX_LEAD_DAYS = 7
        const val MAX_CANDIDATES = 200
        const val RETENTION_DAYS = 30
    }
}
