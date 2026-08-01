package com.cydoniancitizen.bingee.core.ui

import androidx.annotation.StringRes
import com.cydoniancitizen.bingee.R
import com.cydoniancitizen.bingee.core.result.AppError

data class UiError(@param:StringRes val messageRes: Int, val canRetry: Boolean)

fun AppError.toUiError(): UiError = UiError(
    messageRes =
    when (this) {
        AppError.NetworkUnavailable -> R.string.error_network_unavailable
        AppError.Unauthorized -> R.string.error_unauthorized
        AppError.RateLimited -> R.string.error_rate_limited
        AppError.RemoteServiceFailure -> R.string.error_remote_service
        AppError.InvalidRemoteResponse -> R.string.error_invalid_remote_response
        AppError.MissingData -> R.string.error_missing_data
        AppError.InvalidInput -> R.string.error_invalid_input
        AppError.CorruptedData -> R.string.error_corrupted_data
        AppError.UnsupportedData -> R.string.error_unsupported_data
        AppError.Unknown -> R.string.error_unknown
    },
    canRetry = isRetryable
)
