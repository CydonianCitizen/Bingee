package com.cydoniancitizen.bingee.feature.profile

import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.LibraryEntry
import com.cydoniancitizen.bingee.core.model.LibraryProgress
import com.cydoniancitizen.bingee.core.model.LibraryQuery
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.core.model.MovieWatchState
import com.cydoniancitizen.bingee.core.model.PersonalRating
import com.cydoniancitizen.bingee.core.model.SeriesProgress
import com.cydoniancitizen.bingee.core.model.isWatched
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
import kotlinx.coroutines.flow.map
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
    fun statisticsStateUsesSingleProfileLibraryObservation() = runTest {
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
        added: Instant = Instant.EPOCH
    ) = LibraryEntry(
        mediaRef = ExternalMediaRef(MediaSource.TMDB, id),
        mediaType = type,
        title = title,
        addedAt = added,
        progress = progress,
        personalRating = rating?.let { PersonalRating(it) }
    )

    private class FakeLibraryRepo(items: List<LibraryEntry>) : LibraryRepository {
        private val observedEntries = MutableStateFlow(items)
        var observeEntriesCalls = 0

        fun emit(items: List<LibraryEntry>) {
            observedEntries.value = items
        }

        override fun observeEntries(query: LibraryQuery): Flow<AppResult<List<LibraryEntry>>> =
            observedEntries.map { AppResult.Success(it) }.also { observeEntriesCalls++ }
        override fun observeEntryCount(): Flow<AppResult<Int>> = flowOf(AppResult.Success(observedEntries.value.size))
        override fun observeEntry(ref: ExternalMediaRef): Flow<AppResult<LibraryEntry?>> =
            flowOf(AppResult.Success(observedEntries.value.find { it.mediaRef == ref }))
        override fun observeMembershipRefs(): Flow<AppResult<Set<ExternalMediaRef>>> =
            flowOf(AppResult.Success(observedEntries.value.mapTo(mutableSetOf()) { it.mediaRef }))
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
