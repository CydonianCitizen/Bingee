package com.cydoniancitizen.bingee.feature.settings

import com.cydoniancitizen.bingee.domain.repository.AppUpdateRepository
import com.cydoniancitizen.bingee.domain.repository.AppUpdateResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AboutViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun checkForUpdatesUpdatesUiStateToUpToDate() = runTest {
        val fakeRepo = FakeAppUpdateRepository(
            result = AppUpdateResult.UpToDate(installedVersion = "1.0.1")
        )
        val viewModel = AboutViewModel(fakeRepo)

        assertEquals(UpdateCheckUiState.Idle, viewModel.uiState.value.updateState)

        viewModel.checkForUpdates()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value.updateState
        assertTrue(state is UpdateCheckUiState.UpToDate)
        assertEquals("1.0.1", (state as UpdateCheckUiState.UpToDate).installedVersion)
    }

    @Test
    fun checkForUpdatesUpdatesUiStateToUpdateAvailable() = runTest {
        val fakeRepo = FakeAppUpdateRepository(
            result = AppUpdateResult.UpdateAvailable(
                installedVersion = "1.0.1",
                latestVersion = "1.1.0",
                releaseUrl = "https://github.com/CydonianCitizen/Bingee/releases/tag/v1.1.0"
            )
        )
        val viewModel = AboutViewModel(fakeRepo)

        viewModel.checkForUpdates()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value.updateState
        assertTrue(state is UpdateCheckUiState.UpdateAvailable)
        val updateAvailable = state as UpdateCheckUiState.UpdateAvailable
        assertEquals("1.0.1", updateAvailable.installedVersion)
        assertEquals("1.1.0", updateAvailable.latestVersion)
        assertEquals("https://github.com/CydonianCitizen/Bingee/releases/tag/v1.1.0", updateAvailable.releaseUrl)
    }

    @Test
    fun checkForUpdatesUpdatesUiStateToErrorOnFailure() = runTest {
        val fakeRepo = FakeAppUpdateRepository(
            result = AppUpdateResult.Error("Network error")
        )
        val viewModel = AboutViewModel(fakeRepo)

        viewModel.checkForUpdates()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value.updateState
        assertTrue(state is UpdateCheckUiState.Error)
        assertEquals("Network error", (state as UpdateCheckUiState.Error).message)
    }

    private class FakeAppUpdateRepository(private val result: AppUpdateResult) : AppUpdateRepository {
        override suspend fun checkForUpdates(installedVersionName: String): AppUpdateResult = result
    }
}
