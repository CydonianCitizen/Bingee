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

    suspend fun awaitTurn() = mutex.withLock {
        val now = clock.millis()
        val wait = lastStartedAtMillis?.let { (MINIMUM_SPACING_MILLIS - (now - it)).coerceAtLeast(0) } ?: 0
        if (wait > 0) delay.waitMillis(wait)
        lastStartedAtMillis = clock.millis()
    }

    internal companion object {
        // Jikan v4 documentation verified 2026-08-05: maximum 3 requests/second.
        const val MINIMUM_SPACING_MILLIS = 334L
    }
}

internal val DefaultJikanDelay = JikanDelay { delay(it) }
