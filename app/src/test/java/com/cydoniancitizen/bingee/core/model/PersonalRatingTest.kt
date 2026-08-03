package com.cydoniancitizen.bingee.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PersonalRatingTest {
    @Test
    fun acceptsInclusiveIntegerBoundsAndUsesValueEquality() {
        assertEquals(1, PersonalRating(1).value)
        assertEquals(10, PersonalRating(10).value)
        assertEquals(PersonalRating(7), PersonalRating(7))
        assertNotEquals(PersonalRating(7), PersonalRating(8))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsZero() {
        PersonalRating(0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsAboveMaximum() {
        PersonalRating(11)
    }
}
