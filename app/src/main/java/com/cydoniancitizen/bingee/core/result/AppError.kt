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

    data object LocalStorageFailure : AppError {
        override val isRetryable = false
    }

    data object NotificationDeliveryFailure : AppError {
        override val isRetryable = true
    }

    data object NotTrackable : AppError {
        override val isRetryable = false
    }

    data object MediaTypeMismatch : AppError {
        override val isRetryable = false
    }

    data object Unknown : AppError {
        override val isRetryable = false
    }

    sealed interface LinkError : AppError {
        override val isRetryable: Boolean get() = false

        data object SelfLinkProhibited : LinkError
        data object MediaNotFound : LinkError
        data object AlreadyLinked : LinkError
        data object LinkConflict : LinkError
        data object InvalidPreferredMember : LinkError
        data object LinkGroupNotFound : LinkError
        data object CorruptedGroup : LinkError
    }
}
