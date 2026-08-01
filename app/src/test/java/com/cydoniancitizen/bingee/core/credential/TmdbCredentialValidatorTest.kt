package com.cydoniancitizen.bingee.core.credential

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TmdbCredentialValidatorTest {
    private val validator = TmdbCredentialValidator()

    @Test
    fun blankInputIsRejectedSafely() {
        val result = validator.validate("   ")

        assertEquals(
            TmdbCredentialValidation.Invalid(TmdbCredentialInputError.BLANK),
            result
        )
    }

    @Test
    fun surroundingWhitespaceIsTrimmed() {
        val result = validator.validate("  fake_test-token.value  ")

        assertTrue(result is TmdbCredentialValidation.Valid)
        assertTrue(
            (result as TmdbCredentialValidation.Valid).credential.reveal() ==
                "fake_test-token.value"
        )
    }

    @Test
    fun bearerCompatibleStructureIsAcceptedWithoutClaimingAuthorization() {
        val result = validator.validate("fake_test-token.value_123~ok+/=")

        assertTrue(result is TmdbCredentialValidation.Valid)
        assertTrue(result.toString().contains("REDACTED"))
    }

    @Test
    fun internalWhitespaceAndUnsupportedCharactersAreRejected() {
        assertTrue(validator.validate("fake token") is TmdbCredentialValidation.Invalid)
        assertTrue(validator.validate("fake\$token") is TmdbCredentialValidation.Invalid)
    }

    @Test
    fun validationOutputNeverContainsFullCredential() {
        val fakeCredential = "fake_test-token.private"

        val valid = validator.validate(fakeCredential)
        val invalid = validator.validate("$fakeCredential invalid")

        assertFalse(valid.toString().contains(fakeCredential))
        assertFalse(invalid.toString().contains(fakeCredential))
    }
}
