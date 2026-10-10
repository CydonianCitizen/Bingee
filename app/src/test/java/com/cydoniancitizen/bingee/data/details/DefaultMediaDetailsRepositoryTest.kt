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
import com.cydoniancitizen.bingee.data.CacheFreshnessPolicy
import com.cydoniancitizen.bingee.data.calendar.MetadataCalendarStore
import com.cydoniancitizen.bingee.data.library.local.CachedDetailsRelation
import com.cydoniancitizen.bingee.data.library.local.DetailsDao
import com.cydoniancitizen.bingee.data.library.local.ExternalRefEntity
import com.cydoniancitizen.bingee.data.library.local.MediaDetailsEntity
import com.cydoniancitizen.bingee.data.library.local.MediaEntity
import com.cydoniancitizen.bingee.data.library.local.MediaGenreEntity
import com.cydoniancitizen.bingee.data.settings.AppLanguage
import com.cydoniancitizen.bingee.data.settings.AppTheme
import com.cydoniancitizen.bingee.data.settings.AppearancePreferences
import com.cydoniancitizen.bingee.data.settings.toTmdbLanguageTag
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
import kotlinx.coroutines.flow.flowOf
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
    private val movieId = 550L

    @Test
    fun cacheMissSuccessPersistsAndUpdatesSuccessfulFetchTimestamp() = runTest {
        val dao = FakeDetailsDao()
        val remote = FakeRemote { ref, type -> AppResult.Success(details(ref, type, "Remote")) }
        val repository = repository(dao, remote)

        assertNull((repository.observeDetails(movieId, MediaType.MOVIE).first() as AppResult.Success).value)
        assertEquals(AppResult.Success(Unit), repository.refreshDetails(movieId, MediaType.MOVIE))
        val cached = (repository.observeDetails(movieId, MediaType.MOVIE).first() as AppResult.Success).value

        assertEquals("Remote", cached?.details?.title)
        assertEquals(now, cached?.fetchedAt)
        assertEquals(1, remote.calls.size)
    }

    @Test
    fun freshCacheSkipsAutomaticNetworkButManualRefreshBypassesFreshness() = runTest {
        val dao = FakeDetailsDao(cached(movieRef, now.minusSeconds(60), "Cached"))
        val remote = FakeRemote { ref, type -> AppResult.Success(details(ref, type, "Updated")) }
        val repository = repository(dao, remote)

        assertEquals(AppResult.Success(Unit), repository.refreshDetails(movieId, MediaType.MOVIE, force = false))
        assertEquals(0, remote.calls.size)
        assertEquals(AppResult.Success(Unit), repository.refreshDetails(movieId, MediaType.MOVIE, force = true))
        assertEquals(1, remote.calls.size)
        assertEquals("Updated", dao.cache.value?.media?.title)
    }

    @Test
    fun staleCacheIsObservableBeforeFailedRefreshAndTimestampDoesNotChange() = runTest {
        val old = now.minusSeconds(90_000)
        val dao = FakeDetailsDao(cached(movieRef, old, "Cached"))
        val remote = FakeRemote { _, _ -> AppResult.Failure(AppError.NetworkUnavailable) }
        val repository = repository(dao, remote)

        val before = (repository.observeDetails(movieId, MediaType.MOVIE).first() as AppResult.Success).value
        assertEquals(CacheFreshness.STALE, before?.freshness)
        assertEquals(
            AppResult.Failure(AppError.NetworkUnavailable),
            repository.refreshDetails(movieId, MediaType.MOVIE)
        )
        val after = (repository.observeDetails(movieId, MediaType.MOVIE).first() as AppResult.Success).value

        assertEquals("Cached", after?.details?.title)
        assertEquals(old, after?.fetchedAt)
    }

    @Test
    fun invalidTmdbIdFailsWithoutRemoteRequest() = runTest {
        val remote = FakeRemote { ref, type -> AppResult.Success(details(ref, type, "Unexpected")) }
        val repository = repository(FakeDetailsDao(), remote)

        assertEquals(AppResult.Failure(AppError.InvalidInput), repository.refreshDetails(0, MediaType.MOVIE))
        assertEquals(AppResult.Failure(AppError.InvalidInput), repository.observeDetails(0, MediaType.MOVIE).first())
        assertTrue(remote.calls.isEmpty())
    }

    @Test
    fun remoteSuccessFollowedByPersistenceFailureKeepsOldCache() = runTest {
        val old = now.minusSeconds(90_000)
        val dao = FakeDetailsDao(cached(movieRef, old, "Old"), failWrites = true)
        val remote = FakeRemote { ref, type -> AppResult.Success(details(ref, type, "New")) }
        val repository = repository(dao, remote)

        val result = repository.refreshDetails(movieId, MediaType.MOVIE, force = true)

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

        val first = async { repository.refreshDetails(movieId, MediaType.MOVIE, force = true) }
        entered.await()
        val second = async { repository.refreshDetails(movieId, MediaType.MOVIE, force = true) }
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

        val first = async { repository.refreshDetails(movieId, MediaType.MOVIE, force = true) }
        val second = async { repository.refreshDetails(551, MediaType.MOVIE, force = true) }
        bothEntered.await()
        assertEquals(2, remote.calls.size)
        gate.complete(Unit)
        assertTrue(first.await() is AppResult.Success)
        assertTrue(second.await() is AppResult.Success)
    }

    @Test
    fun notFoundLeavesCachedDetailsAndSuccessTimestampUntouched() = runTest {
        val old = now.minusSeconds(90_000)
        val dao = FakeDetailsDao(cached(movieRef, old, "Cached"))
        val repository = repository(dao, FakeRemote { _, _ -> AppResult.Failure(AppError.MissingData) })

        assertEquals(AppResult.Failure(AppError.MissingData), repository.refreshDetails(movieId, MediaType.MOVIE))
        assertEquals("Cached", dao.cache.value?.media?.title)
        assertEquals(old, dao.cache.value?.details?.detailsFetchedAt)
    }

    @Test
    fun languageChangeKeepsCachedTextVisibleButStaleUntilRefreshedInTheNewLanguage() = runTest {
        val appearance = FakeAppearancePreferences()
        val fetchedAt = now.minusSeconds(60)
        val dao = FakeDetailsDao(cached(movieRef, fetchedAt, "English title", language = "en-US"))
        val remote = FakeRemote { _, _ -> AppResult.Failure(AppError.NetworkUnavailable) }
        val repository = repository(dao, remote, appearance)

        // No language change: the young cache stays fresh and skips the network.
        assertEquals(CacheFreshness.FRESH, observed(repository)?.freshness)
        assertEquals(AppResult.Success(Unit), repository.refreshDetails(movieId, MediaType.MOVIE))
        assertTrue(remote.calls.isEmpty())

        appearance.language.value = AppLanguage.ITALIAN
        val switched = observed(repository)
        assertEquals("English title", switched?.details?.title)
        assertEquals(CacheFreshness.STALE, switched?.freshness)

        // A failed Italian refresh keeps the English text and its success timestamp.
        assertEquals(
            AppResult.Failure(AppError.NetworkUnavailable),
            repository.refreshDetails(movieId, MediaType.MOVIE)
        )
        assertEquals("English title", observed(repository)?.details?.title)
        assertEquals(fetchedAt, dao.cache.value?.details?.detailsFetchedAt)

        // A reply to a request made in English before the change is stored as English, so it stays stale.
        remote.language = "en-US"
        remote.result = { ref, type -> AppResult.Success(details(ref, type, "English title")) }
        assertEquals(AppResult.Success(Unit), repository.refreshDetails(movieId, MediaType.MOVIE))
        assertEquals(CacheFreshness.STALE, observed(repository)?.freshness)

        remote.language = "it-IT"
        remote.result = { ref, type -> AppResult.Success(details(ref, type, "Titolo italiano")) }
        assertEquals(AppResult.Success(Unit), repository.refreshDetails(movieId, MediaType.MOVIE))
        val italian = observed(repository)
        assertEquals("Titolo italiano", italian?.details?.title)
        assertEquals(CacheFreshness.FRESH, italian?.freshness)
        assertEquals(3, remote.calls.size)
    }

    private suspend fun observed(repository: DefaultMediaDetailsRepository) =
        (repository.observeDetails(movieId, MediaType.MOVIE).first() as AppResult.Success).value

    private fun repository(
        dao: FakeDetailsDao,
        remote: FakeRemote,
        appearance: AppearancePreferences = FakeAppearancePreferences()
    ) = DefaultMediaDetailsRepository(
        detailsDao = dao,
        client = remote,
        freshnessPolicy = CacheFreshnessPolicy(clock),
        clock = clock,
        metadataStore = FakeMetadataStore(dao),
        appearancePreferences = appearance
    )

    private class FakeAppearancePreferences : AppearancePreferences {
        val language = MutableStateFlow(AppLanguage.ENGLISH)
        override fun observeTheme(): Flow<AppTheme> = flowOf(AppTheme.SYSTEM_DEFAULT)
        override suspend fun setTheme(theme: AppTheme) = Unit
        override fun observeLanguage(): Flow<AppLanguage> = language
        override suspend fun setLanguage(language: AppLanguage) {
            this.language.value = language
        }
        override suspend fun getEffectiveTmdbLanguage(): String = language.value.toTmdbLanguageTag()
    }

    private fun details(ref: ExternalMediaRef, type: MediaType, title: String) = MediaDetails(
        externalRef = ref,
        mediaType = type,
        title = title,
        productionStatus = ProductionStatus.RELEASED
    )

    private fun cached(
        ref: ExternalMediaRef,
        fetchedAt: Instant,
        title: String,
        language: String? = null
    ): CachedDetailsRelation {
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
                detailsFetchedAt = fetchedAt,
                language = language
            ),
            genres = emptyList(),
            externalRefs = listOf(ExternalRefEntity(localId, ref.source, MediaType.MOVIE, ref.externalId))
        )
    }

    private class FakeRemote(var result: suspend (ExternalMediaRef, MediaType) -> AppResult<MediaDetails>) :
        TmdbDetailsRemoteDataSource {
        val calls = mutableListOf<Pair<ExternalMediaRef, MediaType>>()
        var language: String? = null
        override suspend fun load(tmdbId: Long, mediaType: MediaType): AppResult<TmdbMediaDetailsPayload> {
            val reference = ExternalMediaRef(MediaSource.TMDB, tmdbId.toString())
            calls += reference to mediaType
            return when (val loaded = result(reference, mediaType)) {
                is AppResult.Success -> AppResult.Success(TmdbMediaDetailsPayload(loaded.value, language = language))
                is AppResult.Failure -> loaded
            }
        }
    }

    private class FakeMetadataStore(private val dao: FakeDetailsDao) : MetadataCalendarStore {
        override suspend fun storeDetails(
            reference: ExternalMediaRef,
            details: MediaDetails,
            seasons: List<Season>,
            fetchedAt: Instant,
            language: String?
        ) {
            val write = details.toCacheWrite(fetchedAt, language)
            dao.storeDetails(write.media, reference.source, reference.externalId, write.details, write.genres)
        }

        override suspend fun storeSeason(seriesRef: ExternalMediaRef, payload: TmdbSeasonPayload, fetchedAt: Instant) =
            error("Not used")
    }

    private class FakeDetailsDao(initial: CachedDetailsRelation? = null, private val failWrites: Boolean = false) :
        DetailsDao() {
        val cache = MutableStateFlow(initial)

        override fun observeCachedDetails(
            source: MediaSource,
            mediaType: MediaType,
            externalId: String
        ): Flow<CachedDetailsRelation?> = cache
        override suspend fun getCachedDetails(
            source: MediaSource,
            mediaType: MediaType,
            externalId: String
        ): CachedDetailsRelation? = cache.value?.takeIf { row ->
            row.externalRefs.any {
                it.source == source && it.mediaType == mediaType && it.externalId == externalId
            }
        }
        override suspend fun getMedia(source: MediaSource, mediaType: MediaType, externalId: String): MediaEntity? =
            getCachedDetails(source, mediaType, externalId)?.media
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
                externalRefs = listOf(ExternalRefEntity(localId, source, candidate.mediaType, externalId))
            )
        }
    }
}
