package com.cydoniancitizen.bingee.domain.calendar

import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow

internal interface CalendarDateSource {
    fun currentDate(): LocalDate
    fun observeDate(): Flow<LocalDate>
}

@Singleton
internal class SystemCalendarDateSource @Inject constructor(private val clock: Clock) : CalendarDateSource {
    override fun currentDate(): LocalDate = LocalDate.now(clock)

    override fun observeDate(): Flow<LocalDate> = flow {
        while (true) {
            emit(currentDate())
            delay(DATE_CHECK_INTERVAL_MILLIS)
        }
    }.distinctUntilChanged()
}

private const val DATE_CHECK_INTERVAL_MILLIS = 60L * 60L * 1000L
