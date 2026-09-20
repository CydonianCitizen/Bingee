package com.cydoniancitizen.bingee.core.result

enum class AppError(val isRetryable: Boolean) {
    NetworkUnavailable(true),
    Unauthorized(false),
    RateLimited(true),
    RemoteServiceFailure(true),
    InvalidRemoteResponse(true),
    MissingData(false),
    InvalidInput(false),
    CorruptedData(false),
    UnsupportedData(false),
    LocalStorageFailure(false),
    NotificationDeliveryFailure(true),
    NotTrackable(false),
    MediaTypeMismatch(false),
    Unknown(false)
}
