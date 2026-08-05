package com.cydoniancitizen.bingee.data.jikan.progress

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cydoniancitizen.bingee.core.model.AnimeCompletionOrigin
import com.cydoniancitizen.bingee.core.model.AnimeFormat
import com.cydoniancitizen.bingee.core.model.AnimeStatus
import com.cydoniancitizen.bingee.core.model.AnimeWatchProgress
import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.core.result.AppError
import com.cydoniancitizen.bingee.core.result.AppResult
import com.cydoniancitizen.bingee.data.library.local.AnimeDetailsEntity
import com.cydoniancitizen.bingee.data.library.local.BingeeDatabase
import com.cydoniancitizen.bingee.data.library.local.MediaEntity
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
class AnimeProgressRoomTest {
    private lateinit var database: BingeeDatabase
    private lateinit var repository: DefaultAnimeProgressRepository
    private val now = Instant.parse("2026-08-05T10:00:00Z")
    private val animeRef = ExternalMediaRef(MediaSource.JIKAN, "52991")

    @Before
    fun createRepository() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BingeeDatabase::class.java).build()
        repository = DefaultAnimeProgressRepository(
            database.animeDao(),
            Clock.fixed(now, ZoneOffset.UTC)
        )
    }

    @After
    fun closeDatabase() = database.close()

    @Test
    fun knownTotalPersistsIncrementDecrementAndCompletionTransitions() = runBlocking {
        storeAnime(animeRef, AnimeFormat.TV, 3)

        assertEquals(
            AppResult.Success<AnimeWatchProgress?>(null),
            repository.observe(animeRef).first()
        )
        assertEquals(AppResult.Success(Unit), repository.setCount(animeRef, 2))
        assertEquals(AppResult.Success(Unit), repository.increment(animeRef))
        var progress = (repository.observe(animeRef).first() as AppResult.Success).value!!
        assertEquals(3, progress.watchedEpisodes)
        assertEquals(AnimeCompletionOrigin.INFERRED, progress.completionOrigin)

        assertEquals(AppResult.Success(Unit), repository.decrement(animeRef))
        progress = (repository.observe(animeRef).first() as AppResult.Success).value!!
        assertEquals(2, progress.watchedEpisodes)
        assertEquals(null, progress.completionOrigin)

        assertEquals(AppResult.Success(Unit), repository.markComplete(animeRef))
        assertEquals(AnimeCompletionOrigin.EXPLICIT, progress(animeRef).completionOrigin)
        assertEquals(AppResult.Success(Unit), repository.markIncomplete(animeRef))
        assertEquals(2, progress(animeRef).watchedEpisodes)
        assertFalse(progress(animeRef).completionOrigin == AnimeCompletionOrigin.EXPLICIT)
    }

    @Test
    fun movieIsBoundedAndProviderInputIsRejected() = runBlocking {
        storeAnime(ExternalMediaRef(MediaSource.JIKAN, "600"), AnimeFormat.MOVIE, 1)
        val movie = ExternalMediaRef(MediaSource.JIKAN, "600")

        assertEquals(AppResult.Success(Unit), repository.markComplete(movie))
        assertEquals(1, progress(movie).watchedEpisodes)
        assertEquals(AppResult.Success(Unit), repository.markIncomplete(movie))
        assertEquals(0, progress(movie).watchedEpisodes)
        assertEquals(
            AppResult.Failure(AppError.InvalidInput),
            repository.setCount(movie, 2)
        )
        assertEquals(
            AppResult.Failure(AppError.InvalidInput),
            repository.increment(ExternalMediaRef(MediaSource.TMDB, "600"))
        )
    }

    @Test
    fun removalAndReAddRetainProgressAndRatingWithoutProviderMerge() = runBlocking {
        storeAnime(animeRef, AnimeFormat.TV, 12)
        database.libraryDao().addExistingToLibrary(MediaSource.JIKAN, animeRef.externalId, now)
        repository.setCount(animeRef, 5)
        database.ratingDao().setRating(MediaSource.JIKAN, animeRef.externalId, 8, now)

        database.libraryDao().removeMembership(MediaSource.JIKAN, animeRef.externalId)
        assertEquals(5, progress(animeRef).watchedEpisodes)
        assertEquals(8, database.ratingDao().observeRating(MediaSource.JIKAN, animeRef.externalId).first()?.ratingValue)
        database.libraryDao().addExistingToLibrary(MediaSource.JIKAN, animeRef.externalId, now)
        assertTrue(database.libraryDao().isInLibrary(MediaSource.JIKAN, animeRef.externalId))
        assertEquals(
            8,
            database.ratingDao().observeRating(MediaSource.JIKAN, animeRef.externalId).first()?.ratingValue
        )
        assertFalse(database.libraryDao().isInLibrary(MediaSource.TMDB, animeRef.externalId))
    }

    private suspend fun progress(ref: ExternalMediaRef) = (repository.observe(ref).first() as AppResult.Success).value!!

    private suspend fun storeAnime(ref: ExternalMediaRef, format: AnimeFormat, total: Int) {
        database.animeDao().storeAnime(
            MediaEntity(
                mediaType = MediaType.ANIME,
                title = "Anime ${ref.externalId}",
                originalTitle = null,
                overview = null,
                posterUrl = null,
                releaseDate = LocalDate.of(2026, 1, 1),
                createdAt = now,
                metadataUpdatedAt = now
            ),
            ref.externalId,
            AnimeDetailsEntity(
                localMediaId = 0,
                format = format,
                providerStatus = AnimeStatus.FINISHED,
                englishTitle = "Anime ${ref.externalId}",
                japaneseTitle = null,
                synopsis = null,
                episodeCount = total,
                duration = null,
                startDate = LocalDate.of(2026, 1, 1),
                endDate = null,
                season = null,
                year = 2026,
                providerScore = null,
                imageUrl = null,
                detailsUpdatedAt = now
            ),
            emptyList()
        )
    }
}
