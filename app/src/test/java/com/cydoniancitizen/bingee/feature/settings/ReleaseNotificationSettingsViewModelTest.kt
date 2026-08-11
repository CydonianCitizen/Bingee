package com.cydoniancitizen.bingee.feature.settings

import com.cydoniancitizen.bingee.core.model.NotificationCapabilityStatus
import com.cydoniancitizen.bingee.core.model.ReleaseNotificationLeadTime
import com.cydoniancitizen.bingee.core.model.ReleaseNotificationPreferences
import com.cydoniancitizen.bingee.core.result.AppError
import com.cydoniancitizen.bingee.domain.background.BackgroundWorkScheduler
import com.cydoniancitizen.bingee.domain.notification.ReleaseNotificationCapability
import com.cydoniancitizen.bingee.domain.repository.ReleaseNotificationPreferencesRepository
import com.cydoniancitizen.bingee.testutil.MainDispatcherRule
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReleaseNotificationSettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun startupDoesNotCreateChannelOrRequestPermission() = runTest(mainDispatcherRule.dispatcher) {
        val capability = FakeCapability(NotificationCapabilityStatus.PERMISSION_DENIED)
        val viewModel = ReleaseNotificationSettingsViewModel(FakePreferences(), capability, FakeScheduler())
        runCurrent()

        assertFalse(viewModel.uiState.value.preferences.enabled)
        assertEquals(0, capability.ensureCalls)
        assertEquals(NotificationCapabilityStatus.PERMISSION_DENIED, viewModel.uiState.value.capability)
    }

    @Test
    fun explicitEnableRequestsPermissionThenGrantSchedulesBothKindsOfWork() = runTest(mainDispatcherRule.dispatcher) {
        val preferences = FakePreferences()
        val capability = FakeCapability(NotificationCapabilityStatus.PERMISSION_DENIED)
        val scheduler = FakeScheduler()
        val viewModel = ReleaseNotificationSettingsViewModel(preferences, capability, scheduler)
        runCurrent()

        assertTrue(viewModel.onEnableRequested())
        assertEquals(1, capability.ensureCalls)
        assertFalse(preferences.preferences.value.enabled)

        capability.value = NotificationCapabilityStatus.AVAILABLE
        viewModel.onPermissionResult(granted = true, permanentlyDenied = false)
        advanceUntilIdle()

        assertTrue(preferences.preferences.value.enabled)
        assertEquals(1, preferences.writeCount)
        assertEquals(listOf(true), scheduler.notificationReconciliations)
        assertEquals(1, scheduler.immediateCalls)
    }

    @Test
    fun deniedAndPermanentlyBlockedStayDisabledAndOfferSettingsBoundary() = runTest(mainDispatcherRule.dispatcher) {
        val preferences = FakePreferences()
        val capability = FakeCapability(NotificationCapabilityStatus.PERMISSION_DENIED)
        val scheduler = FakeScheduler()
        val viewModel = ReleaseNotificationSettingsViewModel(preferences, capability, scheduler)
        runCurrent()

        viewModel.onPermissionResult(granted = false, permanentlyDenied = true)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.preferences.enabled)
        assertTrue(viewModel.uiState.value.permanentlyDenied)
        assertEquals(listOf(false), scheduler.notificationReconciliations)

        viewModel.openSystemSettings()
        assertEquals(1, capability.settingsCalls)
    }

    @Test
    fun preferenceChangesAndDisableReconcileScheduling() = runTest(mainDispatcherRule.dispatcher) {
        val preferences = FakePreferences(ReleaseNotificationPreferences(enabled = true))
        val capability = FakeCapability(NotificationCapabilityStatus.AVAILABLE)
        val scheduler = FakeScheduler()
        val viewModel = ReleaseNotificationSettingsViewModel(preferences, capability, scheduler)
        runCurrent()

        viewModel.setLeadTime(ReleaseNotificationLeadTime.SEVEN_DAYS)
        advanceUntilIdle()
        assertEquals(ReleaseNotificationLeadTime.SEVEN_DAYS, preferences.preferences.value.leadTime)
        assertEquals(1, scheduler.immediateCalls)

        viewModel.setMovieReleases(false)
        advanceUntilIdle()
        assertFalse(preferences.preferences.value.movieReleases)

        viewModel.disableNotifications()
        advanceUntilIdle()
        assertFalse(preferences.preferences.value.enabled)
        assertEquals(false, scheduler.notificationReconciliations.last())
    }

    @Test
    fun failedPreferenceWriteResetsUpdatingAndCanBeRetried() = runTest(mainDispatcherRule.dispatcher) {
        val preferences = FakePreferences().apply { failure = IllegalStateException("write failed") }
        val viewModel = ReleaseNotificationSettingsViewModel(
            preferences,
            FakeCapability(NotificationCapabilityStatus.PERMISSION_DENIED),
            FakeScheduler()
        )
        runCurrent()

        viewModel.setMovieReleases(false)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isUpdating)
        assertEquals(AppError.LocalStorageFailure, viewModel.uiState.value.error)
        assertTrue(preferences.preferences.value.movieReleases)

        viewModel.clearError()
        preferences.failure = null
        viewModel.setMovieReleases(false)
        advanceUntilIdle()

        assertFalse(preferences.preferences.value.movieReleases)
        assertFalse(viewModel.uiState.value.isUpdating)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun schedulerFailureResetsUpdatingAndSubsequentWriteSucceeds() = runTest(mainDispatcherRule.dispatcher) {
        val preferences = FakePreferences(ReleaseNotificationPreferences(enabled = true))
        val scheduler = FakeScheduler().apply { failure = IllegalStateException("schedule failed") }
        val viewModel = ReleaseNotificationSettingsViewModel(
            preferences,
            FakeCapability(NotificationCapabilityStatus.AVAILABLE),
            scheduler
        )
        runCurrent()

        viewModel.setLeadTime(ReleaseNotificationLeadTime.THREE_DAYS)
        advanceUntilIdle()

        assertEquals(ReleaseNotificationLeadTime.THREE_DAYS, preferences.preferences.value.leadTime)
        assertFalse(viewModel.uiState.value.isUpdating)
        assertEquals(AppError.LocalStorageFailure, viewModel.uiState.value.error)

        scheduler.failure = null
        viewModel.clearError()
        viewModel.setLeadTime(ReleaseNotificationLeadTime.SEVEN_DAYS)
        advanceUntilIdle()

        assertEquals(ReleaseNotificationLeadTime.SEVEN_DAYS, preferences.preferences.value.leadTime)
        assertFalse(viewModel.uiState.value.isUpdating)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun cancellationResetsUpdatingWithoutBecomingAnError() = runTest(mainDispatcherRule.dispatcher) {
        val preferences = FakePreferences().apply { failure = CancellationException("cancelled") }
        val viewModel = ReleaseNotificationSettingsViewModel(
            preferences,
            FakeCapability(NotificationCapabilityStatus.PERMISSION_DENIED),
            FakeScheduler()
        )
        runCurrent()

        viewModel.setMovieReleases(false)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isUpdating)
        assertNull(viewModel.uiState.value.error)
    }

    private class FakePreferences(initial: ReleaseNotificationPreferences = ReleaseNotificationPreferences()) :
        ReleaseNotificationPreferencesRepository {
        override val preferences = MutableStateFlow(initial)
        var failure: Throwable? = null
        var writeCount = 0

        private fun beforeWrite() {
            writeCount++
            failure?.let { throw it }
        }

        override suspend fun setEnabled(enabled: Boolean) {
            beforeWrite()
            preferences.value = preferences.value.copy(enabled = enabled)
        }
        override suspend fun setLeadTime(leadTime: ReleaseNotificationLeadTime) {
            beforeWrite()
            preferences.value = preferences.value.copy(leadTime = leadTime)
        }
        override suspend fun setMovieReleases(enabled: Boolean) {
            beforeWrite()
            preferences.value = preferences.value.copy(movieReleases = enabled)
        }
        override suspend fun setSeasonPremieres(enabled: Boolean) {
            beforeWrite()
            preferences.value = preferences.value.copy(seasonPremieres = enabled)
        }
        override suspend fun setEpisodeAirings(enabled: Boolean) {
            beforeWrite()
            preferences.value = preferences.value.copy(episodeAirings = enabled)
        }
    }

    private class FakeCapability(var value: NotificationCapabilityStatus) : ReleaseNotificationCapability {
        var ensureCalls = 0
        var settingsCalls = 0
        override fun ensureChannel() {
            ensureCalls++
        }
        override fun status(): NotificationCapabilityStatus = value
        override fun openSystemSettings() {
            settingsCalls++
        }
    }

    private class FakeScheduler : BackgroundWorkScheduler {
        val notificationReconciliations = mutableListOf<Boolean>()
        var immediateCalls = 0
        var failure: Throwable? = null

        private fun beforeSchedule() {
            failure?.let { throw it }
        }

        override fun ensureCalendarRefresh() = Unit
        override fun reconcileNotificationWork(enabled: Boolean) {
            beforeSchedule()
            notificationReconciliations += enabled
        }
        override fun enqueueImmediateNotificationEvaluation() {
            beforeSchedule()
            immediateCalls++
        }
    }
}
