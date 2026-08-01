package com.cydoniancitizen.bingee.app

import com.cydoniancitizen.bingee.core.credential.TmdbCredentialStatus
import com.cydoniancitizen.bingee.testutil.FakeCredentialRepository
import com.cydoniancitizen.bingee.testutil.FakeFirstRunPreferences
import com.cydoniancitizen.bingee.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StartupViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun missingCredentialShowsUniqueOnboardingDestination() = runTest(mainDispatcherRule.dispatcher) {
        val repository = FakeCredentialRepository(refreshStatus = TmdbCredentialStatus.NotConfigured)
        val viewModel = StartupViewModel(repository, FakeFirstRunPreferences())

        assertEquals(StartupUiState.Checking, viewModel.uiState.value)
        advanceUntilIdle()

        assertEquals(
            StartupUiState.Ready(StartupDestination.ONBOARDING),
            viewModel.uiState.value
        )
    }

    @Test
    fun validCredentialOrCompletedOnboardingStartsShell() = runTest(mainDispatcherRule.dispatcher) {
        val valid =
            StartupViewModel(
                FakeCredentialRepository(refreshStatus = TmdbCredentialStatus.Valid),
                FakeFirstRunPreferences()
            )
        val offline =
            StartupViewModel(
                FakeCredentialRepository(refreshStatus = TmdbCredentialStatus.NotConfigured),
                FakeFirstRunPreferences(complete = true)
            )

        advanceUntilIdle()

        assertEquals(StartupUiState.Ready(StartupDestination.SHELL), valid.uiState.value)
        assertEquals(StartupUiState.Ready(StartupDestination.SHELL), offline.uiState.value)
    }

    @Test
    fun offlineContinuationUpdatesDestinationWithoutLoop() = runTest(mainDispatcherRule.dispatcher) {
        val preferences = FakeFirstRunPreferences()
        val viewModel =
            StartupViewModel(
                FakeCredentialRepository(refreshStatus = TmdbCredentialStatus.NotConfigured),
                preferences
            )
        advanceUntilIdle()

        viewModel.completeOnboarding()
        advanceUntilIdle()

        assertEquals(StartupUiState.Ready(StartupDestination.SHELL), viewModel.uiState.value)
        assertEquals(1, preferences.markCalls)
    }
}
