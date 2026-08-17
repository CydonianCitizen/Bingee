package com.cydoniancitizen.bingee.feature.profile

import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.LibraryEntry
import com.cydoniancitizen.bingee.core.model.LibraryProgress
import com.cydoniancitizen.bingee.core.model.LibraryQuery
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.core.model.MovieWatchState
import com.cydoniancitizen.bingee.core.model.PersonalRating
import com.cydoniancitizen.bingee.core.model.PersonalViewingEntry
import com.cydoniancitizen.bingee.core.model.SeriesProgress
import com.cydoniancitizen.bingee.core.model.isWatched
import com.cydoniancitizen.bingee.core.result.AppError
import com.cydoniancitizen.bingee.core.result.AppResult
import com.cydoniancitizen.bingee.data.settings.ProfileCategory
import com.cydoniancitizen.bingee.data.settings.ProfileCollection
import com.cydoniancitizen.bingee.data.settings.ProfileDisplayModePreferences
import com.cydoniancitizen.bingee.data.settings.ProfileDisplayModes
import com.cydoniancitizen.bingee.data.settings.ProfileViewMode
import com.cydoniancitizen.bingee.domain.repository.LibraryRepository
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun isWatchedAndBelongsToCategoryWorkCorrectly() {
        val watchedMovie = entry("1", MediaType.MOVIE, LibraryProgress.Movie(MovieWatchState.Watched(Instant.EPOCH)))
        val unwatchedMovie = entry("2", MediaType.MOVIE, LibraryProgress.Movie(MovieWatchState.Unwatched))
        val inProgressSeries = entry("3", MediaType.SERIES, LibraryProgress.Series(SeriesProgress(1, 10, 0, 1, false)))
        val notStartedSeries = entry("4", MediaType.SERIES, LibraryProgress.Series(SeriesProgress(0, 10, 0, 1, false)))

        assertTrue(watchedMovie.isWatched())
        assertFalse(unwatchedMovie.isWatched())
        assertFalse(inProgressSeries.isWatched())
        assertFalse(notStartedSeries.isWatched())

        assertTrue(watchedMovie.belongsToCategory(ProfileCategory.MOVIES))
        assertFalse(watchedMovie.belongsToCategory(ProfileCategory.TV_SERIES))
        assertTrue(inProgressSeries.belongsToCategory(ProfileCategory.TV_SERIES))
        assertFalse(inProgressSeries.belongsToCategory(ProfileCategory.MOVIES))
    }

    @Test
    fun profileViewModelFiltersWatchedAndWatchLater() = runTest {
        val watchedMovie = entry("1", MediaType.MOVIE, LibraryProgress.Movie(MovieWatchState.Watched(Instant.EPOCH)))
        val unwatchedMovie = entry("2", MediaType.MOVIE, LibraryProgress.Movie(MovieWatchState.Unwatched))

        val repo = FakeLibraryRepo(listOf(watchedMovie, unwatchedMovie))
        val prefs = FakeDisplayModePrefs()
        val viewModel = ProfileViewModel(repo, prefs)

        testDispatcher.scheduler.advanceUntilIdle()

        // Default collection = WATCHED, category = MOVIES
        assertEquals(1, viewModel.uiState.value.entries.size)
        assertEquals("1", viewModel.uiState.value.entries.first().mediaRef.externalId)

        // Switch to WATCH_LATER
        viewModel.setCollection(ProfileCollection.WATCH_LATER)
        assertEquals(1, viewModel.uiState.value.entries.size)
        assertEquals("2", viewModel.uiState.value.entries.first().mediaRef.externalId)
    }

    @Test
    fun sortingByTitleRatingAndProgressWorks() = runTest {
        val movieA =
            entry(
                "1",
                MediaType.MOVIE,
                LibraryProgress.Movie(MovieWatchState.Watched(Instant.EPOCH)),
                title = "Zebra",
                rating = 5,
                added = Instant.ofEpochMilli(100)
            )
        val movieB =
            entry(
                "2",
                MediaType.MOVIE,
                LibraryProgress.Movie(MovieWatchState.Watched(Instant.EPOCH)),
                title = "Alpha",
                rating = 9,
                added = Instant.ofEpochMilli(200)
            )

        val repo = FakeLibraryRepo(listOf(movieA, movieB))
        val prefs = FakeDisplayModePrefs()
        val viewModel = ProfileViewModel(repo, prefs)

        testDispatcher.scheduler.advanceUntilIdle()

        // Default RECENTLY_ADDED -> movieB (200), movieA (100)
        assertEquals("Alpha", viewModel.uiState.value.entries[0].title)
        assertEquals("Zebra", viewModel.uiState.value.entries[1].title)

        // TITLE -> Alpha, Zebra
        viewModel.setSortOption(ProfileSortOption.TITLE)
        assertEquals("Alpha", viewModel.uiState.value.entries[0].title)
        assertEquals("Zebra", viewModel.uiState.value.entries[1].title)

        // RATING -> Alpha (9), Zebra (5)
        viewModel.setSortOption(ProfileSortOption.RATING)
        assertEquals("Alpha", viewModel.uiState.value.entries[0].title)
    }

    @Test
    fun statisticsUseOneFocusedPersonalViewingObservation() = runTest {
        val watchedMovie = entry("1", MediaType.MOVIE, LibraryProgress.Movie(MovieWatchState.Watched(Instant.EPOCH)))
        val watchedSeries = entry("2", MediaType.SERIES, LibraryProgress.Series(SeriesProgress(1, 10, 0, 1, false)))
        val repo = FakeLibraryRepo(listOf(watchedMovie, watchedSeries))
        val viewModel = ProfileViewModel(repo, FakeDisplayModePrefs())

        testDispatcher.scheduler.advanceUntilIdle()
        val statistics = viewModel.uiState.value.statistics
        assertEquals(1, statistics.moviesWatchedCount)
        assertEquals(1, statistics.episodesWatchedCount)

        viewModel.onSearchQueryChanged("watched")
        assertSame(statistics, viewModel.uiState.value.statistics)
        viewModel.setSortOption(ProfileSortOption.TITLE)
        assertSame(statistics, viewModel.uiState.value.statistics)
        viewModel.setCollection(ProfileCollection.WATCH_LATER)
        assertSame(statistics, viewModel.uiState.value.statistics)
        viewModel.setCategory(ProfileCategory.TV_SERIES)
        assertSame(statistics, viewModel.uiState.value.statistics)
        assertEquals(1, repo.observeEntriesCalls)
        assertEquals(1, repo.observePersonalViewingCalls)
    }

    @Test
    fun removedHistoryAffectsStatisticsWithoutReappearingInCollection() = runTest {
        val removedLibraryEntry = entry(
            "removed",
            MediaType.MOVIE,
            LibraryProgress.Movie(MovieWatchState.Watched(Instant.EPOCH)),
            favorite = true,
            inLibrary = false
        )
        val removed = PersonalViewingEntry(
            mediaRef = ExternalMediaRef(MediaSource.TMDB, "removed"),
            mediaType = MediaType.MOVIE,
            title = "Removed",
            addedAt = Instant.EPOCH,
            inLibrary = false,
            isFavorite = false,
            movieWatchedAt = Instant.EPOCH
        )
        val repo = FakeLibraryRepo(listOf(removedLibraryEntry), listOf(removed))
        val viewModel = ProfileViewModel(repo, FakeDisplayModePrefs())

        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.entries.isEmpty())
        assertEquals(1, viewModel.uiState.value.statistics.moviesWatchedCount)
        assertEquals("removed", viewModel.uiState.value.statistics.recentlyCompletedTitles.single().mediaRef.externalId)
    }

    @Test
    fun removedFavoriteMovieStaysFavoriteButNotWatched() = runTest {
        val removed = entry(
            "removed",
            MediaType.MOVIE,
            LibraryProgress.Movie(MovieWatchState.Watched(Instant.EPOCH)),
            favorite = true,
            inLibrary = false
        )
        val viewModel = ProfileViewModel(FakeLibraryRepo(listOf(removed)), FakeDisplayModePrefs())

        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.entries.isEmpty())
        viewModel.setCollection(ProfileCollection.FAVORITES)
        assertEquals(listOf("removed"), viewModel.uiState.value.entries.map { it.mediaRef.externalId })
    }

    @Test
    fun removedFavoriteSeriesStaysFavoriteWithoutSerialLibraryState() = runTest {
        val removed = entry(
            "removed-series",
            MediaType.SERIES,
            LibraryProgress.Series(SeriesProgress(1, 1, 1, 1, true)),
            favorite = true,
            inLibrary = false
        )
        val viewModel = ProfileViewModel(FakeLibraryRepo(listOf(removed)), FakeDisplayModePrefs())

        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.setCategory(ProfileCategory.TV_SERIES)

        assertTrue(viewModel.uiState.value.entries.isEmpty())
        assertEquals(null, removed.serialState)
        viewModel.setCollection(ProfileCollection.FAVORITES)
        assertEquals(listOf("removed-series"), viewModel.uiState.value.entries.map { it.mediaRef.externalId })
    }

    @Test
    fun clearingRemovedFavoriteRemovesItFromFavorites() = runTest {
        val removed = entry(
            "removed",
            MediaType.MOVIE,
            LibraryProgress.Movie(MovieWatchState.Watched(Instant.EPOCH)),
            favorite = true,
            inLibrary = false
        )
        val repo = FakeLibraryRepo(listOf(removed))
        val viewModel = ProfileViewModel(repo, FakeDisplayModePrefs())

        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.setCollection(ProfileCollection.FAVORITES)
        assertEquals(1, viewModel.uiState.value.entries.size)

        repo.emitEntries(emptyList())
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.entries.isEmpty())
    }

    @Test
    fun statisticsFailureDoesNotBlankCollectionAndOnlyStatisticsRecoveryClearsIt() = runTest {
        val watched = entry(
            "watched",
            MediaType.MOVIE,
            LibraryProgress.Movie(MovieWatchState.Watched(Instant.EPOCH))
        )
        val repo = FakeLibraryRepo(listOf(watched))
        val viewModel = ProfileViewModel(repo, FakeDisplayModePrefs())
        testDispatcher.scheduler.advanceUntilIdle()

        repo.emitViewingResult(AppResult.Failure(AppError.Unknown))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("watched"), viewModel.uiState.value.entries.map { it.mediaRef.externalId })
        assertEquals(AppError.Unknown, viewModel.uiState.value.statisticsError)
        assertEquals(null, viewModel.uiState.value.loadError)
        assertFalse(viewModel.uiState.value.isLoading)

        repo.emitEntries(listOf(watched))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(AppError.Unknown, viewModel.uiState.value.statisticsError)

        repo.emitViewingResult(AppResult.Success(listOfNotNull(toViewing(watched))))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(null, viewModel.uiState.value.statisticsError)
    }

    @Test
    fun collectionFailureDoesNotOverwriteStatisticsError() = runTest {
        val repo = FakeLibraryRepo(emptyList())
        val viewModel = ProfileViewModel(repo, FakeDisplayModePrefs())
        testDispatcher.scheduler.advanceUntilIdle()

        repo.emitViewingResult(AppResult.Failure(AppError.Unknown))
        repo.emitEntriesResult(AppResult.Failure(AppError.LocalStorageFailure))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(AppError.LocalStorageFailure, viewModel.uiState.value.loadError)
        assertEquals(AppError.Unknown, viewModel.uiState.value.statisticsError)
    }

    @Test
    fun repositoryChangesRecalculateStatisticsAndKeepFilteredItemsIndependent() = runTest {
        val first = entry(
            "1",
            MediaType.MOVIE,
            LibraryProgress.Movie(MovieWatchState.Watched(Instant.EPOCH)),
            title = "First 1"
        )
        val second = entry(
            "2",
            MediaType.MOVIE,
            LibraryProgress.Movie(MovieWatchState.Watched(Instant.EPOCH)),
            title = "Second 2"
        )
        val repo = FakeLibraryRepo(listOf(first))
        val viewModel = ProfileViewModel(repo, FakeDisplayModePrefs())

        testDispatcher.scheduler.advanceUntilIdle()
        val initialStatistics = viewModel.uiState.value.statistics
        viewModel.onSearchQueryChanged("First")
        assertEquals(listOf("First 1"), viewModel.uiState.value.entries.map { it.title })

        repo.emit(listOf(first, second))
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotSame(initialStatistics, viewModel.uiState.value.statistics)
        assertEquals(2, viewModel.uiState.value.statistics.moviesWatchedCount)
        assertEquals(listOf("First 1"), viewModel.uiState.value.entries.map { it.title })
    }

    @Test
    fun displayModePersistenceDelegatesToPrefs() = runTest {
        val repo = FakeLibraryRepo(emptyList())
        val prefs = FakeDisplayModePrefs()
        val viewModel = ProfileViewModel(repo, prefs)

        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.setViewMode(ProfileViewMode.GRID)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(ProfileViewMode.GRID, viewModel.uiState.value.currentViewMode)
        assertEquals(ProfileViewMode.GRID, prefs.modes.value.watchedMovies)
    }

    private fun entry(
        id: String,
        type: MediaType,
        progress: LibraryProgress,
        title: String = "Title $id",
        rating: Int? = null,
        added: Instant = Instant.EPOCH,
        favorite: Boolean = false,
        inLibrary: Boolean = true
    ) = LibraryEntry(
        mediaRef = ExternalMediaRef(MediaSource.TMDB, id),
        mediaType = type,
        title = title,
        addedAt = added,
        progress = progress,
        personalRating = rating?.let { PersonalRating(it) },
        isFavorite = favorite,
        inLibrary = inLibrary
    )

    private class FakeLibraryRepo(
        items: List<LibraryEntry>,
        viewing: List<PersonalViewingEntry> = items.mapNotNull(::toViewing)
    ) : LibraryRepository {
        private val observedEntries = MutableStateFlow<AppResult<List<LibraryEntry>>>(AppResult.Success(items))
        private val observedViewing =
            MutableStateFlow<AppResult<List<PersonalViewingEntry>>>(AppResult.Success(viewing))
        var observeEntriesCalls = 0
        var observePersonalViewingCalls = 0

        fun emit(items: List<LibraryEntry>) {
            emitEntries(items)
            emitViewingResult(AppResult.Success(items.mapNotNull(::toViewing)))
        }

        fun emitEntries(items: List<LibraryEntry>) {
            emitEntriesResult(AppResult.Success(items))
        }

        fun emitEntriesResult(result: AppResult<List<LibraryEntry>>) {
            observedEntries.value = result
        }

        fun emitViewingResult(result: AppResult<List<PersonalViewingEntry>>) {
            observedViewing.value = result
        }

        override fun observeEntries(query: LibraryQuery): Flow<AppResult<List<LibraryEntry>>> =
            observedEntries.also { observeEntriesCalls++ }
        override fun observeEntryCount(): Flow<AppResult<Int>> = flowOf(AppResult.Success(0))
        override fun observeEntry(ref: ExternalMediaRef): Flow<AppResult<LibraryEntry?>> =
            flowOf(AppResult.Success(null))
        override fun observeMembershipRefs(): Flow<AppResult<Set<ExternalMediaRef>>> =
            flowOf(AppResult.Success(emptySet()))
        override fun observePersonalViewing(): Flow<AppResult<List<PersonalViewingEntry>>> =
            observedViewing.also { observePersonalViewingCalls++ }
        override suspend fun add(
            result: com.cydoniancitizen.bingee.core.model.MediaSearchResult
        ): AppResult<LibraryEntry> = AppResult.Failure(com.cydoniancitizen.bingee.core.result.AppError.Unknown)
        override suspend fun add(ref: ExternalMediaRef): AppResult<LibraryEntry> =
            AppResult.Failure(com.cydoniancitizen.bingee.core.result.AppError.Unknown)
        override suspend fun remove(ref: ExternalMediaRef): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun isInLibrary(ref: ExternalMediaRef): AppResult<Boolean> = AppResult.Success(true)
        override suspend fun setFavorite(ref: ExternalMediaRef, isFavorite: Boolean): AppResult<Unit> =
            AppResult.Success(Unit)
        override suspend fun setFavorite(
            result: com.cydoniancitizen.bingee.core.model.MediaSearchResult,
            isFavorite: Boolean
        ): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun setWatchedDate(ref: ExternalMediaRef, watchedDate: java.time.LocalDate?): AppResult<Unit> =
            AppResult.Success(Unit)
    }

    private companion object {
        fun toViewing(entry: LibraryEntry): PersonalViewingEntry? {
            val movieWatchedAt = ((entry.progress as? LibraryProgress.Movie)?.state as? MovieWatchState.Watched)
                ?.watchedAt
            val seriesProgress = (entry.progress as? LibraryProgress.Series)?.progress
            if (movieWatchedAt == null && (seriesProgress?.watchedEpisodes ?: 0) == 0) return null
            return PersonalViewingEntry(
                mediaRef = entry.mediaRef,
                mediaType = entry.mediaType,
                title = entry.title,
                originalTitle = entry.originalTitle,
                posterUrl = entry.posterUrl,
                addedAt = entry.addedAt,
                inLibrary = entry.inLibrary,
                isFavorite = entry.isFavorite,
                personalRating = entry.personalRating,
                movieWatchedAt = movieWatchedAt,
                watchedRegularEpisodes = seriesProgress?.watchedEpisodes ?: 0,
                seriesCompletedAt = Instant.EPOCH.takeIf { seriesProgress?.isComplete == true },
                watchedDate = entry.watchedDate
            )
        }
    }

    private class FakeDisplayModePrefs : ProfileDisplayModePreferences {
        val modes = MutableStateFlow(ProfileDisplayModes())
        override fun observeDisplayModes(): Flow<ProfileDisplayModes> = modes
        override suspend fun setDisplayMode(
            collection: ProfileCollection,
            category: ProfileCategory,
            mode: ProfileViewMode
        ) {
            val current = modes.value
            val updated = when (collection) {
                ProfileCollection.WATCHED -> when (category) {
                    ProfileCategory.MOVIES -> current.copy(watchedMovies = mode)
                    ProfileCategory.TV_SERIES -> current.copy(watchedTvSeries = mode)
                }
                ProfileCollection.WATCH_LATER -> when (category) {
                    ProfileCategory.MOVIES -> current.copy(watchLaterMovies = mode)
                    ProfileCategory.TV_SERIES -> current.copy(watchLaterTvSeries = mode)
                }
                ProfileCollection.FAVORITES -> when (category) {
                    ProfileCategory.MOVIES -> current.copy(favoritesMovies = mode)
                    ProfileCategory.TV_SERIES -> current.copy(favoritesTvSeries = mode)
                }
            }
            modes.value = updated
        }
    }
}
