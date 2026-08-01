package com.cydoniancitizen.bingee.core.credential

import javax.inject.Inject

class TmdbCredentialValidator @Inject constructor() {
    fun inputStatus(input: String): TmdbCredentialInputStatus = if (input.trim().isEmpty()) {
        TmdbCredentialInputStatus.EMPTY
    } else if (validate(input) is TmdbCredentialValidation.Valid) {
        TmdbCredentialInputStatus.LOCALLY_VALID
    } else {
        TmdbCredentialInputStatus.LOCALLY_INVALID
    }

    fun validate(input: String): TmdbCredentialValidation {
        val normalized = input.trim()
        if (normalized.isEmpty()) {
            return TmdbCredentialValidation.Invalid(TmdbCredentialInputError.BLANK)
        }
        if (!BEARER_TOKEN.matches(normalized)) {
            return TmdbCredentialValidation.Invalid(TmdbCredentialInputError.INVALID_STRUCTURE)
        }
        return TmdbCredentialValidation.Valid(TmdbCredential(normalized))
    }

    private companion object {
        val BEARER_TOKEN = Regex("[A-Za-z0-9\\-._~+/]+={0,}")
    }
}

sealed interface TmdbCredentialValidation {
    class Valid internal constructor(internal val credential: TmdbCredential) : TmdbCredentialValidation {
        override fun toString(): String = "Valid(REDACTED)"
    }

    data class Invalid(val reason: TmdbCredentialInputError) : TmdbCredentialValidation
}

enum class TmdbCredentialInputError {
    BLANK,
    INVALID_STRUCTURE
}

class TmdbCredential internal constructor(private val value: String) {
    internal fun reveal(): String = value

    override fun toString(): String = "TmdbCredential(REDACTED)"
}
