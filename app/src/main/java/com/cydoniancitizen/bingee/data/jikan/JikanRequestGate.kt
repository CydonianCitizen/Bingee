package com.cydoniancitizen.bingee.data.jikan

import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal fun interface JikanDelay {
    suspend fun waitMillis(millis: Long)
}

@Singleton
internal class JikanRequestGate @Inject constructor(private val clock: Clock, private val delay: JikanDelay) {
    private val mutex = Mutex()
    private var lastStartedAtMillis: Long? = null
    private val windowTimestamps = ArrayDeque<Long>()

    suspend fun awaitTurn() = mutex.withLock {
        val now = clock.millis()

        val cutoff = now - ROLLING_WINDOW_MILLIS
        while (windowTimestamps.isNotEmpty() && windowTimestamps.first() <= cutoff) {
            windowTimestamps.removeFirst()
        }

        val spacingWait = lastStartedAtMillis?.let {
            (MINIMUM_SPACING_MILLIS - (now - it)).coerceAtLeast(0)
        } ?: 0L

        val windowWait = if (windowTimestamps.size >= MAX_REQUESTS_PER_MINUTE) {
            (ROLLING_WINDOW_MILLIS - (now - windowTimestamps.first())).coerceAtLeast(0)
        } else {
            0L
        }

        val wait = maxOf(spacingWait, windowWait)
        if (wait > 0) {
            delay.waitMillis(wait)
        }

        val actualNow = clock.millis()
        lastStartedAtMillis = actualNow
        windowTimestamps.addLast(actualNow)
    }

    internal companion object {
        // Jikan v4 documentation verified 2026-08-05: maximum 3 requests/second and 60 requests/minute.
        const val MINIMUM_SPACING_MILLIS = 334L
        const val MAX_REQUESTS_PER_MINUTE = 60
        const val ROLLING_WINDOW_MILLIS = 60_000L
    }
}

internal val DefaultJikanDelay = JikanDelay { delay(it) }
