package com.cydoniancitizen.bingee.core.result

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppResultTest {
    @Test
    fun successExposesOnlyValue() {
        val result: AppResult<String> = AppResult.Success("value")

        assertNull(result.errorOrNull())
    }

    @Test
    fun failureExposesOnlyStructuredError() {
        val result: AppResult<String> = AppResult.Failure(AppError.InvalidInput)

        assertEquals(AppError.InvalidInput, result.errorOrNull())
    }

    @Test
    fun retryabilityIsDeterministic() {
        assertTrue(AppError.NetworkUnavailable.isRetryable)
        assertTrue(AppError.RateLimited.isRetryable)
        assertTrue(AppError.RemoteServiceFailure.isRetryable)
        assertTrue(AppError.InvalidRemoteResponse.isRetryable)
        assertTrue(AppError.NotificationDeliveryFailure.isRetryable)
        assertFalse(AppError.Unauthorized.isRetryable)
        assertFalse(AppError.MissingData.isRetryable)
        assertFalse(AppError.InvalidInput.isRetryable)
        assertFalse(AppError.CorruptedData.isRetryable)
        assertFalse(AppError.UnsupportedData.isRetryable)
        assertFalse(AppError.LocalStorageFailure.isRetryable)
        assertFalse(AppError.NotTrackable.isRetryable)
        assertFalse(AppError.MediaTypeMismatch.isRetryable)
        assertFalse(AppError.Unknown.isRetryable)
    }
}
