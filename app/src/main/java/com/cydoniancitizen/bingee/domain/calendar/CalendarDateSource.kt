package com.cydoniancitizen.bingee.domain.calendar

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.annotation.VisibleForTesting
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

internal interface CalendarDateSource {
    fun currentDate(): LocalDate
    fun observeDate(): Flow<LocalDate>
}

@Singleton
internal class SystemCalendarDateSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val clock: Clock
) : CalendarDateSource {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val dateFlow = callbackFlow {
        val signals = Channel<Unit>(Channel.CONFLATED)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                signals.trySend(Unit)
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        val monitor = launch {
            while (isActive) {
                trySend(currentDate())
                val zone = ZoneId.systemDefault()
                val waitMillis = millisUntilNextLocalMidnight(clock.instant(), zone)
                withTimeoutOrNull(waitMillis) { signals.receive() }
            }
        }
        awaitClose {
            monitor.cancel()
            signals.close()
            context.unregisterReceiver(receiver)
        }
    }.distinctUntilChanged().shareIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        replay = 1
    )

    override fun currentDate(): LocalDate = currentLocalDate(clock, ZoneId.systemDefault())

    override fun observeDate(): Flow<LocalDate> = dateFlow
}

@VisibleForTesting
internal fun currentLocalDate(clock: Clock, zoneId: ZoneId): LocalDate = LocalDate.now(clock.withZone(zoneId))

@VisibleForTesting
internal fun millisUntilNextLocalMidnight(now: Instant, zoneId: ZoneId): Long {
    val nextMidnight = now.atZone(zoneId).toLocalDate().plusDays(1).atStartOfDay(zoneId).toInstant()
    return Duration.between(now, nextMidnight).toMillis().coerceAtLeast(1L)
}
