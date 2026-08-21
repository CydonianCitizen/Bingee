package com.cydoniancitizen.bingee.data.library

import android.content.Context
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cydoniancitizen.bingee.core.designsystem.theme.BingeeTheme
import com.cydoniancitizen.bingee.core.model.EpisodePosition
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
import com.cydoniancitizen.bingee.data.library.local.ExternalRefEntity
import com.cydoniancitizen.bingee.data.library.local.MediaDetailsEntity
import com.cydoniancitizen.bingee.data.library.local.MediaGenreEntity
import com.cydoniancitizen.bingee.data.library.local.SeasonEntity
import com.cydoniancitizen.bingee.domain.model.TasteStatistics
import com.cydoniancitizen.bingee.domain.model.calculateWatchedStatistics
import com.cydoniancitizen.bingee.feature.profile.StatisticsContent
import com.cydoniancitizen.bingee.testutil.STATISTICS_RATINGS_ITEM
import com.cydoniancitizen.bingee.testutil.TestCalendarDateSource
import com.cydoniancitizen.bingee.testutil.scrollListTo
import com.cydoniancitizen.bingee.testutil.scrollListToItem
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DefaultLibraryRepositoryTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var database: BingeeDatabase
    private lateinit var repository: DefaultLibraryRepository
    private lateinit var dateSource: TestCalendarDateSource
    private val now = Instant.parse("2026-08-01T10:00:00Z")

    @Before
    fun createRepository() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BingeeDatabase::class.java).build()
        dateSource = TestCalendarDateSource(today())
        repository =
            DefaultLibraryRepository(
                database.libraryDao(),
                database.watchProgressDao(),
                database.ratingDao(),
                Clock.fixed(now, ZoneOffset.UTC),
                dateSource
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
    fun continueWatchingUsesLocalProgressAndNextEpisodeWithoutMoviesOrCompletedSeries() = runBlocking {
        val partial = mediaResult().copy(
            externalRef = ExternalMediaRef(MediaSource.TMDB, "100"),
            mediaType = MediaType.SERIES,
            title = "Partial Series"
        )
        val completed = mediaResult().copy(
            externalRef = ExternalMediaRef(MediaSource.TMDB, "200"),
            mediaType = MediaType.SERIES,
            title = "Completed Series"
        )
        val animated = mediaResult().copy(
            externalRef = ExternalMediaRef(MediaSource.TMDB, "300"),
            mediaType = MediaType.SERIES,
            title = "Animated Series"
        )
        repository.add(mediaResult())
        repository.add(partial)
        repository.add(completed)
        repository.add(animated)
        storeSeries("100", 3)
        storeSeries("200", 2)
        storeSeries("300", 2)

        database.watchProgressDao().markEpisodeWatched(MediaSource.TMDB, "100-episode-1", today(), now)
        database.watchProgressDao().markEpisodeWatched(MediaSource.TMDB, "200-episode-1", today(), now)
        database.watchProgressDao().markEpisodeWatched(
            MediaSource.TMDB,
            "200-episode-2",
            today(),
            now.plusSeconds(1)
        )
        database.watchProgressDao().markEpisodeWatched(
            MediaSource.TMDB,
            "300-episode-1",
            today(),
            now.minusSeconds(1)
        )

        val items = (repository.observeContinueWatching().first() as AppResult.Success).value

        assertEquals(
            listOf(partial.externalRef, animated.externalRef),
            items.map { it.mediaRef }
        )
        assertEquals(1, items.first().progress.watchedEpisodes)
        assertEquals(EpisodePosition(1, 2), items.first().nextEpisode)
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

    @Test
    fun removedWatchedMovieRemainsInPersonalViewingWithCompletionTimestamp() = runBlocking {
        val result = mediaResult()
        val watchedDate = LocalDate.of(2020, 2, 3)
        repository.add(result)
        database.watchProgressDao().markMovieWatched(MediaSource.TMDB, "42", now)
        repository.setWatchedDate(result.externalRef, watchedDate)
        database.ratingDao().setRating(MediaSource.TMDB, "42", 9, now)

        repository.remove(result.externalRef)

        val history = (repository.observePersonalViewing().first() as AppResult.Success).value.single()
        assertEquals(result.externalRef, history.mediaRef)
        assertEquals(now, history.movieWatchedAt)
        assertEquals(now, history.completionTimestamp)
        assertEquals(watchedDate, history.watchedDate)
        assertEquals(PersonalRating(9), history.personalRating)
        assertEquals(now, history.personalRatingUpdatedAt)
        assertEquals(result.releaseDate, history.releaseDate)
        assertFalse(history.inLibrary)
        assertTrue(history.isCompletedTitle)
        assertTrue(history.isViewingTasteEligible)
        assertTrue((repository.observeEntries().first() as AppResult.Success).value.isEmpty())
    }

    @Test
    fun personalViewingProjectionReturnsOneCanonicalRowForMultipleExternalRefs() = runBlocking {
        val result = mediaResult()
        repository.add(result)
        repository.setFavorite(result.externalRef, true)
        database.watchProgressDao().markMovieWatched(MediaSource.TMDB, "42", now)
        database.ratingDao().setRating(MediaSource.TMDB, "42", 8, now)
        val localMediaId = database.portableSnapshotDao().readSnapshot().refs.single().localMediaId
        database.portableSnapshotDao().insertExternalRef(
            ExternalRefEntity(localMediaId, MediaSource.IMDB, "tt2543164")
        )

        repository.remove(result.externalRef)

        val entries = (repository.observePersonalViewing().first() as AppResult.Success).value
        val entry = entries.single()
        assertEquals(1, entries.size)
        assertEquals(ExternalMediaRef(MediaSource.TMDB, "42"), entry.mediaRef)
        assertEquals(ExternalMediaRef(MediaSource.TMDB, "42"), entry.navigableDetailsRef)
        assertTrue(entry.isFavorite)
        assertEquals(PersonalRating(8), entry.personalRating)
        assertEquals(now, entry.personalRatingUpdatedAt)
        assertEquals(now, entry.movieWatchedAt)
        assertTrue(entry.isViewingTasteEligible)
        assertFalse(entry.inLibrary)

        var opened: ExternalMediaRef? = null
        composeRule.setContent {
            BingeeTheme {
                StatisticsContent(
                    statistics = calculateWatchedStatistics(listOf(entry)),
                    tasteStatistics = TasteStatistics(),
                    onScopeChanged = {},
                    onOpenDetails = { reference, _ -> opened = reference }
                )
            }
        }

        composeRule.scrollListTo(hasContentDescription("Rating 8, 1 title")).performClick()
        composeRule.scrollListToItem(STATISTICS_RATINGS_ITEM)
        composeRule.scrollListTo(hasContentDescription("Arrival, Movie · 2016")).performClick()

        assertEquals(ExternalMediaRef(MediaSource.TMDB, "42"), opened)
    }

    @Test
    fun removedCompletedSeriesRetainsGenuineCompletionHistory() = runBlocking {
        val series = mediaResult().copy(
            externalRef = ExternalMediaRef(MediaSource.TMDB, "100"),
            mediaType = MediaType.SERIES,
            title = "Completed Series"
        )
        repository.add(series)
        storeSeries("100", 2)
        database.watchProgressDao().markEpisodeWatched(MediaSource.TMDB, "100-episode-1", today(), now)
        val completedAt = now.plusSeconds(1)
        database.watchProgressDao().markEpisodeWatched(
            MediaSource.TMDB,
            "100-episode-2",
            today(),
            completedAt
        )

        repository.remove(series.externalRef)

        val history = (repository.observePersonalViewing().first() as AppResult.Success).value.single()
        assertEquals(completedAt, history.seriesCompletedAt)
        assertEquals(2, history.watchedRegularEpisodes)
        assertFalse(history.inLibrary)
        assertTrue(history.isCompletedTitle)
        assertTrue(history.isViewingTasteEligible)
    }

    @Test
    fun removedIncompleteSeriesKeepsRegularActivityWithoutCompletion() = runBlocking {
        val series = mediaResult().copy(
            externalRef = ExternalMediaRef(MediaSource.TMDB, "100"),
            mediaType = MediaType.SERIES,
            title = "Incomplete Series"
        )
        repository.add(series)
        storeSeries("100", 2)
        database.watchProgressDao().markEpisodeWatched(MediaSource.TMDB, "100-episode-1", today(), now)
        repository.setSeriesAbandoned(series.externalRef, true)

        repository.remove(series.externalRef)

        val history = (repository.observePersonalViewing().first() as AppResult.Success).value.single()
        assertEquals(1, history.watchedRegularEpisodes)
        assertNull(history.seriesCompletedAt)
        assertFalse(history.isCompletedTitle)
        assertTrue(history.isViewingTasteEligible)
        assertFalse(history.isAbandoned)
        assertFalse(history.inLibrary)
    }

    @Test
    fun specialsOnlyProgressDoesNotEnterPersonalViewingCohorts() = runBlocking {
        val series = mediaResult().copy(
            externalRef = ExternalMediaRef(MediaSource.TMDB, "100"),
            mediaType = MediaType.SERIES,
            title = "Specials Only"
        )
        repository.add(series)
        database.seriesDao().storeSeasonEpisodes(
            MediaSource.TMDB,
            "100",
            SeasonEntity(
                localMediaId = 0,
                source = MediaSource.TMDB,
                externalId = "specials",
                seasonNumber = 0,
                name = "Specials",
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
                    externalId = "special-1",
                    episodeNumber = 1,
                    title = "Special",
                    overview = null,
                    airDate = null,
                    runtimeMinutes = null,
                    stillUrl = null,
                    metadataUpdatedAt = now
                )
            ),
            now
        )
        database.watchProgressDao().markEpisodeWatched(MediaSource.TMDB, "special-1", today(), now)

        assertTrue((repository.observePersonalViewing().first() as AppResult.Success).value.isEmpty())
    }

    @Test
    fun unwatchedSpecialDoesNotMakeCaughtUpRegularSeriesActionable() = runBlocking {
        val series = mediaResult().copy(
            externalRef = ExternalMediaRef(MediaSource.TMDB, "100"),
            mediaType = MediaType.SERIES,
            title = "Regularly Caught Up"
        )
        repository.add(series)
        storeSeries("100", 2)
        (1..2).forEach { number ->
            database.watchProgressDao().markEpisodeWatched(
                MediaSource.TMDB,
                "100-episode-$number",
                today(),
                now
            )
        }
        database.seriesDao().storeSeasonEpisodes(
            MediaSource.TMDB,
            "100",
            SeasonEntity(
                localMediaId = 0,
                source = MediaSource.TMDB,
                externalId = "caught-up-specials",
                seasonNumber = 0,
                name = "Specials",
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
                    externalId = "caught-up-special-1",
                    episodeNumber = 1,
                    title = "Special",
                    overview = null,
                    airDate = null,
                    runtimeMinutes = null,
                    stillUrl = null,
                    metadataUpdatedAt = now
                )
            ),
            now
        )

        val entry = (repository.observeEntries().first() as AppResult.Success).value.single()
        val progress = (entry.progress as LibraryProgress.Series).progress
        assertEquals(2, progress.watchedEpisodes)
        assertEquals(2, progress.trackableEpisodes)
        assertTrue((repository.observeContinueWatching().first() as AppResult.Success).value.isEmpty())
        assertEquals(
            2,
            (repository.observePersonalViewing().first() as AppResult.Success).value.single()
                .watchedRegularEpisodes
        )
    }

    @Test
    fun personalViewingProjectsPersistedRuntimeAndCanonicalGenres() = runBlocking {
        val movie = mediaResult()
        repository.add(movie)
        database.detailsDao().storeDetails(
            candidate = checkNotNull(database.libraryDao().getMediaByExternalRef(MediaSource.TMDB, "42")),
            source = MediaSource.TMDB,
            externalId = "42",
            details = MediaDetailsEntity(
                localMediaId = 0,
                backdropUrl = null,
                productionStatus = "RELEASED",
                originalLanguage = "en",
                runtimeMinutes = 137,
                episodeRuntimeMinutes = null,
                numberOfSeasons = null,
                numberOfEpisodes = null,
                detailsFetchedAt = now
            ),
            genres = listOf(
                MediaGenreEntity(0, 0, "Drama", MediaSource.TMDB, 18),
                MediaGenreEntity(0, 1, "Dramma", MediaSource.TMDB, 18)
            )
        )
        database.watchProgressDao().markMovieWatched(MediaSource.TMDB, "42", now)

        val history = (repository.observePersonalViewing().first() as AppResult.Success).value.single()

        assertEquals(137, history.movieRuntimeMinutes)
        assertEquals(0L, history.watchedRegularRuntimeMinutes)
        // "Drama" and "Dramma" are one canonical genre (TMDB, 18), so the title carries it once and
        // keeps the first persisted localisation rather than projecting a second row for the alias.
        assertEquals(listOf(18L), history.genres.mapNotNull { it.genreId })
        assertEquals(listOf("Drama"), history.genres.map { it.name })
    }

    @Test
    fun personalViewingUsesCurrentProgressWhenNewEpisodeAppears() = runBlocking {
        val series = mediaResult().copy(
            externalRef = ExternalMediaRef(MediaSource.TMDB, "100"),
            mediaType = MediaType.SERIES,
            title = "Dynamic Series"
        )
        repository.add(series)
        storeSeries("100", 2, runtimes = listOf(42, 48))
        database.watchProgressDao().markEpisodeWatched(MediaSource.TMDB, "100-episode-1", today(), now)
        database.watchProgressDao().markEpisodeWatched(MediaSource.TMDB, "100-episode-2", today(), now)

        var history = (repository.observePersonalViewing().first() as AppResult.Success).value.single()
        assertTrue(history.isCompletedTitle)
        assertEquals(90L, history.watchedRegularRuntimeMinutes)

        storeSeries("100", 3, runtimes = listOf(42, 48, 51))

        history = (repository.observePersonalViewing().first() as AppResult.Success).value.single()
        assertFalse(history.isCompletedTitle)
        assertEquals(2, history.watchedRegularEpisodes)
    }

    @Test
    fun currentAvailabilityReevaluatesOnLiveLocalDateRollover() = runBlocking {
        val initialDate = LocalDate.of(2026, 8, 18)
        val nextDate = initialDate.plusDays(1)
        dateSource.advanceTo(initialDate)
        val series = mediaResult().copy(
            externalRef = ExternalMediaRef(MediaSource.TMDB, "100"),
            mediaType = MediaType.SERIES,
            title = "Midnight Series"
        )
        repository.add(series)
        storeSeries(
            seriesId = "100",
            episodeCount = 11,
            airDates = List<LocalDate?>(10) { null } + nextDate
        )
        (1..10).forEach { number ->
            database.watchProgressDao().markEpisodeWatched(
                MediaSource.TMDB,
                "100-episode-$number",
                initialDate,
                now
            )
        }

        val entries = repository.observeEntries().produceIn(this)
        val continueWatching = repository.observeContinueWatching().produceIn(this)
        val personalViewing = repository.observePersonalViewing().produceIn(this)

        val initialEntry = entries.nextMatching { result ->
            result is AppResult.Success && result.value.singleOrNull()?.progress is LibraryProgress.Series
        }
        val initialProgress = (
            (initialEntry as AppResult.Success).value.single().progress as LibraryProgress.Series
            ).progress
        assertEquals(10, initialProgress.trackableEpisodes)
        assertEquals(10, initialProgress.watchedEpisodes)
        assertTrue(initialProgress.isComplete)
        val initialContinueWatching = continueWatching.nextMatching { it is AppResult.Success }
        assertTrue(initialContinueWatching is AppResult.Success && initialContinueWatching.value.isEmpty())
        val initialViewing = personalViewing.nextMatching { it is AppResult.Success } as AppResult.Success
        assertTrue(initialViewing.value.single().seriesIsCurrentlyComplete == true)

        dateSource.advanceTo(nextDate)

        val rolledEntry = entries.nextMatching { result ->
            result is AppResult.Success && result.value.singleOrNull()?.let { entry ->
                (entry.progress as? LibraryProgress.Series)?.progress?.let { progress ->
                    progress.trackableEpisodes == 11 && progress.watchedEpisodes == 10 && !progress.isComplete
                } == true
            } == true
        }
        assertTrue(rolledEntry is AppResult.Success)
        val rolledContinueWatching = continueWatching.nextMatching { result ->
            result is AppResult.Success && result.value.any { it.mediaRef == series.externalRef }
        }
        assertTrue(rolledContinueWatching is AppResult.Success)
        val rolledViewing = personalViewing.nextMatching { result ->
            result is AppResult.Success && result.value.single().seriesIsCurrentlyComplete == false
        }
        assertTrue(rolledViewing is AppResult.Success)

        database.watchProgressDao().markEpisodeWatched(
            MediaSource.TMDB,
            "100-episode-11",
            nextDate,
            now.plusSeconds(1)
        )

        val completedEntry = entries.nextMatching { result ->
            result is AppResult.Success && result.value.singleOrNull()?.let { entry ->
                (entry.progress as? LibraryProgress.Series)?.progress?.let { progress ->
                    progress.trackableEpisodes == 11 && progress.watchedEpisodes == 11 && progress.isComplete
                } == true
            } == true
        }
        assertTrue(completedEntry is AppResult.Success)
        val completedContinueWatching = continueWatching.nextMatching { it is AppResult.Success }
        assertTrue(completedContinueWatching is AppResult.Success && completedContinueWatching.value.isEmpty())
        val completedViewing = personalViewing.nextMatching { result ->
            result is AppResult.Success && result.value.single().seriesIsCurrentlyComplete == true
        }
        assertTrue(completedViewing is AppResult.Success)

        entries.cancel()
        continueWatching.cancel()
        personalViewing.cancel()
    }

    @Test
    fun historicalEpisodeActivitySurvivesFutureAirDateCorrectionAndKeepsAvailabilitySeparate() = runBlocking {
        val series = mediaResult().copy(
            externalRef = ExternalMediaRef(MediaSource.TMDB, "100"),
            mediaType = MediaType.SERIES,
            title = "Corrected History Series"
        )
        repository.add(series)

        val futureDate = today().plusDays(30)
        val firstWatchedAt = Instant.parse("2026-03-10T10:00:00Z")
        val secondWatchedAt = Instant.parse("2026-03-11T10:00:00Z")
        storeSeries(
            seriesId = "100",
            episodeCount = 3,
            runtimes = listOf(42, 48, 60),
            airDates = listOf(today().minusDays(1), null, futureDate)
        )
        database.watchProgressDao().markEpisodeWatched(
            MediaSource.TMDB,
            "100-episode-1",
            today(),
            firstWatchedAt
        )
        database.watchProgressDao().markEpisodeWatched(
            MediaSource.TMDB,
            "100-episode-2",
            today(),
            secondWatchedAt
        )

        database.seriesDao().storeSeasonEpisodes(
            MediaSource.TMDB,
            "100",
            SeasonEntity(
                localMediaId = 0,
                source = MediaSource.TMDB,
                externalId = "100-season-0",
                seasonNumber = 0,
                name = "Specials",
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
                    externalId = "100-special-1",
                    episodeNumber = 1,
                    title = "Special",
                    overview = null,
                    airDate = null,
                    runtimeMinutes = 30,
                    stillUrl = null,
                    metadataUpdatedAt = now
                )
            ),
            now
        )
        database.watchProgressDao().markEpisodeWatched(MediaSource.TMDB, "100-special-1", today(), now)

        storeSeries(
            seriesId = "100",
            episodeCount = 3,
            runtimes = listOf(42, 48, 60),
            airDates = listOf(futureDate, null, futureDate)
        )

        val refreshed = database.seriesDao().observeSeason(MediaSource.TMDB, "100", "100-season-1").first()!!
        assertEquals(futureDate, refreshed.episodes[0].episode.airDate)
        assertNull(refreshed.episodes[1].episode.airDate)
        assertEquals(firstWatchedAt, refreshed.episodes[0].progress?.watchedAt)

        val history = (repository.observePersonalViewing().first() as AppResult.Success).value.single()
        assertEquals(2, history.watchedRegularEpisodes)
        assertEquals(90L, history.watchedRegularRuntimeMinutes)
        assertEquals(
            listOf(firstWatchedAt, secondWatchedAt),
            history.watchedRegularEpisodeActivities.map { it.watchedAt }
        )
        assertTrue(history.isViewingTasteEligible)

        val statistics = calculateWatchedStatistics(listOf(history), ZoneOffset.UTC, today(), 2026)
        assertEquals(2, statistics.episodesWatchedCount)
        assertEquals(90L, statistics.seriesWatchTimeMinutes)
        assertEquals(90L, statistics.monthlyViewing.months[2].seriesMinutes)

        val localMediaId = database.libraryDao().getMediaByExternalRef(MediaSource.TMDB, "100")!!.localMediaId
        val currentProgress = database.libraryDao().observeLibraryProgress(today()).first()
            .single { it.localMediaId == localMediaId }
        assertEquals(1, currentProgress.trackableEpisodes)
        assertEquals(1, currentProgress.watchedEpisodes)
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

    private suspend fun storeSeries(
        seriesId: String,
        episodeCount: Int,
        runtimes: List<Int?> = List(episodeCount) { null },
        airDates: List<LocalDate?> = List(episodeCount) { null }
    ) {
        database.seriesDao().storeSeasonEpisodes(
            MediaSource.TMDB,
            seriesId,
            SeasonEntity(
                localMediaId = 0,
                source = MediaSource.TMDB,
                externalId = "$seriesId-season-1",
                seasonNumber = 1,
                name = "Season 1",
                overview = null,
                posterUrl = null,
                airDate = null,
                episodeCount = episodeCount,
                metadataUpdatedAt = now,
                episodesFetchedAt = null
            ),
            (1..episodeCount).map { number ->
                EpisodeEntity(
                    localSeasonId = 0,
                    source = MediaSource.TMDB,
                    externalId = "$seriesId-episode-$number",
                    episodeNumber = number,
                    title = "Episode $number",
                    overview = null,
                    airDate = airDates.getOrNull(number - 1),
                    runtimeMinutes = runtimes.getOrNull(number - 1),
                    stillUrl = null,
                    metadataUpdatedAt = now
                )
            },
            now
        )
    }

    private fun today(): LocalDate = LocalDate.of(2026, 8, 1)

    private suspend fun <T> ReceiveChannel<T>.nextMatching(predicate: (T) -> Boolean): T =
        withTimeout(2_000) { receiveAsFlow().first(predicate) }
}
