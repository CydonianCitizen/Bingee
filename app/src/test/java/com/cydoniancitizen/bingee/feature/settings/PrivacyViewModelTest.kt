package com.cydoniancitizen.bingee.feature.settings

import com.cydoniancitizen.bingee.core.credential.TmdbCredentialInputStatus
import com.cydoniancitizen.bingee.core.credential.TmdbCredentialStatus
import com.cydoniancitizen.bingee.core.credential.TmdbCredentialValidator
import com.cydoniancitizen.bingee.core.result.AppError
import com.cydoniancitizen.bingee.core.result.AppResult
import com.cydoniancitizen.bingee.testutil.FakeCredentialRepository
import com.cydoniancitizen.bingee.testutil.MainDispatcherRule
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
class PrivacyViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun initialConfiguredStateAndReplacementSuccess() = runTest(mainDispatcherRule.dispatcher) {
        val repository = FakeCredentialRepository(TmdbCredentialStatus.Valid)
        val viewModel = PrivacyViewModel(repository, TmdbCredentialValidator())
        runCurrent()

        assertEquals(TmdbCredentialStatus.Valid, viewModel.uiState.value.credentialStatus)
        assertTrue(viewModel.uiState.value.canRemove)

        viewModel.onInputChanged("fake_replacement")
        viewModel.submit("fake_replacement")
        advanceUntilIdle()

        assertEquals(TmdbCredentialInputStatus.EMPTY, viewModel.uiState.value.inputStatus)
        assertEquals(TmdbCredentialStatus.Valid, viewModel.uiState.value.credentialStatus)
        assertEquals(1, repository.validateCalls)
    }

    @Test
    fun localErrorAndUnauthorizedResponseStaySafe() = runTest(mainDispatcherRule.dispatcher) {
        val repository = FakeCredentialRepository()
        repository.validationResults += AppResult.Failure(AppError.Unauthorized)
        val viewModel = PrivacyViewModel(repository, TmdbCredentialValidator())

        viewModel.submit("invalid token")
        assertEquals(AppError.InvalidInput, viewModel.uiState.value.error)
        assertEquals(0, repository.validateCalls)

        viewModel.submit("fake_rejected")
        advanceUntilIdle()
        assertEquals(AppError.Unauthorized, viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.canRemove)
    }

    @Test
    fun retryUsesStoredCredentialWithoutExposingIt() = runTest(mainDispatcherRule.dispatcher) {
        val repository =
            FakeCredentialRepository(
                TmdbCredentialStatus.TemporarilyUnverifiable(
                    AppError.NetworkUnavailable,
                    hasStoredCredential = true
                )
            )
        val viewModel = PrivacyViewModel(repository, TmdbCredentialValidator())

        viewModel.retry("")
        advanceUntilIdle()

        assertEquals(1, repository.revalidateCalls)
        assertEquals(TmdbCredentialStatus.Valid, viewModel.uiState.value.credentialStatus)
        assertFalse(viewModel.uiState.value.toString().contains("fake_internal"))
    }

    @Test
    fun deletionRequiresConfirmationAndRemovesCredential() = runTest(mainDispatcherRule.dispatcher) {
        val repository = FakeCredentialRepository(TmdbCredentialStatus.Valid)
        val viewModel = PrivacyViewModel(repository, TmdbCredentialValidator())
        runCurrent()

        viewModel.requestRemoval()
        assertTrue(viewModel.uiState.value.showRemovalConfirmation)
        assertEquals(0, repository.removeCalls)

        viewModel.confirmRemoval()
        advanceUntilIdle()

        assertEquals(1, repository.removeCalls)
        assertEquals(TmdbCredentialStatus.NotConfigured, viewModel.uiState.value.credentialStatus)
        assertFalse(viewModel.uiState.value.showRemovalConfirmation)
    }
}
