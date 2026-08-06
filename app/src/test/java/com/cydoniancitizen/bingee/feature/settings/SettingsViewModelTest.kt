package com.cydoniancitizen.bingee.feature.settings

import com.cydoniancitizen.bingee.core.credential.TmdbCredentialInputStatus
import com.cydoniancitizen.bingee.core.credential.TmdbCredentialStatus
import com.cydoniancitizen.bingee.core.credential.TmdbCredentialValidator
import com.cydoniancitizen.bingee.core.result.AppError
import com.cydoniancitizen.bingee.core.result.AppResult
import com.cydoniancitizen.bingee.data.settings.AppLanguage
import com.cydoniancitizen.bingee.data.settings.AppTheme
import com.cydoniancitizen.bingee.data.settings.AppearancePreferences
import com.cydoniancitizen.bingee.testutil.FakeCredentialRepository
import com.cydoniancitizen.bingee.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun initialConfiguredStateAndReplacementSuccess() = runTest(mainDispatcherRule.dispatcher) {
        val repository = FakeCredentialRepository(TmdbCredentialStatus.Valid)
        val viewModel = SettingsViewModel(repository, TmdbCredentialValidator(), FakeAppearancePreferences())
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
        val viewModel = SettingsViewModel(repository, TmdbCredentialValidator(), FakeAppearancePreferences())

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
        val viewModel = SettingsViewModel(repository, TmdbCredentialValidator(), FakeAppearancePreferences())

        viewModel.retry("")
        advanceUntilIdle()

        assertEquals(1, repository.revalidateCalls)
        assertEquals(TmdbCredentialStatus.Valid, viewModel.uiState.value.credentialStatus)
        assertFalse(viewModel.uiState.value.toString().contains("fake_internal"))
    }

    @Test
    fun deletionRequiresConfirmationAndRemovesCredential() = runTest(mainDispatcherRule.dispatcher) {
        val repository = FakeCredentialRepository(TmdbCredentialStatus.Valid)
        val viewModel = SettingsViewModel(repository, TmdbCredentialValidator(), FakeAppearancePreferences())
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

    @Test
    fun sameLanguageSelectionDoesNotRewritePreference() = runTest(mainDispatcherRule.dispatcher) {
        val preferences = FakeAppearancePreferences()
        val viewModel = SettingsViewModel(
            FakeCredentialRepository(),
            TmdbCredentialValidator(),
            preferences
        )
        runCurrent()

        viewModel.setLanguage(AppLanguage.ENGLISH)
        advanceUntilIdle()
        assertTrue(preferences.languageWrites.isEmpty())

        viewModel.setLanguage(AppLanguage.ITALIAN)
        advanceUntilIdle()
        assertEquals(listOf(AppLanguage.ITALIAN), preferences.languageWrites)
    }

    private class FakeAppearancePreferences : AppearancePreferences {
        val languageWrites = mutableListOf<AppLanguage>()

        override fun observeTheme(): Flow<AppTheme> = flowOf(AppTheme.SYSTEM_DEFAULT)
        override suspend fun setTheme(theme: AppTheme) {}
        override fun observeLanguage(): Flow<AppLanguage> = flowOf(AppLanguage.ENGLISH)
        override suspend fun setLanguage(language: AppLanguage) {
            languageWrites += language
        }
        override suspend fun getEffectiveTmdbLanguage(): String = "en-US"
    }
}
