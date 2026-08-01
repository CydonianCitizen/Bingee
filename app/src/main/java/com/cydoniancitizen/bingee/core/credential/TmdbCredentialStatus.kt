package com.cydoniancitizen.bingee.core.credential

import com.cydoniancitizen.bingee.core.result.AppError

sealed interface TmdbCredentialStatus {
    data object Checking : TmdbCredentialStatus

    data object NotConfigured : TmdbCredentialStatus

    data class Validating(val hasStoredCredential: Boolean) : TmdbCredentialStatus

    data object Valid : TmdbCredentialStatus

    data class Rejected(val hasStoredCredential: Boolean) : TmdbCredentialStatus

    data class TemporarilyUnverifiable(val error: AppError, val hasStoredCredential: Boolean) : TmdbCredentialStatus

    data object StorageUnreadable : TmdbCredentialStatus
}

enum class TmdbCredentialInputStatus {
    EMPTY,
    LOCALLY_INVALID,
    LOCALLY_VALID
}
