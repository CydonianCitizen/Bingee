package com.cydoniancitizen.bingee.feature.details

import androidx.lifecycle.SavedStateHandle
import com.cydoniancitizen.bingee.core.model.CacheFreshness
import com.cydoniancitizen.bingee.core.model.CachedMediaDetails
import com.cydoniancitizen.bingee.core.model.CachedSeason
import com.cydoniancitizen.bingee.core.model.Episode
import com.cydoniancitizen.bingee.core.model.EpisodeWatchState
import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.LibraryEntry
import com.cydoniancitizen.bingee.core.model.LibraryQuery
import com.cydoniancitizen.bingee.core.model.LinkedMediaIdentity
import com.cydoniancitizen.bingee.core.model.MediaDetails
import com.cydoniancitizen.bingee.core.model.MediaLinkAuditOrigin
import com.cydoniancitizen.bingee.core.model.MediaLinkGroup
import com.cydoniancitizen.bingee.core.model.MediaLinkGroupId
import com.cydoniancitizen.bingee.core.model.MediaSearchResult
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.core.model.MovieWatchState
import com.cydoniancitizen.bingee.core.model.PersonalRating
import com.cydoniancitizen.bingee.core.model.Season
import com.cydoniancitizen.bingee.core.model.SeasonProgress
import com.cydoniancitizen.bingee.core.model.TrackedEpisode
import com.cydoniancitizen.bingee.core.navigation.DetailRoute
import com.cydoniancitizen.bingee.core.result.AppError
import com.cydoniancitizen.bingee.core.result.AppResult
import com.cydoniancitizen.bingee.domain.equivalence.MediaEquivalenceCandidate
import com.cydoniancitizen.bingee.domain.equivalence.MediaEquivalenceEvaluation
import com.cydoniancitizen.bingee.domain.repository.LibraryRepository
import com.cydoniancitizen.bingee.domain.repository.MediaDetailsRepository
import com.cydoniancitizen.bingee.domain.repository.MediaEquivalenceCandidateRepository
import com.cydoniancitizen.bingee.domain.repository.MediaLinkRepository
import com.cydoniancitizen.bingee.domain.repository.RatingRepository
import com.cydoniancitizen.bingee.domain.repository.SeriesRepository
import com.cydoniancitizen.bingee.domain.repository.WatchProgressRepository
import com.cydoniancitizen.bingee.testutil.MainDispatcherRule
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MediaDetailsViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()
    private val ref = ExternalMediaRef(MediaSource.TMDB, "550")

    @Test
    fun malformedAndUnsupportedRoutesFailSafelyWithoutRefresh() = runTest(mainDispatcherRule.dispatcher) {
        val remote = FakeDetailsRepository()
        val malformed = viewModel(SavedStateHandle(), remote)
        val unsupported = viewModel(args(MediaSource.JIKAN, MediaType.ANIME), remote)
        runCurrent()

        assertEquals(DetailContentState.Error(AppError.InvalidInput), malformed.uiState.value.content)
        assertEquals(DetailContentState.Error(AppError.UnsupportedData), unsupported.uiState.value.content)
        assertTrue(remote.refreshes.isEmpty())
    }

    @Test
    fun cacheMissLoadsThenShowsFullErrorWithoutClearingLibraryState() = runTest(mainDispatcherRule.dispatcher) {
        val remote = FakeDetailsRepository(refreshResult = AppResult.Failure(AppError.NetworkUnavailable))
        val library = FakeLibraryRepository(member = true)
        val viewModel = viewModel(args(), remote, library)
        runCurrent()

        assertEquals(DetailContentState.Error(AppError.NetworkUnavailable), viewModel.uiState.value.content)
        assertEquals(true, viewModel.uiState.value.isInLibrary)
        assertEquals(listOf(false), remote.refreshes.map { it.third })
    }

    @Test
    fun freshCacheDisplaysWithoutAutomaticRefresh() = runTest(mainDispatcherRule.dispatcher) {
        val remote = FakeDetailsRepository(cached(CacheFreshness.FRESH))
        val viewModel = viewModel(args(), remote)
        runCurrent()

        assertTrue(viewModel.uiState.value.content is DetailContentState.Content)
        assertTrue(remote.refreshes.isEmpty())
    }

    @Test
    fun staleCacheRemainsVisibleWhenBackgroundRefreshFails() = runTest(mainDispatcherRule.dispatcher) {
        val remote = FakeDetailsRepository(
            cached(CacheFreshness.STALE),
            AppResult.Failure(AppError.NetworkUnavailable)
        )
        val viewModel = viewModel(args(), remote)
        runCurrent()

        assertTrue(viewModel.uiState.value.content is DetailContentState.Content)
        assertEquals(DetailRefreshState.Error(AppError.NetworkUnavailable), viewModel.uiState.value.refresh)
        assertEquals(listOf(false), remote.refreshes.map { it.third })
    }

    @Test
    fun manualRefreshForcesRemoteAndPreservesVisibleContent() = runTest(mainDispatcherRule.dispatcher) {
        val remote = FakeDetailsRepository(
            cached(CacheFreshness.FRESH),
            AppResult.Failure(AppError.Unauthorized)
        )
        val viewModel = viewModel(args(), remote)
        runCurrent()
        viewModel.refresh()
        viewModel.refresh()
        runCurrent()

        assertTrue(viewModel.uiState.value.content is DetailContentState.Content)
        assertEquals(DetailRefreshState.Error(AppError.Unauthorized), viewModel.uiState.value.refresh)
        assertEquals(listOf(true), remote.refreshes.map { it.third })
    }

    @Test
    fun membershipObservationAddAndRemoveDoNotReplaceContent() = runTest(mainDispatcherRule.dispatcher) {
        val library = FakeLibraryRepository(member = false)
        val viewModel = viewModel(args(), FakeDetailsRepository(cached(CacheFreshness.FRESH)), library)
        runCurrent()

        assertEquals(false, viewModel.uiState.value.isInLibrary)
        viewModel.toggleLibrary()
        runCurrent()
        assertEquals(true, viewModel.uiState.value.isInLibrary)
        assertTrue(viewModel.uiState.value.content is DetailContentState.Content)
        viewModel.toggleLibrary()
        runCurrent()
        assertEquals(false, viewModel.uiState.value.isInLibrary)
        assertEquals(listOf("add", "remove"), library.actions)
    }

    @Test
    fun libraryFailureKeepsContentAndMembership() = runTest(mainDispatcherRule.dispatcher) {
        val library = FakeLibraryRepository(member = false, failure = AppError.LocalStorageFailure)
        val viewModel = viewModel(args(), FakeDetailsRepository(cached(CacheFreshness.FRESH)), library)
        runCurrent()
        viewModel.toggleLibrary()
        runCurrent()

        assertFalse(viewModel.uiState.value.isInLibrary ?: true)
        assertEquals(AppError.LocalStorageFailure, viewModel.uiState.value.libraryError)
        assertTrue(viewModel.uiState.value.content is DetailContentState.Content)
    }

    @Test
    fun movieWatchToggleIsLocalAndEmitsUpdatedState() = runTest(mainDispatcherRule.dispatcher) {
        val progress = FakeWatchProgressRepository()
        val viewModel = viewModel(
            args(),
            FakeDetailsRepository(cached(CacheFreshness.FRESH)),
            progress = progress
        )
        runCurrent()

        assertEquals(
            MovieProgressState.Ready(MovieWatchState.Unwatched),
            viewModel.uiState.value.movieProgress
        )
        viewModel.toggleMovieWatched()
        runCurrent()

        assertTrue((viewModel.uiState.value.movieProgress as MovieProgressState.Ready).state is MovieWatchState.Watched)
        assertEquals(listOf("movie-watched"), progress.actions)
    }

    @Test
    fun seasonExpansionLoadsIncrementallyAndProgressActionsRemainIsolated() = runTest(mainDispatcherRule.dispatcher) {
        val season = cachedSeason()
        val series = FakeSeriesRepository(listOf(season))
        val progress = FakeWatchProgressRepository()
        val viewModel = viewModel(
            args(mediaType = MediaType.SERIES),
            FakeDetailsRepository(cachedSeries()),
            series = series,
            progress = progress
        )
        runCurrent()

        val ready = viewModel.uiState.value.series.content as SeriesContentState.Ready
        assertEquals(1, ready.seasons.size)
        assertEquals(SeasonProgress(0, 1, false), ready.seasons.first().progress)

        viewModel.toggleSeasonExpanded(season)
        runCurrent()
        assertEquals(listOf(Triple(ref, 1, false)), series.refreshes)
        assertTrue(season.season.externalRef in viewModel.uiState.value.series.expandedSeasons)
        viewModel.retrySeason(season)
        runCurrent()
        assertEquals(
            listOf(Triple(ref, 1, false), Triple(ref, 1, true)),
            series.refreshes
        )

        viewModel.toggleEpisode(season.episodes.first())
        viewModel.toggleEpisode(season.episodes.last())
        viewModel.toggleSeasonWatched(season)
        runCurrent()
        assertEquals(listOf("episode-watched", "season-watched"), progress.actions)
    }

    @Test
    fun emptySeasonSummaryCacheBootstrapsOnceEvenWhenVersionTwoDetailsAreFresh() =
        runTest(mainDispatcherRule.dispatcher) {
            val details = FakeDetailsRepository(cachedSeries())
            viewModel(
                args(mediaType = MediaType.SERIES),
                details,
                series = FakeSeriesRepository()
            )
            runCurrent()

            assertEquals(listOf(true), details.refreshes.map { it.third })
        }

    @Test
    fun ratingSetUpdateIdenticalAndRemoveRemainIndependent() = runTest(mainDispatcherRule.dispatcher) {
        val rating = FakeRatingRepository()
        val library = FakeLibraryRepository(member = false)
        val progress = FakeWatchProgressRepository()
        val viewModel = viewModel(
            args(),
            FakeDetailsRepository(cached(CacheFreshness.FRESH)),
            library,
            progress = progress,
            rating = rating
        )
        runCurrent()

        viewModel.selectRating(1)
        viewModel.setRating()
        runCurrent()
        assertEquals(PersonalRating(1), (viewModel.uiState.value.rating as DetailRatingState.Ready).rating)
        viewModel.selectRating(10)
        viewModel.setRating()
        runCurrent()
        viewModel.setRating()
        runCurrent()
        viewModel.removeRating()
        runCurrent()

        assertEquals(listOf("set:1", "set:10", "set:10", "remove"), rating.actions)
        assertEquals(null, (viewModel.uiState.value.rating as DetailRatingState.Ready).rating)
        assertEquals(false, viewModel.uiState.value.isInLibrary)
        assertTrue(progress.actions.isEmpty())
    }

    @Test
    fun invalidOrFailedRatingKeepsDetailContent() = runTest(mainDispatcherRule.dispatcher) {
        val rating = FakeRatingRepository(failure = AppError.LocalStorageFailure)
        val viewModel = viewModel(
            args(),
            FakeDetailsRepository(cached(CacheFreshness.FRESH)),
            rating = rating
        )
        runCurrent()
        viewModel.selectRating(0)
        assertEquals(
            AppError.InvalidInput,
            (viewModel.uiState.value.rating as DetailRatingState.Ready).error
        )
        viewModel.selectRating(5)
        viewModel.setRating()
        runCurrent()

        assertEquals(
            AppError.LocalStorageFailure,
            (viewModel.uiState.value.rating as DetailRatingState.Ready).error
        )
        assertTrue(viewModel.uiState.value.content is DetailContentState.Content)
    }

    private fun viewModel(
        state: SavedStateHandle,
        details: FakeDetailsRepository,
        library: FakeLibraryRepository = FakeLibraryRepository(),
        series: FakeSeriesRepository = FakeSeriesRepository(),
        progress: FakeWatchProgressRepository = FakeWatchProgressRepository(),
        rating: FakeRatingRepository = FakeRatingRepository(),
        animeAvailability: com.cydoniancitizen.bingee.core.common.AnimeFeatureAvailability =
            com.cydoniancitizen.bingee.core.common.TestingAnimeFeatureAvailability(isAvailable = true)
    ) = MediaDetailsViewModel(
        state,
        details,
        library,
        series,
        progress,
        rating,
        FakeCandidateRepository(),
        FakeLinkRepository(),
        animeAvailability
    )

    private class FakeCandidateRepository : MediaEquivalenceCandidateRepository {
        override fun observeLibraryCandidates(): Flow<List<MediaEquivalenceCandidate>> = flowOf(emptyList())

        override fun observeCandidatesForMedia(identity: LinkedMediaIdentity): Flow<List<MediaEquivalenceCandidate>> =
            flowOf(emptyList())

        override suspend fun evaluatePair(
            first: LinkedMediaIdentity,
            second: LinkedMediaIdentity
        ): AppResult<MediaEquivalenceEvaluation> = AppResult.Failure(AppError.LinkError.MediaNotFound)
    }

    private class FakeLinkRepository : MediaLinkRepository {
        override fun observeLinkForMedia(identity: LinkedMediaIdentity): Flow<MediaLinkGroup?> = flowOf(null)

        override fun observeLinkGroup(groupId: MediaLinkGroupId): Flow<MediaLinkGroup?> = flowOf(null)

        override suspend fun createLink(
            first: LinkedMediaIdentity,
            second: LinkedMediaIdentity,
            preferredPresentation: LinkedMediaIdentity,
            origin: MediaLinkAuditOrigin
        ): AppResult<MediaLinkGroup> = AppResult.Failure(AppError.LinkError.MediaNotFound)

        override suspend fun changePreferredPresentation(
            groupId: MediaLinkGroupId,
            preferredPresentation: LinkedMediaIdentity,
            origin: MediaLinkAuditOrigin
        ): AppResult<MediaLinkGroup> = AppResult.Failure(AppError.LinkError.LinkGroupNotFound)

        override suspend fun unlink(groupId: MediaLinkGroupId, origin: MediaLinkAuditOrigin): AppResult<Unit> =
            AppResult.Success(Unit)
    }

    private fun args(source: MediaSource = MediaSource.TMDB, mediaType: MediaType = MediaType.MOVIE) = SavedStateHandle(
        mapOf(
            DetailRoute.SOURCE_ARG to source.name,
            DetailRoute.MEDIA_TYPE_ARG to mediaType.name,
            DetailRoute.EXTERNAL_ID_ARG to "550"
        )
    )

    private fun cached(freshness: CacheFreshness) = CachedMediaDetails(
        details = MediaDetails(ref, MediaType.MOVIE, "Cached movie"),
        fetchedAt = Instant.parse("2026-08-03T10:00:00Z"),
        freshness = freshness
    )

    private fun cachedSeries() = CachedMediaDetails(
        details = MediaDetails(ref, MediaType.SERIES, "Cached series"),
        fetchedAt = Instant.parse("2026-08-03T10:00:00Z"),
        freshness = CacheFreshness.FRESH
    )

    private fun cachedSeason(): CachedSeason {
        val seasonRef = ExternalMediaRef(MediaSource.TMDB, "11")
        val trackable = TrackedEpisode(
            Episode(ref, seasonRef, ExternalMediaRef(MediaSource.TMDB, "101"), 1, 1, "First"),
            EpisodeWatchState.Unwatched
        )
        val future = TrackedEpisode(
            Episode(ref, seasonRef, ExternalMediaRef(MediaSource.TMDB, "102"), 1, 2, "Future"),
            EpisodeWatchState.Unavailable
        )
        return CachedSeason(
            season = Season(ref, seasonRef, 1, name = "Season 1", episodeCount = 2),
            metadataUpdatedAt = Instant.parse("2026-08-03T10:00:00Z"),
            episodesFetchedAt = null,
            episodes = listOf(trackable, future),
            progress = SeasonProgress(0, 1, false),
            episodeCacheFreshness = null
        )
    }

    private class FakeDetailsRepository(
        initial: CachedMediaDetails? = null,
        var refreshResult: AppResult<Unit> = AppResult.Success(Unit)
    ) : MediaDetailsRepository {
        val observed = MutableStateFlow<AppResult<CachedMediaDetails?>>(AppResult.Success(initial))
        val refreshes = mutableListOf<Triple<ExternalMediaRef, MediaType, Boolean>>()
        override fun observeDetails(reference: ExternalMediaRef): Flow<AppResult<CachedMediaDetails?>> = observed
        override suspend fun refreshDetails(
            reference: ExternalMediaRef,
            mediaType: MediaType,
            force: Boolean
        ): AppResult<Unit> {
            refreshes += Triple(reference, mediaType, force)
            return refreshResult
        }
    }

    private class FakeLibraryRepository(member: Boolean = false, private val failure: AppError? = null) :
        LibraryRepository {
        private val entry = MutableStateFlow(if (member) libraryEntry() else null)
        val actions = mutableListOf<String>()
        override fun observeEntries(query: LibraryQuery): Flow<AppResult<List<LibraryEntry>>> =
            entry.map { AppResult.Success(listOfNotNull(it)) }
        override fun observeEntryCount(): Flow<AppResult<Int>> = entry.map {
            AppResult.Success(
                if (it ==
                    null
                ) {
                    0
                } else {
                    1
                }
            )
        }
        override fun observeEntry(ref: ExternalMediaRef): Flow<AppResult<LibraryEntry?>> =
            entry.map { AppResult.Success(it) }
        override fun observeMembershipRefs(): Flow<AppResult<Set<ExternalMediaRef>>> =
            entry.map { AppResult.Success(listOfNotNull(it?.mediaRef).toSet()) }
        override suspend fun add(result: MediaSearchResult): AppResult<LibraryEntry> = error("unused")
        override suspend fun add(ref: ExternalMediaRef): AppResult<LibraryEntry> {
            actions += "add"
            failure?.let { return AppResult.Failure(it) }
            return AppResult.Success(libraryEntry()).also { entry.value = it.value }
        }
        override suspend fun remove(ref: ExternalMediaRef): AppResult<Unit> {
            actions += "remove"
            failure?.let { return AppResult.Failure(it) }
            entry.value = null
            return AppResult.Success(Unit)
        }
        override suspend fun isInLibrary(ref: ExternalMediaRef): AppResult<Boolean> =
            AppResult.Success(entry.value != null)
        override suspend fun setFavorite(ref: ExternalMediaRef, isFavorite: Boolean): AppResult<Unit> =
            AppResult.Success(Unit)
        override suspend fun setFavorite(result: MediaSearchResult, isFavorite: Boolean): AppResult<Unit> =
            AppResult.Success(Unit)
        override suspend fun setWatchedDate(ref: ExternalMediaRef, watchedDate: LocalDate?): AppResult<Unit> =
            AppResult.Success(Unit)

        companion object {
            fun libraryEntry() = LibraryEntry(
                mediaRef = ExternalMediaRef(MediaSource.TMDB, "550"),
                mediaType = MediaType.MOVIE,
                title = "Cached movie",
                addedAt = Instant.parse("2026-08-03T10:00:00Z")
            )
        }
    }

    private class FakeSeriesRepository(initial: List<CachedSeason> = emptyList()) : SeriesRepository {
        private val seasons = MutableStateFlow<AppResult<List<CachedSeason>>>(AppResult.Success(initial))
        val refreshes = mutableListOf<Triple<ExternalMediaRef, Int, Boolean>>()
        override fun observeSeasons(seriesRef: ExternalMediaRef): Flow<AppResult<List<CachedSeason>>> = seasons

        override suspend fun refreshSeason(
            seriesRef: ExternalMediaRef,
            seasonNumber: Int,
            force: Boolean
        ): AppResult<Unit> {
            refreshes += Triple(seriesRef, seasonNumber, force)
            return AppResult.Success(Unit)
        }
    }

    private class FakeWatchProgressRepository : WatchProgressRepository {
        private val movie = MutableStateFlow<AppResult<MovieWatchState>>(
            AppResult.Success(MovieWatchState.Unwatched)
        )
        val actions = mutableListOf<String>()
        override fun observeMovie(reference: ExternalMediaRef): Flow<AppResult<MovieWatchState>> = movie

        override suspend fun markEpisodeWatched(episodeRef: ExternalMediaRef) = success("episode-watched")
        override suspend fun markEpisodeUnwatched(episodeRef: ExternalMediaRef) = success("episode-unwatched")
        override suspend fun markSeasonWatched(seasonRef: ExternalMediaRef) = success("season-watched")
        override suspend fun markSeasonUnwatched(seasonRef: ExternalMediaRef) = success("season-unwatched")
        override suspend fun markMovieWatched(reference: ExternalMediaRef): AppResult<Unit> {
            actions += "movie-watched"
            movie.value = AppResult.Success(MovieWatchState.Watched(Instant.EPOCH))
            return AppResult.Success(Unit)
        }
        override suspend fun markMovieUnwatched(reference: ExternalMediaRef): AppResult<Unit> {
            actions += "movie-unwatched"
            movie.value = AppResult.Success(MovieWatchState.Unwatched)
            return AppResult.Success(Unit)
        }

        private fun success(action: String): AppResult<Unit> {
            actions += action
            return AppResult.Success(Unit)
        }
    }

    private class FakeRatingRepository(initial: PersonalRating? = null, private val failure: AppError? = null) :
        RatingRepository {
        private val rating = MutableStateFlow<AppResult<PersonalRating?>>(AppResult.Success(initial))
        val actions = mutableListOf<String>()

        override fun observeRating(reference: ExternalMediaRef): Flow<AppResult<PersonalRating?>> = rating

        override suspend fun setRating(reference: ExternalMediaRef, rating: PersonalRating): AppResult<Unit> {
            actions += "set:${rating.value}"
            failure?.let { return AppResult.Failure(it) }
            this.rating.value = AppResult.Success(rating)
            return AppResult.Success(Unit)
        }

        override suspend fun removeRating(reference: ExternalMediaRef): AppResult<Unit> {
            actions += "remove"
            failure?.let { return AppResult.Failure(it) }
            rating.value = AppResult.Success(null)
            return AppResult.Success(Unit)
        }
    }
}
