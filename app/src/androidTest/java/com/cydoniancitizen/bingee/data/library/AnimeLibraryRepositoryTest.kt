package com.cydoniancitizen.bingee.data.library

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cydoniancitizen.bingee.core.model.AnimeFormat
import com.cydoniancitizen.bingee.core.model.AnimeStatus
import com.cydoniancitizen.bingee.core.model.AnimeWatchProgress
import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.LibraryMediaFilter
import com.cydoniancitizen.bingee.core.model.LibraryProgress
import com.cydoniancitizen.bingee.core.model.LibraryQuery
import com.cydoniancitizen.bingee.core.model.LibrarySort
import com.cydoniancitizen.bingee.core.model.MediaSearchResult
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.core.model.PersonalRating
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AnimeLibraryRepositoryTest {
    private lateinit var database: BingeeDatabase
    private lateinit var repository: DefaultLibraryRepository
    private val now = Instant.parse("2026-08-05T10:00:00Z")
    private val animeRef = ExternalMediaRef(MediaSource.JIKAN, "550")
    private val tmdbMovieRef = ExternalMediaRef(MediaSource.TMDB, "550")

    @Before
    fun createRepository() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BingeeDatabase::class.java).build()
        repository = DefaultLibraryRepository(
            database.libraryDao(),
            database.ratingDao(),
            Clock.fixed(now, ZoneOffset.UTC)
        )
    }

    @After
    fun closeDatabase() = database.close()

    @Test
    fun mixedProvidersRemainSeparateAndAllFiltersAndLocalizedSearchAreCorrect() = runBlocking {
        storeAnime()
        repository.add(animeRef)
        repository.add(
            MediaSearchResult(
                externalRef = tmdbMovieRef,
                mediaType = MediaType.MOVIE,
                title = "Arrival",
                originalTitle = "Arrival"
            )
        )
        repository.add(
            MediaSearchResult(
                externalRef = ExternalMediaRef(MediaSource.TMDB, "100"),
                mediaType = MediaType.SERIES,
                title = "Blue Series",
                originalTitle = "Blue Series"
            )
        )

        val all = entries()
        assertEquals(3, all.size)
        assertEquals(
            setOf(tmdbMovieRef, ExternalMediaRef(MediaSource.TMDB, "100"), animeRef),
            all.map { it.mediaRef }.toSet()
        )
        assertEquals(MediaType.ANIME, all.single { it.mediaRef == animeRef }.mediaType)
        assertEquals(listOf("Arrival"), entries(LibraryQuery(mediaFilter = LibraryMediaFilter.MOVIES)).map { it.title })
        assertEquals(
            listOf("Blue Series"),
            entries(LibraryQuery(mediaFilter = LibraryMediaFilter.TV_SERIES)).map { it.title }
        )
        assertEquals(
            listOf("Anime Primary"),
            entries(LibraryQuery(mediaFilter = LibraryMediaFilter.ANIME)).map {
                it.title
            }
        )
        assertEquals(listOf("Anime Primary"), entries(LibraryQuery(searchQuery = "English Anime")).map { it.title })
        assertEquals(listOf("Anime Primary"), entries(LibraryQuery(searchQuery = "日本語作品")).map { it.title })
        assertEquals(
            listOf("Anime Primary", "Arrival", "Blue Series"),
            entries(LibraryQuery(sort = LibrarySort.TITLE)).map { it.title }
        )
    }

    @Test
    fun progressAndRatingOrderingAreProviderAwareAndOffline() = runBlocking {
        storeAnime()
        repository.add(animeRef)
        repository.add(
            MediaSearchResult(tmdbMovieRef, MediaType.MOVIE, "Arrival")
        )
        repository.add(
            MediaSearchResult(
                ExternalMediaRef(MediaSource.TMDB, "100"),
                MediaType.SERIES,
                "Blue Series"
            )
        )
        database.animeDao().setProgress(
            MediaSource.JIKAN,
            "550",
            AnimeWatchProgress(5, null, null, now)
        )
        database.ratingDao().setRating(MediaSource.JIKAN, "550", 10, now)
        database.ratingDao().setRating(MediaSource.TMDB, "550", 8, now)

        val byProgress = entries(LibraryQuery(sort = LibrarySort.PROGRESS))
        assertEquals(animeRef, byProgress.first().mediaRef)
        assertTrue(byProgress.first().progress is LibraryProgress.Anime)
        assertEquals(
            listOf(animeRef, tmdbMovieRef),
            entries(LibraryQuery(sort = LibrarySort.PERSONAL_RATING))
                .take(2)
                .map { it.mediaRef }
        )
        assertTrue(entries().all { it.title.isNotBlank() })
    }

    @Test
    fun removalAndReAddRetainAnimeProgressRatingAndSeparateIdentity() = runBlocking {
        storeAnime()
        repository.add(animeRef)
        database.animeDao().setProgress(
            MediaSource.JIKAN,
            "550",
            AnimeWatchProgress(5, null, null, now)
        )
        database.ratingDao().setRating(MediaSource.JIKAN, "550", 8, now)

        assertEquals(AppResult.Success(Unit), repository.remove(animeRef))
        assertTrue(entries().isEmpty())
        val reAdded = repository.add(animeRef) as AppResult.Success
        assertEquals(animeRef, reAdded.value.mediaRef)

        val restored = entries().single()
        assertEquals(animeRef, restored.mediaRef)
        assertEquals(PersonalRating(8), restored.personalRating)
        assertEquals(LibraryProgress.Anime(5, 12, false), restored.progress)
        assertTrue(database.libraryDao().isInLibrary(MediaSource.JIKAN, "550"))
    }

    private suspend fun entries(query: LibraryQuery = LibraryQuery()) =
        (repository.observeEntries(query).first() as AppResult.Success).value

    private suspend fun storeAnime() {
        database.animeDao().storeAnime(
            media = MediaEntity(
                mediaType = MediaType.ANIME,
                title = "Anime Primary",
                originalTitle = "日本語作品",
                overview = "Offline fixture",
                posterUrl = null,
                releaseDate = LocalDate.of(2025, 1, 1),
                createdAt = now,
                metadataUpdatedAt = now
            ),
            externalId = "550",
            details = AnimeDetailsEntity(
                localMediaId = 0,
                format = AnimeFormat.TV,
                providerStatus = AnimeStatus.FINISHED,
                englishTitle = "English Anime",
                japaneseTitle = "日本語作品",
                synopsis = "Offline fixture",
                episodeCount = 12,
                duration = "24 min",
                startDate = LocalDate.of(2025, 1, 1),
                endDate = LocalDate.of(2025, 3, 26),
                season = "winter",
                year = 2025,
                providerScore = 8.5,
                imageUrl = null,
                detailsUpdatedAt = now
            ),
            relations = emptyList()
        )
    }
}
