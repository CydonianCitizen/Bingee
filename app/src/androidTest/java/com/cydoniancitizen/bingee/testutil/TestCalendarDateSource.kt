package com.cydoniancitizen.bingee.testutil

import com.cydoniancitizen.bingee.domain.calendar.CalendarDateSource
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

internal class TestCalendarDateSource(initialDate: LocalDate) : CalendarDateSource {
    private val dates = MutableStateFlow(initialDate)

    override fun currentDate(): LocalDate = dates.value

    override fun observeDate(): Flow<LocalDate> = dates

    fun advanceTo(date: LocalDate) {
        dates.value = date
    }
}
