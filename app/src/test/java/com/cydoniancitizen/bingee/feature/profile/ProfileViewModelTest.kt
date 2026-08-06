package com.cydoniancitizen.bingee.feature.profile

import com.cydoniancitizen.bingee.core.common.TestingAnimeFeatureAvailability
import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.LibraryEntry
import com.cydoniancitizen.bingee.core.model.LibraryProgress
import com.cydoniancitizen.bingee.core.model.LibraryQuery
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.core.model.MovieWatchState
import com.cydoniancitizen.bingee.core.model.PersonalRating
import com.cydoniancitizen.bingee.core.model.SeriesProgress
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
        assertTrue(inProgressSeries.isWatched())
        assertFalse(notStartedSeries.isWatched())

        assertTrue(watchedMovie.belongsToCategory(ProfileCategory.MOVIES))
        assertFalse(watchedMovie.belongsToCategory(ProfileCategory.TV_SERIES))
        assertTrue(inProgressSeries.belongsToCategory(ProfileCategory.TV_SERIES))
        assertFalse(inProgressSeries.belongsToCategory(ProfileCategory.MOVIES))
    }

    @Test
    fun animeClassificationSupportsFutureReactivation() {
        val animeMovie = entry("5", MediaType.ANIME, LibraryProgress.Movie(MovieWatchState.Watched(Instant.EPOCH)))
        val animeSeries = entry("6", MediaType.ANIME, LibraryProgress.Anime(5, 12, false))

        assertTrue(animeMovie.belongsToCategory(ProfileCategory.MOVIES))
        assertFalse(animeMovie.belongsToCategory(ProfileCategory.TV_SERIES))

        assertTrue(animeSeries.belongsToCategory(ProfileCategory.TV_SERIES))
        assertFalse(animeSeries.belongsToCategory(ProfileCategory.MOVIES))
    }

    @Test
    fun profileViewModelFiltersWatchedAndWatchLater() = runTest {
        val watchedMovie = entry("1", MediaType.MOVIE, LibraryProgress.Movie(MovieWatchState.Watched(Instant.EPOCH)))
        val unwatchedMovie = entry("2", MediaType.MOVIE, LibraryProgress.Movie(MovieWatchState.Unwatched))

        val repo = FakeLibraryRepo(listOf(watchedMovie, unwatchedMovie))
        val prefs = FakeDisplayModePrefs()
        val viewModel = ProfileViewModel(repo, prefs, TestingAnimeFeatureAvailability(false))

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
    fun animeRemainsHiddenWhenFeatureDisabled() = runTest {
        val watchedMovie = entry("1", MediaType.MOVIE, LibraryProgress.Movie(MovieWatchState.Watched(Instant.EPOCH)))
        val animeMovie = entry("2", MediaType.ANIME, LibraryProgress.Movie(MovieWatchState.Watched(Instant.EPOCH)))

        val repo = FakeLibraryRepo(listOf(watchedMovie, animeMovie))
        val prefs = FakeDisplayModePrefs()
        val viewModel = ProfileViewModel(repo, prefs, TestingAnimeFeatureAvailability(false))

        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.entries.size)
        assertEquals("1", viewModel.uiState.value.entries.first().mediaRef.externalId)
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
        val viewModel = ProfileViewModel(repo, prefs, TestingAnimeFeatureAvailability(false))

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
    fun displayModePersistenceDelegatesToPrefs() = runTest {
        val repo = FakeLibraryRepo(emptyList())
        val prefs = FakeDisplayModePrefs()
        val viewModel = ProfileViewModel(repo, prefs, TestingAnimeFeatureAvailability(false))

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

    private class FakeLibraryRepo(val items: List<LibraryEntry>) : LibraryRepository {
        override fun observeEntries(query: LibraryQuery): Flow<AppResult<List<LibraryEntry>>> =
            flowOf(AppResult.Success(items))
        override fun observeEntryCount(): Flow<AppResult<Int>> = flowOf(AppResult.Success(items.size))
        override fun observeEntry(ref: ExternalMediaRef): Flow<AppResult<LibraryEntry?>> =
            flowOf(AppResult.Success(items.find { it.mediaRef == ref }))
        override fun observeMembershipRefs(): Flow<AppResult<Set<ExternalMediaRef>>> =
            flowOf(AppResult.Success(items.mapTo(mutableSetOf()) { it.mediaRef }))
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
                ProfileCollection.STATISTICS -> current
            }
            modes.value = updated
        }
    }
}
