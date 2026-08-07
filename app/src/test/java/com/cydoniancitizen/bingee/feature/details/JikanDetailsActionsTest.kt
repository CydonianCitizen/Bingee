package com.cydoniancitizen.bingee.feature.details

import androidx.lifecycle.SavedStateHandle
import com.cydoniancitizen.bingee.core.common.AnimeFeatureAvailability
import com.cydoniancitizen.bingee.core.common.TestingAnimeFeatureAvailability
import com.cydoniancitizen.bingee.core.model.AnimeDetails
import com.cydoniancitizen.bingee.core.model.AnimeFormat
import com.cydoniancitizen.bingee.core.model.AnimeStatus
import com.cydoniancitizen.bingee.core.model.CacheFreshness
import com.cydoniancitizen.bingee.core.model.CachedAnimeDetails
import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.LibraryEntry
import com.cydoniancitizen.bingee.core.model.LibraryProgress
import com.cydoniancitizen.bingee.core.model.LibraryQuery
import com.cydoniancitizen.bingee.core.model.LinkedMediaIdentity
import com.cydoniancitizen.bingee.core.model.MediaLinkAuditOrigin
import com.cydoniancitizen.bingee.core.model.MediaLinkGroup
import com.cydoniancitizen.bingee.core.model.MediaLinkGroupId
import com.cydoniancitizen.bingee.core.model.MediaSearchResult
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.core.model.MovieWatchState
import com.cydoniancitizen.bingee.core.model.PersonalRating
import com.cydoniancitizen.bingee.core.navigation.DetailRoute
import com.cydoniancitizen.bingee.core.result.AppResult
import com.cydoniancitizen.bingee.domain.equivalence.MediaEquivalenceCandidate
import com.cydoniancitizen.bingee.domain.equivalence.MediaEquivalenceEvaluation
import com.cydoniancitizen.bingee.domain.repository.AnimeDetailsRepository
import com.cydoniancitizen.bingee.domain.repository.AnimeProgressRepository
import com.cydoniancitizen.bingee.domain.repository.LibraryRepository
import com.cydoniancitizen.bingee.domain.repository.MediaEquivalenceCandidateRepository
import com.cydoniancitizen.bingee.domain.repository.MediaLinkRepository
import com.cydoniancitizen.bingee.domain.repository.RatingRepository
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class JikanDetailsActionsTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val jikanMovieRef = ExternalMediaRef(MediaSource.JIKAN, "5001")
    private val jikanSeriesRef = ExternalMediaRef(MediaSource.JIKAN, "5002")

    @Test
    fun jikanMovieWatchLater() = runTest(mainDispatcherRule.dispatcher) {
        val libraryRepo = FakeLibraryRepo()
        val viewModel = createViewModel(
            ref = jikanMovieRef,
            mediaType = MediaType.MOVIE,
            libraryRepository = libraryRepo
        )
        runCurrent()

        assertEquals(false, viewModel.uiState.value.isInLibrary)

        viewModel.toggleLibrary()
        runCurrent()

        assertEquals(true, viewModel.uiState.value.isInLibrary)
        assertEquals(jikanMovieRef, libraryRepo.lastAddedRef)
        assertEquals(MediaSource.JIKAN, libraryRepo.lastAddedRef?.source)
        assertEquals("5001", libraryRepo.lastAddedRef?.externalId)

        viewModel.toggleLibrary()
        runCurrent()

        assertEquals(false, viewModel.uiState.value.isInLibrary)
        assertEquals(jikanMovieRef, libraryRepo.lastRemovedRef)
    }

    @Test
    fun jikanSeriesWatchLater() = runTest(mainDispatcherRule.dispatcher) {
        val libraryRepo = FakeLibraryRepo()
        val viewModel = createViewModel(
            ref = jikanSeriesRef,
            mediaType = MediaType.SERIES,
            libraryRepository = libraryRepo
        )
        runCurrent()

        assertEquals(false, viewModel.uiState.value.isInLibrary)

        viewModel.toggleLibrary()
        runCurrent()

        assertEquals(true, viewModel.uiState.value.isInLibrary)
        assertEquals(jikanSeriesRef, libraryRepo.lastAddedRef)
        assertEquals(MediaSource.JIKAN, libraryRepo.lastAddedRef?.source)
        assertEquals("5002", libraryRepo.lastAddedRef?.externalId)

        viewModel.toggleLibrary()
        runCurrent()

        assertEquals(false, viewModel.uiState.value.isInLibrary)
    }

    @Test
    fun jikanMovieWatched() = runTest(mainDispatcherRule.dispatcher) {
        val watchProgressRepo = FakeWatchProgressRepo()
        val viewModel = createViewModel(
            ref = jikanMovieRef,
            mediaType = MediaType.MOVIE,
            watchProgressRepository = watchProgressRepo
        )
        runCurrent()

        val initialMovieProgress = viewModel.uiState.value.movieProgress as? MovieProgressState.Ready
        assertEquals(MovieWatchState.Unwatched, initialMovieProgress?.state)

        viewModel.toggleMovieWatched()
        runCurrent()

        assertEquals(jikanMovieRef, watchProgressRepo.lastMovieWatchedRef)
        assertEquals(MediaSource.JIKAN, watchProgressRepo.lastMovieWatchedRef?.source)

        viewModel.toggleMovieWatched()
        runCurrent()

        assertEquals(jikanMovieRef, watchProgressRepo.lastMovieUnwatchedRef)
    }

    @Test
    fun jikanSeriesWatched() = runTest(mainDispatcherRule.dispatcher) {
        val watchProgressRepo = FakeWatchProgressRepo()
        val viewModel = createViewModel(
            ref = jikanSeriesRef,
            mediaType = MediaType.SERIES,
            watchProgressRepository = watchProgressRepo
        )
        runCurrent()

        assertFalse(viewModel.uiState.value.isSeriesWatched)

        viewModel.toggleSeriesWatched()
        runCurrent()

        assertEquals(jikanSeriesRef, watchProgressRepo.lastSeriesWatchedRef)
        assertEquals(MediaSource.JIKAN, watchProgressRepo.lastSeriesWatchedRef?.source)

        viewModel.toggleSeriesWatched()
        runCurrent()

        assertEquals(jikanSeriesRef, watchProgressRepo.lastSeriesUnwatchedRef)
    }

    @Test
    fun favoriteToggle() = runTest(mainDispatcherRule.dispatcher) {
        val libraryRepo = FakeLibraryRepo()
        val viewModel = createViewModel(
            ref = jikanMovieRef,
            mediaType = MediaType.MOVIE,
            libraryRepository = libraryRepo
        )
        runCurrent()

        assertFalse(viewModel.uiState.value.isFavorite)

        viewModel.toggleFavorite()
        runCurrent()

        assertTrue(libraryRepo.favoriteState[jikanMovieRef] == true)
        assertTrue(viewModel.uiState.value.isFavorite)
        assertEquals(MediaSource.JIKAN, jikanMovieRef.source)
    }

    @Test
    fun personalRating() = runTest(mainDispatcherRule.dispatcher) {
        val ratingRepo = FakeRatingRepo()
        val viewModel = createViewModel(
            ref = jikanMovieRef,
            mediaType = MediaType.MOVIE,
            ratingRepository = ratingRepo
        )
        runCurrent()

        val readyState = viewModel.uiState.value.rating as? DetailRatingState.Ready
        assertNull(readyState?.rating)

        viewModel.selectRating(8)
        viewModel.setRating()
        runCurrent()

        assertEquals(PersonalRating(8), ratingRepo.ratings[jikanMovieRef])

        viewModel.removeRating()
        runCurrent()

        assertNull(ratingRepo.ratings[jikanMovieRef])
    }

    @Test
    fun watchedCompletionDate() = runTest(mainDispatcherRule.dispatcher) {
        val libraryRepo = FakeLibraryRepo()
        val viewModel = createViewModel(
            ref = jikanMovieRef,
            mediaType = MediaType.MOVIE,
            libraryRepository = libraryRepo
        )
        runCurrent()

        assertNull(viewModel.uiState.value.watchedDate)

        val targetDate = LocalDate.of(2026, 8, 7)
        viewModel.setWatchedDate(targetDate)
        runCurrent()

        assertEquals(targetDate, libraryRepo.watchedDates[jikanMovieRef])
        assertEquals(targetDate, viewModel.uiState.value.watchedDate)

        viewModel.setWatchedDate(null)
        runCurrent()

        assertNull(libraryRepo.watchedDates[jikanMovieRef])
        assertNull(viewModel.uiState.value.watchedDate)
    }

    @Test
    fun providerIdentityPreservedAndNoTmdbMerge() = runTest(mainDispatcherRule.dispatcher) {
        val libraryRepo = FakeLibraryRepo()
        val watchProgressRepo = FakeWatchProgressRepo()
        val viewModel = createViewModel(
            ref = jikanSeriesRef,
            mediaType = MediaType.SERIES,
            libraryRepository = libraryRepo,
            watchProgressRepository = watchProgressRepo
        )
        runCurrent()

        viewModel.toggleLibrary()
        viewModel.toggleFavorite()
        viewModel.toggleSeriesWatched()
        viewModel.setWatchedDate(LocalDate.of(2026, 8, 1))
        runCurrent()

        assertEquals(MediaSource.JIKAN, libraryRepo.lastAddedRef?.source)
        assertEquals("5002", libraryRepo.lastAddedRef?.externalId)
        assertEquals(MediaSource.JIKAN, watchProgressRepo.lastSeriesWatchedRef?.source)
        assertEquals("5002", watchProgressRepo.lastSeriesWatchedRef?.externalId)
    }

    @Test
    fun noAnimeSpecificDuplicateStateCreatedForTheseActions() = runTest(mainDispatcherRule.dispatcher) {
        val animeProgressRepo = FakeAnimeProgressRepo()
        val watchProgressRepo = FakeWatchProgressRepo()
        val viewModel = createViewModel(
            ref = jikanMovieRef,
            mediaType = MediaType.MOVIE,
            animeProgressRepository = animeProgressRepo,
            watchProgressRepository = watchProgressRepo
        )
        runCurrent()

        viewModel.toggleMovieWatched()
        runCurrent()

        assertEquals(jikanMovieRef, watchProgressRepo.lastMovieWatchedRef)
        assertEquals(0, animeProgressRepo.incrementCalls)
        assertEquals(0, animeProgressRepo.completeCalls)
    }

    private fun createViewModel(
        ref: ExternalMediaRef,
        mediaType: MediaType,
        detailsRepository: AnimeDetailsRepository = FakeAnimeDetailsRepo(ref),
        animeProgressRepository: AnimeProgressRepository = FakeAnimeProgressRepo(),
        libraryRepository: LibraryRepository = FakeLibraryRepo(),
        ratingRepository: RatingRepository = FakeRatingRepo(),
        candidateRepository: MediaEquivalenceCandidateRepository = FakeCandidateRepo(),
        linkRepository: MediaLinkRepository = FakeLinkRepo(),
        watchProgressRepository: WatchProgressRepository = FakeWatchProgressRepo(),
        availability: AnimeFeatureAvailability = TestingAnimeFeatureAvailability(isAvailable = true)
    ): AnimeDetailsViewModel {
        val savedStateHandle = SavedStateHandle(
            mapOf(
                DetailRoute.SOURCE_ARG to ref.source.name,
                DetailRoute.MEDIA_TYPE_ARG to mediaType.name,
                DetailRoute.EXTERNAL_ID_ARG to ref.externalId
            )
        )
        return AnimeDetailsViewModel(
            savedStateHandle = savedStateHandle,
            detailsRepository = detailsRepository,
            progressRepository = animeProgressRepository,
            libraryRepository = libraryRepository,
            ratingRepository = ratingRepository,
            candidateRepository = candidateRepository,
            linkRepository = linkRepository,
            watchProgressRepository = watchProgressRepository,
            availability = availability
        )
    }

    private class FakeAnimeDetailsRepo(val ref: ExternalMediaRef) : AnimeDetailsRepository {
        val cached = CachedAnimeDetails(
            details = AnimeDetails(
                externalRef = ref,
                title = "Jikan Title",
                englishTitle = "Jikan English",
                japaneseTitle = null,
                synopsis = "Synopsis",
                posterUrl = null,
                format = if (ref.externalId == "5001") AnimeFormat.MOVIE else AnimeFormat.TV,
                status = AnimeStatus.FINISHED,
                episodeCount = 12,
                duration = null,
                startDate = LocalDate.of(2024, 1, 1),
                endDate = null,
                season = null,
                year = 2024,
                providerScore = 8.5,
                relations = emptyList()
            ),
            fetchedAt = Instant.now(),
            freshness = CacheFreshness.FRESH
        )

        override fun observeDetails(reference: ExternalMediaRef): Flow<AppResult<CachedAnimeDetails?>> =
            flowOf(AppResult.Success(cached))

        override suspend fun refreshDetails(reference: ExternalMediaRef, force: Boolean): AppResult<Unit> =
            AppResult.Success(Unit)
    }

    private class FakeLibraryRepo : LibraryRepository {
        val members = mutableSetOf<ExternalMediaRef>()
        val favoriteState = mutableMapOf<ExternalMediaRef, Boolean>()
        val watchedDates = mutableMapOf<ExternalMediaRef, LocalDate?>()
        var lastAddedRef: ExternalMediaRef? = null
        var lastRemovedRef: ExternalMediaRef? = null

        private val entryFlows = mutableMapOf<ExternalMediaRef, MutableStateFlow<AppResult<LibraryEntry?>>>()

        private fun getFlow(ref: ExternalMediaRef): MutableStateFlow<AppResult<LibraryEntry?>> =
            entryFlows.getOrPut(ref) { MutableStateFlow(AppResult.Success(createEntry(ref))) }

        private fun createEntry(ref: ExternalMediaRef): LibraryEntry? {
            if (ref !in members && favoriteState[ref] != true && watchedDates[ref] == null) return null
            return LibraryEntry(
                mediaRef = ref,
                mediaType = if (ref.externalId == "5001") MediaType.MOVIE else MediaType.SERIES,
                title = "Jikan Title",
                addedAt = Instant.now(),
                progress = LibraryProgress.Unavailable,
                personalRating = null,
                isFavorite = favoriteState[ref] ?: false,
                watchedDate = watchedDates[ref],
                inLibrary = ref in members
            )
        }

        private fun notifyChange(ref: ExternalMediaRef) {
            getFlow(ref).value = AppResult.Success(createEntry(ref))
        }

        override fun observeEntries(query: LibraryQuery): Flow<AppResult<List<LibraryEntry>>> = flowOf(AppResult.Success(emptyList()))
        override fun observeEntryCount(): Flow<AppResult<Int>> = flowOf(AppResult.Success(members.size))
        override fun observeEntry(ref: ExternalMediaRef): Flow<AppResult<LibraryEntry?>> = getFlow(ref)
        override fun observeMembershipRefs(): Flow<AppResult<Set<ExternalMediaRef>>> = flowOf(AppResult.Success(members))

        override suspend fun add(result: MediaSearchResult): AppResult<LibraryEntry> {
            members.add(result.externalRef)
            lastAddedRef = result.externalRef
            notifyChange(result.externalRef)
            return AppResult.Success(createEntry(result.externalRef)!!)
        }

        override suspend fun add(ref: ExternalMediaRef): AppResult<LibraryEntry> {
            members.add(ref)
            lastAddedRef = ref
            notifyChange(ref)
            return AppResult.Success(createEntry(ref)!!)
        }

        override suspend fun remove(ref: ExternalMediaRef): AppResult<Unit> {
            members.remove(ref)
            lastRemovedRef = ref
            notifyChange(ref)
            return AppResult.Success(Unit)
        }

        override suspend fun isInLibrary(ref: ExternalMediaRef): AppResult<Boolean> = AppResult.Success(ref in members)

        override suspend fun setFavorite(ref: ExternalMediaRef, isFavorite: Boolean): AppResult<Unit> {
            favoriteState[ref] = isFavorite
            notifyChange(ref)
            return AppResult.Success(Unit)
        }

        override suspend fun setFavorite(result: MediaSearchResult, isFavorite: Boolean): AppResult<Unit> = setFavorite(result.externalRef, isFavorite)

        override suspend fun setWatchedDate(ref: ExternalMediaRef, watchedDate: LocalDate?): AppResult<Unit> {
            watchedDates[ref] = watchedDate
            notifyChange(ref)
            return AppResult.Success(Unit)
        }
    }

    private class FakeWatchProgressRepo : WatchProgressRepository {
        var lastMovieWatchedRef: ExternalMediaRef? = null
        var lastMovieUnwatchedRef: ExternalMediaRef? = null
        var lastSeriesWatchedRef: ExternalMediaRef? = null
        var lastSeriesUnwatchedRef: ExternalMediaRef? = null

        private val movieState = MutableStateFlow<MovieWatchState>(MovieWatchState.Unwatched)

        override fun observeMovie(reference: ExternalMediaRef): Flow<AppResult<MovieWatchState>> =
            movieState.map { AppResult.Success(it) }

        override suspend fun markEpisodeWatched(episodeRef: ExternalMediaRef): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun markEpisodeUnwatched(episodeRef: ExternalMediaRef): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun markSeasonWatched(seasonRef: ExternalMediaRef): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun markSeasonUnwatched(seasonRef: ExternalMediaRef): AppResult<Unit> = AppResult.Success(Unit)

        override suspend fun markMovieWatched(reference: ExternalMediaRef): AppResult<Unit> {
            lastMovieWatchedRef = reference
            movieState.value = MovieWatchState.Watched(Instant.now())
            return AppResult.Success(Unit)
        }

        override suspend fun markMovieUnwatched(reference: ExternalMediaRef): AppResult<Unit> {
            lastMovieUnwatchedRef = reference
            movieState.value = MovieWatchState.Unwatched
            return AppResult.Success(Unit)
        }

        override suspend fun markSeriesWatched(reference: ExternalMediaRef): AppResult<Unit> {
            lastSeriesWatchedRef = reference
            return AppResult.Success(Unit)
        }

        override suspend fun markSeriesUnwatched(reference: ExternalMediaRef): AppResult<Unit> {
            lastSeriesUnwatchedRef = reference
            return AppResult.Success(Unit)
        }
    }

    private class FakeRatingRepo : RatingRepository {
        val ratings = mutableMapOf<ExternalMediaRef, PersonalRating>()
        private val ratingFlows = mutableMapOf<ExternalMediaRef, MutableStateFlow<AppResult<PersonalRating?>>>()

        private fun getFlow(ref: ExternalMediaRef): MutableStateFlow<AppResult<PersonalRating?>> =
            ratingFlows.getOrPut(ref) { MutableStateFlow(AppResult.Success(ratings[ref])) }

        override fun observeRating(reference: ExternalMediaRef): Flow<AppResult<PersonalRating?>> = getFlow(reference)

        override suspend fun setRating(reference: ExternalMediaRef, rating: PersonalRating): AppResult<Unit> {
            ratings[reference] = rating
            getFlow(reference).value = AppResult.Success(rating)
            return AppResult.Success(Unit)
        }

        override suspend fun removeRating(reference: ExternalMediaRef): AppResult<Unit> {
            ratings.remove(reference)
            getFlow(reference).value = AppResult.Success(null)
            return AppResult.Success(Unit)
        }
    }

    private class FakeAnimeProgressRepo : AnimeProgressRepository {
        var incrementCalls = 0
        var completeCalls = 0

        override fun observe(reference: ExternalMediaRef) = flowOf(AppResult.Success(null))
        override suspend fun increment(reference: ExternalMediaRef): AppResult<Unit> {
            incrementCalls++
            return AppResult.Success(Unit)
        }
        override suspend fun decrement(reference: ExternalMediaRef) = AppResult.Success(Unit)
        override suspend fun setCount(reference: ExternalMediaRef, count: Int) = AppResult.Success(Unit)
        override suspend fun markComplete(reference: ExternalMediaRef): AppResult<Unit> {
            completeCalls++
            return AppResult.Success(Unit)
        }
        override suspend fun markIncomplete(reference: ExternalMediaRef) = AppResult.Success(Unit)
        override suspend fun reset(reference: ExternalMediaRef) = AppResult.Success(Unit)
    }

    private class FakeCandidateRepo : MediaEquivalenceCandidateRepository {
        override fun observeLibraryCandidates(): Flow<List<MediaEquivalenceCandidate>> = flowOf(emptyList())
        override fun observeCandidatesForMedia(identity: LinkedMediaIdentity): Flow<List<MediaEquivalenceCandidate>> = flowOf(emptyList())
        override suspend fun evaluatePair(first: LinkedMediaIdentity, second: LinkedMediaIdentity): AppResult<MediaEquivalenceEvaluation> = AppResult.Failure(com.cydoniancitizen.bingee.core.result.AppError.UnsupportedData)
    }

    private class FakeLinkRepo : MediaLinkRepository {
        override fun observeLinkForMedia(identity: LinkedMediaIdentity): Flow<MediaLinkGroup?> = flowOf(null)
        override fun observeLinkGroup(groupId: MediaLinkGroupId): Flow<MediaLinkGroup?> = flowOf(null)
        override suspend fun createLink(first: LinkedMediaIdentity, second: LinkedMediaIdentity, preferredPresentation: LinkedMediaIdentity, origin: MediaLinkAuditOrigin): AppResult<MediaLinkGroup> = AppResult.Failure(com.cydoniancitizen.bingee.core.result.AppError.UnsupportedData)
        override suspend fun changePreferredPresentation(groupId: MediaLinkGroupId, preferredPresentation: LinkedMediaIdentity, origin: MediaLinkAuditOrigin): AppResult<MediaLinkGroup> = AppResult.Failure(com.cydoniancitizen.bingee.core.result.AppError.UnsupportedData)
        override suspend fun unlink(groupId: MediaLinkGroupId, origin: MediaLinkAuditOrigin): AppResult<Unit> = AppResult.Success(Unit)
    }
}
