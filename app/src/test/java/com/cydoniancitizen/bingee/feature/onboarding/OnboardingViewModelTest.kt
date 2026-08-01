package com.cydoniancitizen.bingee.feature.onboarding

import com.cydoniancitizen.bingee.core.credential.TmdbCredentialInputStatus
import com.cydoniancitizen.bingee.core.credential.TmdbCredentialStatus
import com.cydoniancitizen.bingee.core.credential.TmdbCredentialValidator
import com.cydoniancitizen.bingee.core.result.AppError
import com.cydoniancitizen.bingee.core.result.AppResult
import com.cydoniancitizen.bingee.testutil.FakeCredentialRepository
import com.cydoniancitizen.bingee.testutil.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun initialStateAndInputChangesAreSafe() = runTest(mainDispatcherRule.dispatcher) {
        val repository = FakeCredentialRepository()
        val viewModel = OnboardingViewModel(repository, TmdbCredentialValidator())
        runCurrent()

        assertEquals(TmdbCredentialStatus.NotConfigured, viewModel.uiState.value.credentialStatus)
        assertFalse(viewModel.uiState.value.toString().contains("fake_valid"))

        viewModel.onInputChanged("fake_valid")
        assertEquals(TmdbCredentialInputStatus.LOCALLY_VALID, viewModel.uiState.value.inputStatus)

        viewModel.onInputChanged("invalid token")
        assertEquals(TmdbCredentialInputStatus.LOCALLY_INVALID, viewModel.uiState.value.inputStatus)
        assertFalse(viewModel.uiState.value.toString().contains("invalid token"))
    }

    @Test
    fun localErrorPreventsNetworkCall() = runTest(mainDispatcherRule.dispatcher) {
        val repository = FakeCredentialRepository()
        val viewModel = OnboardingViewModel(repository, TmdbCredentialValidator())

        viewModel.submit("invalid token")

        assertEquals(AppError.InvalidInput, viewModel.uiState.value.error)
        assertEquals(0, repository.validateCalls)
    }

    @Test
    fun loadingBlocksDuplicateSubmitThenSucceeds() = runTest(mainDispatcherRule.dispatcher) {
        val repository = FakeCredentialRepository()
        repository.validationGate = CompletableDeferred()
        val viewModel = OnboardingViewModel(repository, TmdbCredentialValidator())
        viewModel.onInputChanged("fake_valid")

        viewModel.submit("fake_valid")
        runCurrent()
        viewModel.submit("fake_valid")

        assertTrue(viewModel.uiState.value.isSubmitting)
        assertEquals(1, repository.validateCalls)

        repository.validationGate?.complete(Unit)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.configured)
        assertEquals(TmdbCredentialStatus.Valid, viewModel.uiState.value.credentialStatus)
    }

    @Test
    fun unauthorizedAndRetryableFailureRemainDistinctAndCanRetry() = runTest(mainDispatcherRule.dispatcher) {
        val repository = FakeCredentialRepository()
        repository.validationResults += AppResult.Failure(AppError.Unauthorized)
        repository.validationResults += AppResult.Failure(AppError.NetworkUnavailable)
        repository.validationResults += AppResult.Success(Unit)
        val viewModel = OnboardingViewModel(repository, TmdbCredentialValidator())

        viewModel.submit("fake_valid")
        advanceUntilIdle()
        assertEquals(AppError.Unauthorized, viewModel.uiState.value.error)
        assertTrue(viewModel.uiState.value.credentialStatus is TmdbCredentialStatus.Rejected)

        viewModel.submit("fake_valid")
        advanceUntilIdle()
        assertEquals(AppError.NetworkUnavailable, viewModel.uiState.value.error)
        assertTrue(
            viewModel.uiState.value.credentialStatus is
                TmdbCredentialStatus.TemporarilyUnverifiable
        )

        viewModel.submit("fake_valid")
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.configured)
        assertEquals(3, repository.validateCalls)
    }

    @Test
    fun inputChangeCancelsStaleRequest() = runTest(mainDispatcherRule.dispatcher) {
        val repository = FakeCredentialRepository()
        repository.validationGate = CompletableDeferred()
        val viewModel = OnboardingViewModel(repository, TmdbCredentialValidator())

        viewModel.submit("fake_old")
        runCurrent()
        viewModel.onInputChanged("fake_new")
        runCurrent()

        assertEquals(1, repository.cancelCalls)
        assertEquals(TmdbCredentialInputStatus.LOCALLY_VALID, viewModel.uiState.value.inputStatus)
        assertFalse(viewModel.uiState.value.configured)
    }
}
