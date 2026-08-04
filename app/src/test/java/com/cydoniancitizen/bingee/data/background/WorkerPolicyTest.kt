package com.cydoniancitizen.bingee.data.background

import com.cydoniancitizen.bingee.core.result.AppError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkerPolicyTest {
    @Test
    fun transientCompleteFailuresRetryOnlyBeforeThirdAttempt() {
        listOf(
            AppError.NetworkUnavailable,
            AppError.RateLimited,
            AppError.RemoteServiceFailure,
            AppError.LocalStorageFailure
        ).forEach { error ->
            assertEquals(WorkerRunDecision.RETRY, workerRunDecision(error, 0))
            assertEquals(WorkerRunDecision.RETRY, workerRunDecision(error, 1))
            assertEquals(WorkerRunDecision.SUCCESS, workerRunDecision(error, 2))
        }
    }

    @Test
    fun permanentConditionsWaitForNextPeriodAndProgrammingDefectsFail() {
        assertEquals(WorkerRunDecision.SUCCESS, workerRunDecision(AppError.Unauthorized, 0))
        assertEquals(WorkerRunDecision.FAILURE, workerRunDecision(AppError.InvalidInput, 0))
        assertTrue(shouldRetryNotificationEvaluation(true, 1))
        assertFalse(shouldRetryNotificationEvaluation(true, 2))
        assertFalse(shouldRetryNotificationEvaluation(false, 0))
    }
}
