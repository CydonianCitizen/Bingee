package com.cydoniancitizen.bingee.core.ui

import com.cydoniancitizen.bingee.R
import com.cydoniancitizen.bingee.core.result.AppError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppErrorUiMapperTest {
    @Test
    fun everyErrorMapsToExpectedSafeResource() {
        val expected =
            mapOf(
                AppError.NetworkUnavailable to R.string.error_network_unavailable,
                AppError.Unauthorized to R.string.error_unauthorized,
                AppError.RateLimited to R.string.error_rate_limited,
                AppError.RemoteServiceFailure to R.string.error_remote_service,
                AppError.InvalidRemoteResponse to R.string.error_invalid_remote_response,
                AppError.MissingData to R.string.error_missing_data,
                AppError.InvalidInput to R.string.error_invalid_input,
                AppError.CorruptedData to R.string.error_corrupted_data,
                AppError.UnsupportedData to R.string.error_unsupported_data,
                AppError.LocalStorageFailure to R.string.error_local_storage,
                AppError.Unknown to R.string.error_unknown
            )

        expected.forEach { (error, resourceId) ->
            assertEquals(resourceId, error.toUiError().messageRes)
        }
    }

    @Test
    fun retryFlagComesFromStructuredClassification() {
        assertTrue(AppError.NetworkUnavailable.toUiError().canRetry)
        assertFalse(AppError.Unauthorized.toUiError().canRetry)
    }

    @Test
    fun mappingCannotExposeInternalExceptionMessage() {
        val uiError = AppError.Unknown.toUiError()

        assertEquals(R.string.error_unknown, uiError.messageRes)
        assertFalse(uiError.toString().contains("Throwable"))
        assertFalse(uiError.toString().contains("Exception"))
    }
}
