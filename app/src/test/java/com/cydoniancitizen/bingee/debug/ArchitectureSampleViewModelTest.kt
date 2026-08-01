package com.cydoniancitizen.bingee.debug

import com.cydoniancitizen.bingee.core.result.AppError
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ArchitectureSampleViewModelTest {
    @Test
    fun initialStateIsIdle() = runTest {
        val viewModel =
            ArchitectureSampleViewModel(
                mediaRepository = FakeMediaRepository(),
                dispatcher = StandardTestDispatcher(testScheduler)
            )

        assertEquals(ArchitectureSampleUiState.Initial, viewModel.uiState.value)
    }

    @Test
    fun loadMovesThroughLoadingToContent() = runTest {
        val viewModel =
            ArchitectureSampleViewModel(
                mediaRepository = FakeMediaRepository(),
                dispatcher = StandardTestDispatcher(testScheduler)
            )

        viewModel.load("fixed")
        assertEquals(ArchitectureSampleUiState.Loading, viewModel.uiState.value)

        advanceUntilIdle()

        assertEquals(
            ArchitectureSampleUiState.Content(FakeMediaData.searchResults),
            viewModel.uiState.value
        )
    }

    @Test
    fun repositoryFailureBecomesStructuredFailureState() = runTest {
        val viewModel =
            ArchitectureSampleViewModel(
                mediaRepository = FakeMediaRepository.failing(AppError.RateLimited),
                dispatcher = StandardTestDispatcher(testScheduler)
            )

        viewModel.load("fixed")
        advanceUntilIdle()

        assertEquals(
            ArchitectureSampleUiState.Failure(AppError.RateLimited),
            viewModel.uiState.value
        )
    }
}
