package com.cydoniancitizen.bingee.domain.calendar

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SystemCalendarDateSourceTest {
    @Test
    fun observeDateEmitsCurrentLocalDateImmediately() = runBlocking {
        val clock = Clock.fixed(Instant.parse("2026-08-18T22:30:00Z"), ZoneOffset.UTC)
        val source = SystemCalendarDateSource(
            ApplicationProvider.getApplicationContext<Context>(),
            clock
        )

        assertEquals(
            currentLocalDate(clock, ZoneId.systemDefault()),
            source.observeDate().first()
        )
    }
}
