package com.cydoniancitizen.bingee.data.details

import com.cydoniancitizen.bingee.core.model.CacheFreshness
import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.MediaDetails
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.core.model.ProductionStatus
import com.cydoniancitizen.bingee.core.model.Season
import com.cydoniancitizen.bingee.core.result.AppError
import com.cydoniancitizen.bingee.core.result.AppResult
import com.cydoniancitizen.bingee.data.calendar.MetadataCalendarStore
import com.cydoniancitizen.bingee.data.library.local.CachedDetailsRelation
import com.cydoniancitizen.bingee.data.library.local.DetailsDao
import com.cydoniancitizen.bingee.data.library.local.ExternalRefEntity
import com.cydoniancitizen.bingee.data.library.local.MediaDetailsEntity
import com.cydoniancitizen.bingee.data.library.local.MediaEntity
import com.cydoniancitizen.bingee.data.library.local.MediaGenreEntity
import com.cydoniancitizen.bingee.data.library.local.SeasonEntity
import com.cydoniancitizen.bingee.data.library.local.SeasonSummaryStore
import com.cydoniancitizen.bingee.data.series.toEntity
import com.cydoniancitizen.bingee.data.tmdb.details.TmdbDetailsRemoteDataSource
import com.cydoniancitizen.bingee.data.tmdb.details.TmdbMediaDetailsPayload
import com.cydoniancitizen.bingee.data.tmdb.series.TmdbSeasonPayload
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultMediaDetailsRepositoryTest {
    private val now = Instant.parse("2026-08-03T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val movieRef = ExternalMediaRef(MediaSource.TMDB, "550")

    @Test
    fun cacheMissSuccessPersistsAndUpdatesSuccessfulFetchTimestamp() = runTest {
        val dao = FakeDetailsDao()
        val remote = FakeRemote { ref, type -> AppResult.Success(details(ref, type, "Remote")) }
        val repository = repository(dao, remote)

        assertNull((repository.observeDetails(movieRef).first() as AppResult.Success).value)
        assertEquals(AppResult.Success(Unit), repository.refreshDetails(movieRef, MediaType.MOVIE))
        val cached = (repository.observeDetails(movieRef).first() as AppResult.Success).value

        assertEquals("Remote", cached?.details?.title)
        assertEquals(now, cached?.fetchedAt)
        assertEquals(1, remote.calls.size)
    }

    @Test
    fun freshCacheSkipsAutomaticNetworkButManualRefreshBypassesFreshness() = runTest {
        val dao = FakeDetailsDao(cached(movieRef, now.minusSeconds(60), "Cached"))
        val remote = FakeRemote { ref, type -> AppResult.Success(details(ref, type, "Updated")) }
        val repository = repository(dao, remote)

        assertEquals(AppResult.Success(Unit), repository.refreshDetails(movieRef, MediaType.MOVIE, force = false))
        assertEquals(0, remote.calls.size)
        assertEquals(AppResult.Success(Unit), repository.refreshDetails(movieRef, MediaType.MOVIE, force = true))
        assertEquals(1, remote.calls.size)
        assertEquals("Updated", dao.cache.value?.media?.title)
    }

    @Test
    fun staleCacheIsObservableBeforeFailedRefreshAndTimestampDoesNotChange() = runTest {
        val old = now.minusSeconds(90_000)
        val dao = FakeDetailsDao(cached(movieRef, old, "Cached"))
        val remote = FakeRemote { _, _ -> AppResult.Failure(AppError.NetworkUnavailable) }
        val repository = repository(dao, remote)

        val before = (repository.observeDetails(movieRef).first() as AppResult.Success).value
        assertEquals(CacheFreshness.STALE, before?.freshness)
        assertEquals(
            AppResult.Failure(AppError.NetworkUnavailable),
            repository.refreshDetails(movieRef, MediaType.MOVIE)
        )
        val after = (repository.observeDetails(movieRef).first() as AppResult.Success).value

        assertEquals("Cached", after?.details?.title)
        assertEquals(old, after?.fetchedAt)
    }

    @Test
    fun unsupportedProviderFailsWithoutRemoteRequest() = runTest {
        val remote = FakeRemote { ref, type -> AppResult.Success(details(ref, type, "Unexpected")) }
        val repository = repository(FakeDetailsDao(), remote)
        val jikan = ExternalMediaRef(MediaSource.JIKAN, "550")

        assertEquals(AppResult.Failure(AppError.UnsupportedData), repository.refreshDetails(jikan, MediaType.MOVIE))
        assertEquals(AppResult.Failure(AppError.UnsupportedData), repository.observeDetails(jikan).first())
        assertTrue(remote.calls.isEmpty())
    }

    @Test
    fun remoteSuccessFollowedByPersistenceFailureKeepsOldCache() = runTest {
        val old = now.minusSeconds(90_000)
        val dao = FakeDetailsDao(cached(movieRef, old, "Old"), failWrites = true)
        val remote = FakeRemote { ref, type -> AppResult.Success(details(ref, type, "New")) }
        val repository = repository(dao, remote)

        val result = repository.refreshDetails(movieRef, MediaType.MOVIE, force = true)

        assertEquals(AppResult.Failure(AppError.Unknown), result)
        assertEquals("Old", dao.cache.value?.media?.title)
        assertEquals(old, dao.cache.value?.details?.detailsFetchedAt)
    }

    @Test
    fun duplicateSameReferenceRefreshesAreCoalesced() = runTest {
        val gate = CompletableDeferred<Unit>()
        val entered = CompletableDeferred<Unit>()
        val remote = FakeRemote { ref, type ->
            entered.complete(Unit)
            gate.await()
            AppResult.Success(details(ref, type, "Remote"))
        }
        val repository = repository(FakeDetailsDao(), remote)

        val first = async { repository.refreshDetails(movieRef, MediaType.MOVIE, force = true) }
        entered.await()
        val second = async { repository.refreshDetails(movieRef, MediaType.MOVIE, force = true) }
        runCurrent()
        assertEquals(1, remote.calls.size)
        gate.complete(Unit)

        assertEquals(AppResult.Success(Unit), first.await())
        assertEquals(AppResult.Success(Unit), second.await())
    }

    @Test
    fun differentReferencesRefreshConcurrently() = runTest {
        val gate = CompletableDeferred<Unit>()
        val bothEntered = CompletableDeferred<Unit>()
        val enteredCount = java.util.concurrent.atomic.AtomicInteger()
        val remote = FakeRemote { ref, type ->
            if (enteredCount.incrementAndGet() == 2) bothEntered.complete(Unit)
            gate.await()
            AppResult.Success(details(ref, type, ref.externalId))
        }
        val repository = repository(FakeDetailsDao(), remote)

        val first = async { repository.refreshDetails(movieRef, MediaType.MOVIE, force = true) }
        val secondRef = ExternalMediaRef(MediaSource.TMDB, "551")
        val second = async { repository.refreshDetails(secondRef, MediaType.MOVIE, force = true) }
        bothEntered.await()
        assertEquals(2, remote.calls.size)
        gate.complete(Unit)
        assertTrue(first.await() is AppResult.Success)
        assertTrue(second.await() is AppResult.Success)
    }

    private fun repository(dao: FakeDetailsDao, remote: FakeRemote) = DefaultMediaDetailsRepository(
        detailsDao = dao,
        client = remote,
        freshnessPolicy = CacheFreshnessPolicy(clock),
        clock = clock,
        metadataStore = FakeMetadataStore(dao, FakeSeasonSummaryStore())
    )

    private fun details(ref: ExternalMediaRef, type: MediaType, title: String) = MediaDetails(
        externalRef = ref,
        mediaType = type,
        title = title,
        productionStatus = ProductionStatus.RELEASED
    )

    private fun cached(ref: ExternalMediaRef, fetchedAt: Instant, title: String): CachedDetailsRelation {
        val localId = ref.externalId.toLong()
        return CachedDetailsRelation(
            media = MediaEntity(
                localMediaId = localId,
                mediaType = MediaType.MOVIE,
                title = title,
                originalTitle = null,
                overview = null,
                posterUrl = null,
                releaseDate = LocalDate.of(2020, 1, 1),
                createdAt = fetchedAt,
                metadataUpdatedAt = fetchedAt
            ),
            details = MediaDetailsEntity(
                localMediaId = localId,
                backdropUrl = null,
                productionStatus = ProductionStatus.RELEASED.name,
                originalLanguage = null,
                runtimeMinutes = null,
                episodeRuntimeMinutes = null,
                numberOfSeasons = null,
                numberOfEpisodes = null,
                detailsFetchedAt = fetchedAt
            ),
            genres = emptyList(),
            externalRefs = listOf(ExternalRefEntity(localId, ref.source, ref.externalId))
        )
    }

    private class FakeRemote(private val result: suspend (ExternalMediaRef, MediaType) -> AppResult<MediaDetails>) :
        TmdbDetailsRemoteDataSource {
        val calls = mutableListOf<Pair<ExternalMediaRef, MediaType>>()
        override suspend fun load(
            reference: ExternalMediaRef,
            mediaType: MediaType
        ): AppResult<TmdbMediaDetailsPayload> {
            calls += reference to mediaType
            return when (val loaded = result(reference, mediaType)) {
                is AppResult.Success -> AppResult.Success(TmdbMediaDetailsPayload(loaded.value))
                is AppResult.Failure -> loaded
            }
        }
    }

    private class FakeSeasonSummaryStore : SeasonSummaryStore {
        override suspend fun upsertSeasonSummaries(
            source: MediaSource,
            seriesExternalId: String,
            summaries: List<SeasonEntity>
        ) = Unit
    }

    private class FakeMetadataStore(private val dao: FakeDetailsDao, private val seasons: SeasonSummaryStore) :
        MetadataCalendarStore {
        override suspend fun storeDetails(
            reference: ExternalMediaRef,
            details: MediaDetails,
            seasons: List<Season>,
            fetchedAt: Instant
        ) {
            val write = details.toCacheWrite(fetchedAt)
            dao.storeDetails(write.media, reference.source, reference.externalId, write.details, write.genres)
            this.seasons.upsertSeasonSummaries(
                reference.source,
                reference.externalId,
                seasons.map { it.toEntity(fetchedAt) }
            )
        }

        override suspend fun storeSeason(seriesRef: ExternalMediaRef, payload: TmdbSeasonPayload, fetchedAt: Instant) =
            error("Not used")
    }

    private class FakeDetailsDao(initial: CachedDetailsRelation? = null, private val failWrites: Boolean = false) :
        DetailsDao() {
        val cache = MutableStateFlow(initial)

        override fun observeCachedDetails(source: MediaSource, externalId: String): Flow<CachedDetailsRelation?> = cache
        override suspend fun getCachedDetails(source: MediaSource, externalId: String): CachedDetailsRelation? =
            cache.value?.takeIf { row -> row.externalRefs.any { it.source == source && it.externalId == externalId } }
        override suspend fun getMedia(source: MediaSource, externalId: String): MediaEntity? =
            getCachedDetails(source, externalId)?.media
        override suspend fun insertMedia(media: MediaEntity): Long = 1
        override suspend fun updateMedia(media: MediaEntity) = Unit
        override suspend fun insertExternalRef(externalRef: ExternalRefEntity) = Unit
        override suspend fun replaceDetails(details: MediaDetailsEntity) = Unit
        override suspend fun deleteGenres(localMediaId: Long) = Unit
        override suspend fun insertGenres(genres: List<MediaGenreEntity>) = Unit

        override suspend fun storeDetails(
            candidate: MediaEntity,
            source: MediaSource,
            externalId: String,
            details: MediaDetailsEntity,
            genres: List<MediaGenreEntity>
        ) {
            if (failWrites) throw RuntimeException("persistence failed")
            val localId = cache.value?.media?.localMediaId ?: externalId.toLong()
            cache.value = CachedDetailsRelation(
                media = candidate.copy(localMediaId = localId),
                details = details.copy(localMediaId = localId),
                genres = genres.map { it.copy(localMediaId = localId) },
                externalRefs = listOf(ExternalRefEntity(localId, source, externalId))
            )
        }
    }
}
