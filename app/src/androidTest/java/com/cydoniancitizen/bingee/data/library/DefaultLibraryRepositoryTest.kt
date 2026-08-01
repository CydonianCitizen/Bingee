package com.cydoniancitizen.bingee.data.library

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.LibraryEntry
import com.cydoniancitizen.bingee.core.model.MediaSearchResult
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.core.result.AppError
import com.cydoniancitizen.bingee.core.result.AppResult
import com.cydoniancitizen.bingee.data.library.local.BingeeDatabase
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DefaultLibraryRepositoryTest {
    private lateinit var database: BingeeDatabase
    private lateinit var repository: DefaultLibraryRepository
    private val now = Instant.parse("2026-08-01T10:00:00Z")

    @Before
    fun createRepository() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BingeeDatabase::class.java).build()
        repository =
            DefaultLibraryRepository(
                database.libraryDao(),
                Clock.fixed(now, ZoneOffset.UTC)
            )
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun addMapsDomainMetadataAndMembershipWithoutNetworkOrCredential() = runBlocking {
        val result = mediaResult()

        val added = repository.add(result)

        val entry = (added as AppResult.Success).value
        assertEquals(result.externalRef, entry.mediaRef)
        assertEquals("Arrival", entry.title)
        assertEquals(now, entry.addedAt)
        assertEquals(AppResult.Success(true), repository.isInLibrary(result.externalRef))
        assertEquals(AppResult.Success(setOf(result.externalRef)), repository.observeMembershipRefs().first())
        assertEquals(AppResult.Success(listOf(entry)), repository.observeEntries(MediaType.MOVIE).first())
        assertEquals(
            AppResult.Success(emptyList<LibraryEntry>()),
            repository.observeEntries(MediaType.SERIES).first()
        )
    }

    @Test
    fun removeIsIdempotentAndOnlyChangesMembership() = runBlocking {
        val result = mediaResult()
        repository.add(result)

        assertEquals(AppResult.Success(Unit), repository.remove(result.externalRef))
        assertEquals(AppResult.Success(Unit), repository.remove(result.externalRef))
        assertEquals(AppResult.Success(false), repository.isInLibrary(result.externalRef))
        assertEquals(AppResult.Success(emptyList<LibraryEntry>()), repository.observeEntries().first())
        assertTrue(database.libraryDao().getMediaByExternalRef(MediaSource.TMDB, "42") != null)
    }

    @Test
    fun providerIdentityWhitespaceIsNormalizedBeforePersistence() = runBlocking {
        val result = mediaResult().copy(externalRef = ExternalMediaRef(MediaSource.TMDB, " 42 "))

        val added = repository.add(result) as AppResult.Success

        assertEquals(ExternalMediaRef(MediaSource.TMDB, "42"), added.value.mediaRef)
        assertTrue(database.libraryDao().isInLibrary(MediaSource.TMDB, "42"))
    }

    @Test
    fun malformedPersistedEnumBecomesSafeCorruptedDataError() = runBlocking {
        repository.add(mediaResult())
        database.openHelper.writableDatabase.execSQL(
            "UPDATE media_entries SET media_type = 'BROKEN'"
        )

        assertEquals(
            AppResult.Failure(AppError.CorruptedData),
            repository.observeEntries().first()
        )
    }

    private fun mediaResult() = MediaSearchResult(
        externalRef = ExternalMediaRef(MediaSource.TMDB, "42"),
        mediaType = MediaType.MOVIE,
        title = " Arrival ",
        originalTitle = "Arrival",
        posterUrl = "https://image.example/42.jpg",
        releaseDate = LocalDate.of(2016, 11, 11),
        overview = "First contact."
    )
}
