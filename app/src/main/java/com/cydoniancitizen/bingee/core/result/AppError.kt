package com.cydoniancitizen.bingee.core.result

sealed interface AppError {
    val isRetryable: Boolean

    data object NetworkUnavailable : AppError {
        override val isRetryable = true
    }

    data object Unauthorized : AppError {
        override val isRetryable = false
    }

    data object RateLimited : AppError {
        override val isRetryable = true
    }

    data object RemoteServiceFailure : AppError {
        override val isRetryable = true
    }

    data object InvalidRemoteResponse : AppError {
        override val isRetryable = true
    }

    data object MissingData : AppError {
        override val isRetryable = false
    }

    data object InvalidInput : AppError {
        override val isRetryable = false
    }

    data object CorruptedData : AppError {
        override val isRetryable = false
    }

    data object UnsupportedData : AppError {
        override val isRetryable = false
    }

    data object Unknown : AppError {
        override val isRetryable = false
    }
}
