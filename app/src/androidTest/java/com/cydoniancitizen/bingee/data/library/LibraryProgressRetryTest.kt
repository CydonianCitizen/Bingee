package com.cydoniancitizen.bingee.data.library

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.LibraryProgress
import com.cydoniancitizen.bingee.core.model.MediaSearchResult
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.core.model.MovieWatchState
import com.cydoniancitizen.bingee.core.result.AppError
import com.cydoniancitizen.bingee.core.result.AppResult
import com.cydoniancitizen.bingee.data.library.local.BingeeDatabase
import com.cydoniancitizen.bingee.domain.calendar.CalendarDateSource
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryProgressRetryTest {
    private lateinit var database: BingeeDatabase
    private lateinit var repository: DefaultLibraryRepository
    private val dates = FailingDateSource()
    private val now = Instant.parse("2026-08-01T10:00:00Z")
    private val ref = ExternalMediaRef(MediaSource.TMDB, "42")

    @Before
    fun createRepository() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            BingeeDatabase::class.java
        ).build()
        repository = DefaultLibraryRepository(
            database.libraryDao(),
            database.watchProgressDao(),
            database.ratingDao(),
            Clock.fixed(now, ZoneOffset.UTC),
            dates,
            Dispatchers.Default
        )
        assertTrue(repository.add(MediaSearchResult(ref, MediaType.MOVIE, "Retry fixture")) is AppResult.Success)
        database.watchProgressDao().markMovieWatched(ref.source, ref.externalId, now)
        Unit
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun allProgressConsumersRecoverOnSameSubscriptionAndKeepReceivingRoomWrites() = runBlocking {
        val entries = repository.observeEntries().produceIn(this)
        val detail = repository.observeEntry(ref, MediaType.MOVIE).produceIn(this)
        val viewing = repository.observePersonalViewing().produceIn(this)
        try {
            entries.awaitSuccess()
            detail.awaitSuccess { it != null }
            viewing.awaitSuccess { it.size == 1 }
            assertEquals(1, dates.subscriptions.get())

            dates.updates.send(Result.failure(IOException("Synthetic observation failure")))
            listOf(entries, detail, viewing).forEach {
                assertEquals(AppResult.Failure(AppError.Unknown), withTimeout(5_000) { it.receive() })
            }
            entries.awaitSuccess()
            detail.awaitSuccess { it != null }
            viewing.awaitSuccess { it.size == 1 }
            assertEquals(2, dates.subscriptions.get())

            // Dropping one consumer must not cancel the shared read needed by the others.
            entries.cancel()
            database.watchProgressDao().markMovieUnwatched(ref.source, ref.externalId)
            detail.awaitSuccess { it?.progress == LibraryProgress.Movie(MovieWatchState.Unwatched) }
            viewing.awaitSuccess { it.isEmpty() }
            assertEquals(2, dates.subscriptions.get())
        } finally {
            entries.cancel()
            detail.cancel()
            viewing.cancel()
        }
    }

    @Test
    fun repeatedInitialFailuresStayRateLimitedThenRecoverWithoutManualRetry() = runBlocking {
        dates.failuresRemaining.set(3)
        val entries = repository.observeEntries().produceIn(this)
        try {
            repeat(3) {
                assertEquals(
                    AppResult.Failure(AppError.Unknown),
                    withTimeout(5_000) { entries.receive() }
                )
            }
            entries.awaitSuccess()
            val starts = List(4) { withTimeout(5_000) { dates.starts.receive() } }
            starts.zipWithNext().forEach { (earlier, later) ->
                assertTrue("Retry must wait at least one second", later - earlier >= 1_000_000_000L)
            }
            assertEquals(4, dates.subscriptions.get())
        } finally {
            entries.cancel()
        }
    }

    @Test
    fun persistentFailureStopsAfterLastConsumerLeavesAndRestartsOnReturn() = runBlocking {
        assertNull(withTimeoutOrNull(1_100) { dates.starts.receive() })
        dates.failuresRemaining.set(Int.MAX_VALUE)
        val entries = repository.observeEntries().produceIn(this)
        try {
            assertTrue(withTimeout(5_000) { entries.receive() } is AppResult.Failure)
        } finally {
            entries.cancel()
        }

        // Observe the actual five-second grace period plus one retry interval. No sleep-based settling.
        withTimeoutOrNull(6_100) { while (true) dates.starts.receive() }
        val stoppedAt = dates.subscriptions.get()
        assertNull(withTimeoutOrNull(1_100) { dates.starts.receive() })
        assertEquals(stoppedAt, dates.subscriptions.get())
        assertTrue("Retries during the grace period remain bounded", stoppedAt in 1..7)

        dates.failuresRemaining.set(0)
        val returned = repository.observeEntries().produceIn(this)
        try {
            returned.awaitSuccess()
            assertEquals(stoppedAt + 1, dates.subscriptions.get())
        } finally {
            returned.cancel()
        }
    }

    @Test
    fun lastConsumerCancellationReleasesHealthyObservation() = runBlocking {
        val entries = repository.observeEntries().produceIn(this)
        try {
            entries.awaitSuccess()
            withTimeout(5_000) { dates.starts.receive() }
            assertEquals(1, dates.subscriptions.get())
        } finally {
            entries.cancel()
        }
        withTimeout(7_000) { dates.stops.receive() }
        val stoppedAt = dates.subscriptions.get()
        assertNull(withTimeoutOrNull(1_100) { dates.starts.receive() })
        assertEquals(stoppedAt, dates.subscriptions.get())
    }

    private suspend fun <T> ReceiveChannel<AppResult<T>>.awaitSuccess(predicate: (T) -> Boolean = { true }): T =
        withTimeout(5_000) {
            var result = receive()
            while (result !is AppResult.Success || !predicate(result.value)) result = receive()
            result.value
        }

    private class FailingDateSource : CalendarDateSource {
        val updates = Channel<Result<LocalDate>>(Channel.UNLIMITED)
        val starts = Channel<Long>(Channel.UNLIMITED)
        val stops = Channel<Unit>(Channel.UNLIMITED)
        val subscriptions = AtomicInteger()
        val failuresRemaining = AtomicInteger()

        override fun currentDate(): LocalDate = LocalDate.of(2026, 8, 1)

        override fun observeDate(): Flow<LocalDate> = flow {
            subscriptions.incrementAndGet()
            starts.send(System.nanoTime())
            try {
                if (failuresRemaining.getAndUpdate { (it - 1).coerceAtLeast(0) } > 0) {
                    throw IOException("Synthetic initial observation failure")
                }
                emit(currentDate())
                for (update in updates) emit(update.getOrThrow())
            } finally {
                stops.trySend(Unit)
            }
        }
    }
}
