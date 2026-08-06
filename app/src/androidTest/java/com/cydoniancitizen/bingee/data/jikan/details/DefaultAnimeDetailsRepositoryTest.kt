package com.cydoniancitizen.bingee.data.jikan.details

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cydoniancitizen.bingee.core.common.TestingAnimeFeatureAvailability
import com.cydoniancitizen.bingee.core.model.AnimeCompletionOrigin
import com.cydoniancitizen.bingee.core.model.AnimeDetails
import com.cydoniancitizen.bingee.core.model.AnimeFormat
import com.cydoniancitizen.bingee.core.model.AnimeRelation
import com.cydoniancitizen.bingee.core.model.AnimeStatus
import com.cydoniancitizen.bingee.core.model.AnimeWatchProgress
import com.cydoniancitizen.bingee.core.model.CacheFreshness
import com.cydoniancitizen.bingee.core.model.CachedAnimeDetails
import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.result.AppError
import com.cydoniancitizen.bingee.core.result.AppResult
import com.cydoniancitizen.bingee.data.calendar.ReleaseEventProjector
import com.cydoniancitizen.bingee.data.details.CacheFreshnessPolicy
import com.cydoniancitizen.bingee.data.jikan.JikanDelay
import com.cydoniancitizen.bingee.data.jikan.JikanRequestGate
import com.cydoniancitizen.bingee.data.jikan.search.JikanAiredDto
import com.cydoniancitizen.bingee.data.jikan.search.JikanAnimeSearchResponseDto
import com.cydoniancitizen.bingee.data.jikan.search.JikanImagesDto
import com.cydoniancitizen.bingee.data.jikan.search.JikanJpgImageDto
import com.cydoniancitizen.bingee.data.jikan.search.JikanSearchService
import com.cydoniancitizen.bingee.data.library.local.BingeeDatabase
import com.cydoniancitizen.bingee.domain.repository.AnimeDetailsRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.Response

@RunWith(AndroidJUnit4::class)
class DefaultAnimeDetailsRepositoryTest {
    private lateinit var database: BingeeDatabase
    private lateinit var service: FakeJikanService
    private lateinit var metadataStore: AnimeMetadataStore
    private lateinit var repository: AnimeDetailsRepository
    private val now = Instant.parse("2026-08-05T10:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val animeRef = ExternalMediaRef(MediaSource.JIKAN, "52991")

    @Before
    fun createRepository() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BingeeDatabase::class.java).build()
        service = FakeJikanService()
        val client = JikanDetailsClient(
            service,
            JikanRequestGate(clock, JikanDelay { })
        )
        metadataStore = AnimeMetadataStore(
            database,
            database.animeDao(),
            database.releaseEventDao(),
            ReleaseEventProjector()
        )
        repository = DefaultAnimeDetailsRepository(
            database.animeDao(),
            client,
            metadataStore,
            CacheFreshnessPolicy(clock),
            clock,
            TestingAnimeFeatureAvailability(isAvailable = true)
        )
    }

    @After
    fun closeDatabase() = database.close()

    @Test
    fun cachedDetailsAreObservableBeforeForcedRemoteCompletion() = runTest {
        metadataStore.store(cached(), now.minusSeconds(60))
        val pending = CompletableDeferred<Response<JikanAnimeFullResponseDto>>()
        service.pending = pending

        val refresh = async { repository.refreshDetails(animeRef, force = true) }
        service.requestStarted.await()
        val cached = repository.observeDetails(animeRef).first().successValue()
        assertEquals("Cached title", cached?.details?.title)
        assertEquals(CacheFreshness.FRESH, cached?.freshness)

        pending.complete(Response.success(fullDto(title = "Remote title")))
        assertEquals(AppResult.Success(Unit), refresh.await())
        assertEquals("Remote title", repository.observeDetails(animeRef).first().successValue()?.details?.title)
    }

    @Test
    fun freshCacheAvoidsRequestAndForcedRefreshBypassesFreshness() = runTest {
        metadataStore.store(cached(), now.minusSeconds(60))

        assertEquals(AppResult.Success(Unit), repository.refreshDetails(animeRef))
        assertEquals(0, service.calls)

        service.response = Response.success(fullDto(title = "Forced title"))
        assertEquals(AppResult.Success(Unit), repository.refreshDetails(animeRef, force = true))
        assertEquals(1, service.calls)
        assertEquals("Forced title", repository.observeDetails(animeRef).first().successValue()?.details?.title)
    }

    @Test
    fun staleCacheRefreshesAndJikanFailureOrMalformedResponsePreservesIt() = runTest {
        metadataStore.store(cached(), now.minusSeconds(26 * 60 * 60))
        service.response = Response.success(fullDto(title = "Fresh title"))

        assertEquals(AppResult.Success(Unit), repository.refreshDetails(animeRef))
        assertEquals("Fresh title", repository.observeDetails(animeRef).first().successValue()?.details?.title)

        service.response = Response.error(503, okhttp3.ResponseBody.create(null, "unavailable"))
        assertEquals(
            AppResult.Failure(AppError.RemoteServiceFailure),
            repository.refreshDetails(animeRef, force = true)
        )
        assertEquals("Fresh title", repository.observeDetails(animeRef).first().successValue()?.details?.title)

        service.response = Response.success(malformedDto())
        assertEquals(
            AppResult.Failure(AppError.InvalidRemoteResponse),
            repository.refreshDetails(animeRef, force = true)
        )
        assertEquals("Fresh title", repository.observeDetails(animeRef).first().successValue()?.details?.title)
    }

    @Test
    fun detailsRelationsAndPremierePersistAndReplaceAtomically() = runTest {
        metadataStore.store(cached(), now)
        service.response = Response.success(
            fullDto(
                title = "Updated title",
                startDate = LocalDate.of(2026, 8, 20),
                relationIds = listOf(60000, 60001)
            )
        )

        assertEquals(AppResult.Success(Unit), repository.refreshDetails(animeRef, force = true))
        val row = database.animeDao().getAnime(animeRef.externalId)!!
        assertEquals("Updated title", row.details?.englishTitle)
        assertEquals(listOf("60000", "60001"), row.relations.map { it.relatedJikanId })
        assertEquals("2026-08-20", eventDate())

        assertEquals(AppResult.Success(Unit), repository.refreshDetails(animeRef, force = true))
        assertEquals(2, database.animeDao().getAnime(animeRef.externalId)!!.relations.size)

        service.response = Response.success(
            fullDto(
                title = "Changed date",
                startDate = LocalDate.of(2026, 8, 21),
                relationIds = listOf(60002)
            )
        )
        assertEquals(AppResult.Success(Unit), repository.refreshDetails(animeRef, force = true))
        assertEquals("2026-08-21", eventDate())
        assertEquals(
            listOf("60002"),
            database.animeDao().getAnime(animeRef.externalId)!!.relations.map {
                it.relatedJikanId
            }
        )

        service.response =
            Response.success(fullDto(title = "Removed date", startDate = null, relationIds = emptyList()))
        assertEquals(AppResult.Success(Unit), repository.refreshDetails(animeRef, force = true))
        assertNull(eventDate())
        assertTrue(database.animeDao().getAnime(animeRef.externalId)!!.relations.isEmpty())
    }

    @Test
    fun failedAtomicRelationReplacementPreservesDetailsRelationsAndPremiere() = runTest {
        metadataStore.store(cached(), now)
        service.response = Response.success(
            fullDto(
                title = "Broken replacement",
                startDate = LocalDate.of(2030, 1, 1),
                relationIds = listOf(60000, 60000)
            )
        )

        assertEquals(
            AppResult.Failure(AppError.LocalStorageFailure),
            repository.refreshDetails(animeRef, force = true)
        )
        val row = database.animeDao().getAnime(animeRef.externalId)!!
        assertEquals("Cached title", row.details?.englishTitle)
        assertEquals(listOf("60000"), row.relations.map { it.relatedJikanId })
        assertEquals("2026-08-05", eventDate())
    }

    @Test
    fun refreshPreservesProgressAndRatingWithoutLibraryMembership() = runTest {
        metadataStore.store(cached(), now)
        database.animeDao().setProgress(
            MediaSource.JIKAN,
            animeRef.externalId,
            AnimeWatchProgress(5, now, AnimeCompletionOrigin.EXPLICIT, now)
        )
        database.ratingDao().setRating(MediaSource.JIKAN, animeRef.externalId, 8, now)
        assertFalse(database.libraryDao().isInLibrary(MediaSource.JIKAN, animeRef.externalId))

        service.response = Response.success(fullDto(title = "Metadata only"))
        assertEquals(AppResult.Success(Unit), repository.refreshDetails(animeRef, force = true))

        val row = database.animeDao().getAnime(animeRef.externalId)!!
        assertEquals("Metadata only", row.details?.englishTitle)
        assertEquals(5, row.progress?.watchedEpisodeCount)
        assertEquals(now, row.progress?.completedAt)
        assertEquals(8, database.ratingDao().observeRating(MediaSource.JIKAN, animeRef.externalId).first()?.ratingValue)
        assertFalse(database.libraryDao().isInLibrary(MediaSource.JIKAN, animeRef.externalId))
    }

    @Test
    fun invalidTmdbReferenceDoesNotReadCredentialsOrFallbackToTmdb() = runTest {
        val tmdb = ExternalMediaRef(MediaSource.TMDB, animeRef.externalId)

        assertEquals(AppResult.Failure(AppError.InvalidInput), repository.refreshDetails(tmdb))
        assertEquals(0, service.calls)
        assertEquals(AppResult.Failure(AppError.InvalidInput), repository.observeDetails(tmdb).first())
    }

    @Test
    fun cancellationFromJikanPropagates() = runTest {
        service.failure = CancellationException("cancelled")
        try {
            repository.refreshDetails(animeRef, force = true)
            throw AssertionError("Expected cancellation")
        } catch (_: CancellationException) {
            // Repository must preserve structured cancellation.
        }
    }

    private fun cached() = AnimeDetails(
        externalRef = animeRef,
        title = "Cached title",
        englishTitle = "Cached title",
        japaneseTitle = "保存作品",
        synopsis = "Cached synopsis",
        format = AnimeFormat.TV,
        status = AnimeStatus.FINISHED,
        episodeCount = 12,
        duration = "24 min",
        startDate = LocalDate.of(2026, 8, 5),
        endDate = LocalDate.of(2026, 10, 28),
        providerScore = 8.0,
        relations = listOf(
            AnimeRelation(
                relation = "Sequel",
                animeRef = ExternalMediaRef(MediaSource.JIKAN, "60000"),
                title = "Old relation"
            )
        )
    )

    private fun fullDto(
        title: String,
        startDate: LocalDate? = LocalDate.of(2026, 8, 5),
        relationIds: List<Int> = listOf(60000)
    ) = JikanAnimeFullResponseDto(
        data = JikanAnimeFullDto(
            malId = animeRef.externalId.toInt(),
            title = title,
            titleEnglish = title,
            titleJapanese = "日本語 $title",
            synopsis = "Synopsis",
            images = JikanImagesDto(JikanJpgImageDto(null, null)),
            type = "TV",
            status = "Finished Airing",
            episodes = 12,
            duration = "24 min",
            aired = JikanAiredDto(startDate?.toString(), null),
            season = "summer",
            year = 2026,
            score = 8.5,
            relations = relationIds.map {
                JikanRelationDto(
                    relation = "Sequel",
                    entry = listOf(JikanRelationEntryDto(it, "anime", "Relation $it"))
                )
            }
        )
    )

    private fun malformedDto() = JikanAnimeFullResponseDto(
        data = JikanAnimeFullDto(
            malId = null,
            title = null,
            titleEnglish = null,
            titleJapanese = null,
            synopsis = null,
            images = null,
            type = null,
            status = null,
            episodes = null,
            duration = null,
            aired = null,
            season = null,
            year = null,
            score = null,
            relations = null
        )
    )

    private fun eventDate(): String? = database.openHelper.readableDatabase.query(
        "SELECT event_date FROM release_events WHERE source = 'JIKAN' AND subject_external_id = '52991'"
    ).use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }

    private fun AppResult<CachedAnimeDetails?>.successValue(): CachedAnimeDetails? = (this as AppResult.Success).value

    private class FakeJikanService : JikanSearchService {
        var response: Response<JikanAnimeFullResponseDto> = Response.success(
            JikanAnimeFullResponseDto(null)
        )
        var pending: CompletableDeferred<Response<JikanAnimeFullResponseDto>>? = null
        var failure: Throwable? = null
        var calls = 0
        val requestStarted = CompletableDeferred<Unit>()

        override suspend fun searchAnime(
            query: String,
            page: Int,
            safeForWork: Boolean
        ): Response<JikanAnimeSearchResponseDto> = error("Search is outside this repository test")

        override suspend fun getAnimeFull(id: Int): Response<JikanAnimeFullResponseDto> {
            calls++
            requestStarted.complete(Unit)
            failure?.let { throw it }
            return pending?.await() ?: response
        }
    }
}
