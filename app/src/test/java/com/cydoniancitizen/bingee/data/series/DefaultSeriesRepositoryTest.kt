package com.cydoniancitizen.bingee.data.series

import com.cydoniancitizen.bingee.core.model.Episode
import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.MediaDetails
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.Season
import com.cydoniancitizen.bingee.core.result.AppError
import com.cydoniancitizen.bingee.core.result.AppResult
import com.cydoniancitizen.bingee.data.calendar.MetadataCalendarStore
import com.cydoniancitizen.bingee.data.library.local.EpisodeEntity
import com.cydoniancitizen.bingee.data.library.local.MediaEntity
import com.cydoniancitizen.bingee.data.library.local.SeasonEntity
import com.cydoniancitizen.bingee.data.library.local.SeasonWithEpisodesRelation
import com.cydoniancitizen.bingee.data.library.local.SeriesDao
import com.cydoniancitizen.bingee.data.library.local.StoredSeasonEpisodes
import com.cydoniancitizen.bingee.data.tmdb.series.TmdbSeasonPayload
import com.cydoniancitizen.bingee.data.tmdb.series.TmdbSeasonRemoteDataSource
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultSeriesRepositoryTest {
    private val now = Instant.parse("2026-08-03T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val seriesRef = ref("100")

    @Test
    fun freshCacheAvoidsNetworkWhileForcedRefreshUpdatesOnlyAfterSuccess() = runTest {
        val dao = FakeSeriesDao(cachedAt = now.minusSeconds(60))
        val remote = FakeRemote { number -> AppResult.Success(payload(number)) }
        val repository = repository(dao, remote)

        assertEquals(AppResult.Success(Unit), repository.refreshSeason(seriesRef, 1))
        assertTrue(remote.calls.isEmpty())
        assertEquals(AppResult.Success(Unit), repository.refreshSeason(seriesRef, 1, force = true))
        assertEquals(listOf(1), remote.calls)
        assertEquals(now, dao.storedAt)
    }

    @Test
    fun staleAndMissingCacheRefreshWhileFailureLeavesFreshnessUntouched() = runTest {
        val old = now.minusSeconds(90_000)
        val dao = FakeSeriesDao(cachedAt = old)
        val remote = FakeRemote { AppResult.Failure(AppError.NetworkUnavailable) }
        val repository = repository(dao, remote)

        assertEquals(
            AppResult.Failure(AppError.NetworkUnavailable),
            repository.refreshSeason(seriesRef, 1)
        )
        assertEquals(old, dao.cachedAt)
        assertEquals(null, dao.storedAt)

        dao.cachedAt = null
        assertEquals(
            AppResult.Failure(AppError.NetworkUnavailable),
            repository.refreshSeason(seriesRef, 1)
        )
        assertEquals(listOf(1, 1), remote.calls)
    }

    @Test
    fun duplicateRefreshCoalescesAndDifferentSeasonsProceedIndependently() = runTest {
        val gate = CompletableDeferred<Unit>()
        val entered = mutableSetOf<Int>()
        val bothEntered = CompletableDeferred<Unit>()
        val remote = FakeRemote { number ->
            entered += number
            if (entered.size == 2) bothEntered.complete(Unit)
            gate.await()
            AppResult.Success(payload(number))
        }
        val repository = repository(FakeSeriesDao(), remote)

        val first = async { repository.refreshSeason(seriesRef, 1, force = true) }
        runCurrent()
        val duplicate = async { repository.refreshSeason(seriesRef, 1, force = true) }
        val other = async { repository.refreshSeason(seriesRef, 2, force = true) }
        bothEntered.await()
        assertEquals(listOf(1, 2), remote.calls)
        gate.complete(Unit)

        assertEquals(AppResult.Success(Unit), first.await())
        assertEquals(AppResult.Success(Unit), duplicate.await())
        assertEquals(AppResult.Success(Unit), other.await())
    }

    @Test
    fun unsupportedProviderAndMalformedPayloadFailSafely() = runTest {
        val remote = FakeRemote { AppResult.Success(payload(2)) }
        val repository = repository(FakeSeriesDao(), remote)

        assertEquals(
            AppResult.Failure(AppError.UnsupportedData),
            repository.refreshSeason(ExternalMediaRef(MediaSource.IMDB, "100"), 1)
        )
        assertEquals(
            AppResult.Failure(AppError.InvalidRemoteResponse),
            repository.refreshSeason(seriesRef, 1, force = true)
        )
    }

    @Test
    fun persistenceFailureDoesNotAdvanceCachedFreshness() = runTest {
        val old = now.minusSeconds(90_000)
        val dao = FakeSeriesDao(cachedAt = old, failWrites = true)
        val repository = repository(
            dao,
            FakeRemote { number -> AppResult.Success(payload(number)) }
        )

        assertEquals(
            AppResult.Failure(AppError.Unknown),
            repository.refreshSeason(seriesRef, 1, force = true)
        )
        assertEquals(old, dao.cachedAt)
        assertEquals(null, dao.storedAt)
    }

    private fun repository(dao: FakeSeriesDao, remote: FakeRemote) = DefaultSeriesRepository(
        seriesDao = dao,
        metadataStore = FakeMetadataStore(dao),
        remote = remote,
        freshnessPolicy = SeasonCacheFreshnessPolicy(clock),
        clock = clock
    )

    private fun payload(number: Int): TmdbSeasonPayload {
        val seasonRef = ref((10 + number).toString())
        return TmdbSeasonPayload(
            Season(seriesRef, seasonRef, number, name = "Season $number", episodeCount = 1),
            listOf(Episode(seriesRef, seasonRef, ref((100 + number).toString()), number, 1, "Episode"))
        )
    }

    private fun ref(id: String) = ExternalMediaRef(MediaSource.TMDB, id)

    private class FakeRemote(private val result: suspend (Int) -> AppResult<TmdbSeasonPayload>) :
        TmdbSeasonRemoteDataSource {
        val calls = mutableListOf<Int>()
        override suspend fun load(seriesRef: ExternalMediaRef, seasonNumber: Int): AppResult<TmdbSeasonPayload> {
            calls += seasonNumber
            return result(seasonNumber)
        }
    }

    private class FakeMetadataStore(private val dao: FakeSeriesDao) : MetadataCalendarStore {
        override suspend fun storeDetails(
            reference: ExternalMediaRef,
            details: MediaDetails,
            seasons: List<Season>,
            fetchedAt: Instant
        ) = error("Not used")

        override suspend fun storeSeason(seriesRef: ExternalMediaRef, payload: TmdbSeasonPayload, fetchedAt: Instant) {
            dao.storeSeasonEpisodes(
                seriesRef.source,
                seriesRef.externalId,
                payload.season.toEntity(fetchedAt),
                payload.episodes.map { it.toEntity(fetchedAt) },
                fetchedAt
            )
        }
    }

    private class FakeSeriesDao(var cachedAt: Instant? = null, private val failWrites: Boolean = false) : SeriesDao() {
        var storedAt: Instant? = null
        private val rows = MutableStateFlow<List<SeasonWithEpisodesRelation>>(emptyList())

        override fun observeSeriesSeasons(
            source: MediaSource,
            seriesExternalId: String
        ): Flow<List<SeasonWithEpisodesRelation>> = rows

        override fun observeSeason(
            source: MediaSource,
            seriesExternalId: String,
            seasonExternalId: String
        ): Flow<SeasonWithEpisodesRelation?> = MutableStateFlow(null)

        override suspend fun getSeason(source: MediaSource, seasonExternalId: String): SeasonEntity? = null

        override suspend fun getSeasonForSeries(
            source: MediaSource,
            seriesExternalId: String,
            seasonNumber: Int
        ): SeasonEntity? = cachedAt?.let { seasonEntity(seasonNumber, it) }

        override suspend fun storeSeasonEpisodes(
            source: MediaSource,
            seriesExternalId: String,
            season: SeasonEntity,
            episodes: List<EpisodeEntity>,
            fetchedAt: Instant
        ): StoredSeasonEpisodes {
            if (failWrites) throw RuntimeException("persistence failed")
            storedAt = fetchedAt
            cachedAt = fetchedAt
            return StoredSeasonEpisodes(season, episodes)
        }

        override suspend fun upsertSeasonSummaries(
            source: MediaSource,
            seriesExternalId: String,
            summaries: List<SeasonEntity>
        ) = Unit

        override suspend fun getMedia(source: MediaSource, externalId: String): MediaEntity? = null
        override suspend fun getSeasonByNumber(localMediaId: Long, seasonNumber: Int): SeasonEntity? = null
        override suspend fun insertSeason(season: SeasonEntity): Long = 1
        override suspend fun updateSeason(season: SeasonEntity) = Unit
        override suspend fun getEpisode(source: MediaSource, externalId: String): EpisodeEntity? = null
        override suspend fun getEpisodesForSeason(localSeasonId: Long): List<EpisodeEntity> = emptyList()

        override suspend fun getEpisodesByExternalIds(
            source: MediaSource,
            externalIds: List<String>
        ): List<EpisodeEntity> = emptyList()

        override suspend fun insertEpisodes(episodes: List<EpisodeEntity>): List<Long> =
            episodes.indices.map { it + 1L }

        override suspend fun updateEpisodes(episodes: List<EpisodeEntity>) = Unit
        override suspend fun updateEpisodesFetchedAt(localSeasonId: Long, fetchedAt: Instant) = Unit

        private fun seasonEntity(number: Int, fetchedAt: Instant) = SeasonEntity(
            localSeasonId = 1,
            localMediaId = 1,
            source = MediaSource.TMDB,
            externalId = (10 + number).toString(),
            seasonNumber = number,
            name = "Season $number",
            overview = null,
            posterUrl = null,
            airDate = null,
            episodeCount = 1,
            metadataUpdatedAt = fetchedAt,
            episodesFetchedAt = fetchedAt
        )
    }
}
