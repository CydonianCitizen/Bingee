package com.cydoniancitizen.bingee.data.background

import com.cydoniancitizen.bingee.core.model.NotificationCapabilityStatus
import com.cydoniancitizen.bingee.core.model.NotificationDispatchSummary
import com.cydoniancitizen.bingee.domain.notification.NotificationDispatchCoordinator
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationEvaluationWorkerExecutorTest {
    @Test
    fun disabledUnavailableEmptyAndSuccessfulDispatchesSucceed() = runTest {
        listOf(
            NotificationDispatchSummary(),
            NotificationDispatchSummary(capability = NotificationCapabilityStatus.PERMISSION_DENIED),
            NotificationDispatchSummary(candidates = 1, posted = 1)
        ).forEach { summary ->
            assertEquals(
                WorkerRunDecision.SUCCESS,
                NotificationEvaluationWorkerExecutor(FakeCoordinator(summary)).execute(0)
            )
        }
    }

    @Test
    fun transientPostingOrRoomFailureRetriesOnlyWithinAttemptCap() = runTest {
        val executor = NotificationEvaluationWorkerExecutor(
            FakeCoordinator(NotificationDispatchSummary(failed = 1, transientFailure = true))
        )

        assertEquals(WorkerRunDecision.RETRY, executor.execute(0))
        assertEquals(WorkerRunDecision.RETRY, executor.execute(1))
        assertEquals(WorkerRunDecision.SUCCESS, executor.execute(2))
    }

    private class FakeCoordinator(private val summary: NotificationDispatchSummary) :
        NotificationDispatchCoordinator {
        override suspend fun dispatch(): NotificationDispatchSummary = summary
    }
}
