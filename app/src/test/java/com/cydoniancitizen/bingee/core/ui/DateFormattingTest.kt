package com.cydoniancitizen.bingee.core.ui

import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class DateFormattingTest {
    @Test
    fun formatsDatesForTheRequestedLocale() {
        val date = LocalDate.of(2024, 1, 2)

        assertEquals("Jan 2, 2024", date.formatLocalized(Locale.US))
        assertEquals("2 gen 2024", date.formatLocalized(Locale.ITALIAN))
    }
}
