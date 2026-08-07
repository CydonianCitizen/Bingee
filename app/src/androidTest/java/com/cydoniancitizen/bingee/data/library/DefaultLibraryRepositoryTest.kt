package com.cydoniancitizen.bingee.data.library

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.LibraryEntry
import com.cydoniancitizen.bingee.core.model.LibraryMediaFilter
import com.cydoniancitizen.bingee.core.model.LibraryProgress
import com.cydoniancitizen.bingee.core.model.LibraryQuery
import com.cydoniancitizen.bingee.core.model.MediaSearchResult
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.core.model.MovieWatchState
import com.cydoniancitizen.bingee.core.model.PersonalRating
import com.cydoniancitizen.bingee.core.result.AppError
import com.cydoniancitizen.bingee.core.result.AppResult
import com.cydoniancitizen.bingee.data.library.local.BingeeDatabase
import com.cydoniancitizen.bingee.data.library.local.EpisodeEntity
import com.cydoniancitizen.bingee.data.library.local.SeasonEntity
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
                database.watchProgressDao(),
                database.ratingDao(),
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
        assertEquals(
            AppResult.Success(
                listOf(entry.copy(progress = LibraryProgress.Movie(MovieWatchState.Unwatched)))
            ),
            repository.observeEntries(LibraryQuery(mediaFilter = LibraryMediaFilter.MOVIES)).first()
        )
        assertEquals(
            AppResult.Success(emptyList<LibraryEntry>()),
            repository.observeEntries(LibraryQuery(mediaFilter = LibraryMediaFilter.TV_SERIES)).first()
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

    @Test
    fun movieAndSeriesProgressFlowLocallyWithoutChangingMembershipBehavior() = runBlocking {
        val movie = mediaResult()
        val series = mediaResult().copy(
            externalRef = ExternalMediaRef(MediaSource.TMDB, "100"),
            mediaType = MediaType.SERIES,
            title = "Series"
        )
        repository.add(movie)
        repository.add(series)

        val initial = (repository.observeEntries().first() as AppResult.Success).value
        assertTrue(initial.first { it.mediaRef == series.externalRef }.progress is LibraryProgress.Unavailable)

        database.seriesDao().storeSeasonEpisodes(
            MediaSource.TMDB,
            "100",
            SeasonEntity(
                localMediaId = 0,
                source = MediaSource.TMDB,
                externalId = "11",
                seasonNumber = 1,
                name = "Season 1",
                overview = null,
                posterUrl = null,
                airDate = null,
                episodeCount = 1,
                metadataUpdatedAt = now,
                episodesFetchedAt = null
            ),
            listOf(
                EpisodeEntity(
                    localSeasonId = 0,
                    source = MediaSource.TMDB,
                    externalId = "101",
                    episodeNumber = 1,
                    title = "Episode",
                    overview = null,
                    airDate = null,
                    runtimeMinutes = null,
                    stillUrl = null,
                    metadataUpdatedAt = now
                )
            ),
            now
        )
        database.watchProgressDao().markEpisodeWatched(
            MediaSource.TMDB,
            "101",
            LocalDate.of(2026, 8, 1),
            now
        )
        database.watchProgressDao().markMovieWatched(MediaSource.TMDB, "42", now)

        val updated = (repository.observeEntries().first() as AppResult.Success).value
        assertEquals(
            LibraryProgress.Movie(MovieWatchState.Watched(now)),
            updated.first { it.mediaRef == movie.externalRef }.progress
        )
        val seriesProgress =
            (updated.first { it.mediaRef == series.externalRef }.progress as LibraryProgress.Series).progress
        assertEquals(1, seriesProgress.watchedEpisodes)
        assertTrue(seriesProgress.isComplete)

        repository.remove(series.externalRef)
        assertFalse(
            (repository.observeEntries().first() as AppResult.Success).value
                .any { it.mediaRef == series.externalRef }
        )
        assertEquals(
            now,
            database.seriesDao().observeSeason(MediaSource.TMDB, "100", "11").first()!!
                .episodes.first().progress?.watchedAt
        )
    }

    @Test
    fun ratingSurvivesRemovalAndReAddAndAppearsInLibraryProjection() = runBlocking {
        val result = mediaResult()
        repository.add(result)
        database.ratingDao().setRating(MediaSource.TMDB, "42", 9, now)

        assertEquals(
            PersonalRating(9),
            (repository.observeEntries().first() as AppResult.Success).value.single().personalRating
        )
        repository.remove(result.externalRef)
        assertEquals(0, (repository.observeEntries().first() as AppResult.Success).value.size)
        repository.add(result.externalRef)

        assertEquals(
            PersonalRating(9),
            (repository.observeEntries().first() as AppResult.Success).value.single().personalRating
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
