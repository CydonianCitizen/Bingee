package com.cydoniancitizen.bingee.data.library.local

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryDaoTest {
    private lateinit var database: BingeeDatabase
    private lateinit var dao: LibraryDao

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BingeeDatabase::class.java).build()
        dao = database.libraryDao()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun repeatedAddIsIdempotentAndRefreshesMetadataWithoutResettingTimestamps() = runBlocking {
        val firstTime = Instant.parse("2026-08-01T10:00:00Z")
        val secondTime = Instant.parse("2026-08-02T10:00:00Z")
        val first = dao.addToLibrary(media("Old title", MediaType.MOVIE, firstTime), MediaSource.TMDB, "42", firstTime)
        val second = dao.addToLibrary(
            media("New title", MediaType.MOVIE, secondTime),
            MediaSource.TMDB,
            "42",
            secondTime
        )

        assertEquals(first.media.localMediaId, second.media.localMediaId)
        assertEquals("New title", second.media.title)
        assertEquals(firstTime, second.media.createdAt)
        assertEquals(secondTime, second.media.metadataUpdatedAt)
        assertEquals(firstTime, second.addedAt)
        assertEquals(1, rowCount("media_entries"))
        assertEquals(1, rowCount("external_refs"))
        assertEquals(1, rowCount("library_entries"))
    }

    @Test
    fun sameNumericIdFromDifferentProvidersDoesNotCollide() = runBlocking {
        val now = Instant.parse("2026-08-01T10:00:00Z")
        val tmdb = dao.addToLibrary(media("Movie", MediaType.MOVIE, now), MediaSource.TMDB, "7", now)
        val jikan = dao.addToLibrary(media("Series", MediaType.SERIES, now), MediaSource.JIKAN, "7", now)

        assertNotEquals(tmdb.media.localMediaId, jikan.media.localMediaId)
        assertEquals(2, dao.observeLibraryItems().firstValue().size)
        assertTrue(dao.isInLibrary(MediaSource.TMDB, "7"))
        assertTrue(dao.isInLibrary(MediaSource.JIKAN, "7"))
    }

    @Test
    fun filtersUseStructuralMediaType() = runBlocking {
        val now = Instant.parse("2026-08-01T10:00:00Z")
        dao.addToLibrary(media("Movie", MediaType.MOVIE, now), MediaSource.TMDB, "1", now)
        dao.addToLibrary(media("Series", MediaType.SERIES, now), MediaSource.TMDB, "2", now)

        assertEquals(listOf("Movie"), dao.observeLibraryItems(MediaType.MOVIE).firstValue().map { it.media.title })
        assertEquals(listOf("Series"), dao.observeLibraryItems(MediaType.SERIES).firstValue().map { it.media.title })
    }

    @Test
    fun removeEmitsAndRetainsCanonicalMetadataAndReference() = runBlocking {
        val now = Instant.parse("2026-08-01T10:00:00Z")
        val sizes = Channel<Int>(Channel.UNLIMITED)
        val observation = launch(Dispatchers.IO) {
            dao.observeLibraryItems().collect { sizes.send(it.size) }
        }
        assertEquals(0, withTimeout(5_000) { sizes.receive() })

        dao.addToLibrary(media("Movie", MediaType.MOVIE, now), MediaSource.TMDB, "1", now)
        assertEquals(1, withTimeout(5_000) { sizes.receive() })
        assertEquals(1, dao.removeMembership(MediaSource.TMDB, "1"))
        assertEquals(0, withTimeout(5_000) { sizes.receive() })

        assertFalse(dao.isInLibrary(MediaSource.TMDB, "1"))
        assertNotNull(dao.getMediaByExternalRef(MediaSource.TMDB, "1"))
        assertEquals(1, rowCount("media_entries"))
        assertEquals(1, rowCount("external_refs"))
        assertEquals(0, rowCount("library_entries"))
        assertEquals(0, dao.removeMembership(MediaSource.TMDB, "1"))
        observation.cancel()
    }

    @Test(expected = SQLiteConstraintException::class)
    fun foreignKeyRejectsOrphanExternalReference() {
        database.openHelper.writableDatabase.execSQL(
            "INSERT INTO external_refs(local_media_id, source, external_id) VALUES(999, 'TMDB', 'orphan')"
        )
    }

    @Test
    fun diskDatabaseKeepsLibraryAcrossReopen() = runBlocking {
        database.close()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "library-reopen-test.db"
        context.deleteDatabase(name)
        try {
            var diskDatabase = Room.databaseBuilder(context, BingeeDatabase::class.java, name).build()
            val now = Instant.parse("2026-08-01T10:00:00Z")
            diskDatabase.libraryDao().addToLibrary(
                media("Persistent", MediaType.MOVIE, now),
                MediaSource.TMDB,
                "99",
                now
            )
            diskDatabase.close()

            diskDatabase = Room.databaseBuilder(context, BingeeDatabase::class.java, name).build()
            assertEquals(
                "Persistent",
                diskDatabase.libraryDao().observeLibraryItems().firstValue().single().media.title
            )
            diskDatabase.close()
        } finally {
            context.deleteDatabase(name)
            database = Room.inMemoryDatabaseBuilder(context, BingeeDatabase::class.java).build()
        }
    }

    private fun media(title: String, type: MediaType, now: Instant) = MediaEntity(
        mediaType = type,
        title = title,
        originalTitle = null,
        overview = "Overview",
        posterUrl = "https://image.example/poster.jpg",
        releaseDate = LocalDate.of(2026, 8, 1),
        createdAt = now,
        metadataUpdatedAt = now
    )

    private fun rowCount(table: String): Int =
        database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }
}

private suspend fun <T> kotlinx.coroutines.flow.Flow<T>.firstValue(): T = first()
