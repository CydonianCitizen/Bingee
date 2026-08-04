package com.cydoniancitizen.bingee.app

import com.cydoniancitizen.bingee.core.model.NotificationCapabilityStatus
import com.cydoniancitizen.bingee.domain.background.BackgroundWorkScheduler
import com.cydoniancitizen.bingee.domain.notification.ReleaseNotificationCapability
import com.cydoniancitizen.bingee.domain.repository.ReleaseNotificationPreferencesRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

@Singleton
internal class StartupWorkCoordinator @Inject constructor(
    private val scheduler: BackgroundWorkScheduler,
    private val preferencesRepository: ReleaseNotificationPreferencesRepository,
    private val notificationCapability: ReleaseNotificationCapability
) {
    suspend fun reconcile() {
        try {
            scheduler.ensureCalendarRefresh()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return
        }
        val enabled = try {
            preferencesRepository.preferences.first().enabled
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        }
        try {
            if (enabled) notificationCapability.ensureChannel()
            scheduler.reconcileNotificationWork(
                enabled && notificationCapability.status() == NotificationCapabilityStatus.AVAILABLE
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // WorkManager reconciles again on the next ordinary app startup.
        }
    }
}
