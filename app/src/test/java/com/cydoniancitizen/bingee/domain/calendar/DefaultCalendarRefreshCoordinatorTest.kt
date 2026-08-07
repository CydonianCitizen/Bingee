package com.cydoniancitizen.bingee.domain.calendar

import com.cydoniancitizen.bingee.core.credential.TmdbCredentialStatus
import com.cydoniancitizen.bingee.core.model.CacheFreshness
import com.cydoniancitizen.bingee.core.model.CachedMediaDetails
import com.cydoniancitizen.bingee.core.model.CachedSeason
import com.cydoniancitizen.bingee.core.model.CalendarRefreshOutcome
import com.cydoniancitizen.bingee.core.model.CalendarRefreshSummary
import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.LibraryEntry
import com.cydoniancitizen.bingee.core.model.MediaDetails
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.core.model.ReleaseCalendarWindow
import com.cydoniancitizen.bingee.core.model.ReleaseEvent
import com.cydoniancitizen.bingee.core.model.Season
import com.cydoniancitizen.bingee.core.model.SeasonProgress
import com.cydoniancitizen.bingee.core.result.AppError
import com.cydoniancitizen.bingee.core.result.AppResult
import com.cydoniancitizen.bingee.debug.FakeLibraryRepository
import com.cydoniancitizen.bingee.domain.repository.MediaDetailsRepository
import com.cydoniancitizen.bingee.domain.repository.ReleaseCalendarRepository
import com.cydoniancitizen.bingee.domain.repository.SeriesRepository
import com.cydoniancitizen.bingee.domain.repository.TmdbCredentialRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultCalendarRefreshCoordinatorTest {
    private val now = Instant.parse("2026-08-03T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    @Test
    fun emptyLibraryAndMissingCredentialDoNoRemoteWorkAndDoNotMarkSuccess() = runTest {
        val calendar = FakeCalendarRepository()
        val details = FakeDetailsRepository()
        val empty = coordinator(emptyList(), details, FakeSeriesRepository(), calendar)

        assertEquals(CalendarRefreshOutcome.NO_WORK, empty.refresh().outcome)
        assertEquals(0, calendar.markCalls)

        val blocked = coordinator(
            listOf(entry("1", MediaType.MOVIE)),
            details,
            FakeSeriesRepository(),
            calendar,
            TmdbCredentialStatus.NotConfigured
        )
        assertEquals(CalendarRefreshOutcome.CREDENTIAL_REQUIRED, blocked.refresh().outcome)
        assertTrue(details.calls.isEmpty())
        assertEquals(0, calendar.markCalls)
    }

    @Test
    fun movieSuccessBackfillsAndMarksSuccessfulTimestamp() = runTest {
        val calendar = FakeCalendarRepository()
        val details = FakeDetailsRepository()
        val summary = coordinator(
            listOf(entry("1", MediaType.MOVIE)),
            details,
            FakeSeriesRepository(),
            calendar
        ).refresh()

        assertEquals(CalendarRefreshOutcome.COMPLETE_SUCCESS, summary.outcome)
        assertEquals(1, summary.operationsSucceeded)
        assertEquals(1, calendar.backfillCalls)
        assertEquals(now, calendar.lastMarked)
    }

    @Test
    fun seasonFailureIsPartialAndDoesNotCancelOtherSelectedSeasons() = runTest {
        val seriesRef = ref("10")
        val cached = listOf(
            season(seriesRef, 1, LocalDate.of(2020, 1, 1), episodesCached = true),
            season(seriesRef, 2, LocalDate.of(2026, 9, 1), episodesCached = false)
        )
        val series = FakeSeriesRepository(
            seasons = mapOf(seriesRef to cached),
            results = mapOf(1 to AppResult.Failure(AppError.NetworkUnavailable))
        )
        val calendar = FakeCalendarRepository()

        val summary = coordinator(
            listOf(entry("10", MediaType.SERIES)),
            FakeDetailsRepository(),
            series,
            calendar
        ).refresh()

        assertEquals(CalendarRefreshOutcome.PARTIAL_SUCCESS, summary.outcome)
        assertEquals(listOf(1, 2), series.calls)
        assertTrue(summary.operationsSucceeded >= 2)
        assertEquals(1, summary.operationsFailed)
        assertEquals(1, calendar.markCalls)
    }

    @Test
    fun titleConcurrencyNeverExceedsThreeAndCancellationPropagates() = runTest {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val details = FakeDetailsRepository { _, _ ->
            if (activeCalls == 3) entered.complete(Unit)
            release.await()
            AppResult.Success(Unit)
        }
        val coordinator = coordinator(
            (1..4).map { entry(it.toString(), MediaType.MOVIE) },
            details,
            FakeSeriesRepository(),
            FakeCalendarRepository()
        )

        val refresh = async { coordinator.refresh() }
        entered.await()
        assertEquals(3, details.maxActive)
        release.complete(Unit)
        runCurrent()
        assertEquals(CalendarRefreshOutcome.COMPLETE_SUCCESS, refresh.await().outcome)
        assertEquals(3, details.maxActive)
    }

    @Test
    fun cancellationStopsProviderRefreshWithoutProducingASummary() = runTest {
        val requestStarted = CompletableDeferred<Unit>()
        val details = FakeDetailsRepository { _, _ ->
            requestStarted.complete(Unit)
            awaitCancellation()
        }
        val refresh = async {
            coordinator(
                listOf(entry("1", MediaType.MOVIE)),
                details,
                FakeSeriesRepository(),
                FakeCalendarRepository()
            ).refresh()
        }

        requestStarted.await()
        refresh.cancelAndJoin()

        assertEquals(0, details.activeCalls)
    }

    private fun coordinator(
        entries: List<LibraryEntry>,
        details: FakeDetailsRepository,
        series: FakeSeriesRepository,
        calendar: FakeCalendarRepository,
        credential: TmdbCredentialStatus = TmdbCredentialStatus.Valid
    ) = DefaultCalendarRefreshCoordinator(
        libraryRepository = FakeLibraryRepository(entries),
        detailsRepository = details,
        seriesRepository = series,
        calendarRepository = calendar,
        credentialRepository = FakeCredentialRepository(credential),
        clock = clock,
        window = ReleaseCalendarWindow()
    )

    private fun entry(id: String, type: MediaType) = LibraryEntry(
        mediaRef = ref(id),
        mediaType = type,
        title = "Title $id",
        addedAt = now
    )

    private fun season(seriesRef: ExternalMediaRef, number: Int, date: LocalDate?, episodesCached: Boolean) =
        CachedSeason(
            season = Season(
                seriesRef = seriesRef,
                externalRef = ref("s$number"),
                seasonNumber = number,
                airDate = date
            ),
            metadataUpdatedAt = now,
            episodesFetchedAt = now.takeIf { episodesCached },
            episodes = emptyList(),
            progress = SeasonProgress.EMPTY,
            episodeCacheFreshness = CacheFreshness.FRESH.takeIf { episodesCached }
        )

    private fun ref(id: String) = ExternalMediaRef(MediaSource.TMDB, id)

    private class FakeDetailsRepository(
        private val result: suspend FakeDetailsRepository.(ExternalMediaRef, MediaType) -> AppResult<Unit> =
            { _, _ -> AppResult.Success(Unit) }
    ) : MediaDetailsRepository {
        val calls = mutableListOf<Pair<ExternalMediaRef, MediaType>>()
        var activeCalls = 0
        var maxActive = 0

        override fun observeDetails(reference: ExternalMediaRef): Flow<AppResult<CachedMediaDetails?>> =
            flowOf(AppResult.Success(null))

        override suspend fun refreshDetails(
            reference: ExternalMediaRef,
            mediaType: MediaType,
            force: Boolean
        ): AppResult<Unit> {
            calls += reference to mediaType
            activeCalls++
            maxActive = maxOf(maxActive, activeCalls)
            return try {
                result(reference, mediaType)
            } finally {
                activeCalls--
            }
        }
    }

    private class FakeSeriesRepository(
        private val seasons: Map<ExternalMediaRef, List<CachedSeason>> = emptyMap(),
        private val results: Map<Int, AppResult<Unit>> = emptyMap()
    ) : SeriesRepository {
        val calls = mutableListOf<Int>()
        override fun observeSeasons(seriesRef: ExternalMediaRef): Flow<AppResult<List<CachedSeason>>> =
            flowOf(AppResult.Success(seasons[seriesRef].orEmpty()))

        override suspend fun refreshSeason(
            seriesRef: ExternalMediaRef,
            seasonNumber: Int,
            force: Boolean
        ): AppResult<Unit> {
            calls += seasonNumber
            return results[seasonNumber] ?: AppResult.Success(Unit)
        }
    }

    private class FakeCalendarRepository : ReleaseCalendarRepository {
        var backfillCalls = 0
        var markCalls = 0
        var lastMarked: Instant? = null
        override fun observeEvents(fromDate: LocalDate): Flow<AppResult<List<ReleaseEvent>>> =
            flowOf(AppResult.Success(emptyList()))
        override fun observeLastSuccessfulRefresh(): Flow<AppResult<Instant?>> = flowOf(AppResult.Success(lastMarked))
        override suspend fun getEvents(
            fromDate: LocalDate,
            throughDate: LocalDate,
            limit: Int
        ): AppResult<List<ReleaseEvent>> = AppResult.Success(emptyList())
        override suspend fun backfill(): AppResult<Unit> {
            backfillCalls++
            return AppResult.Success(Unit)
        }
        override suspend fun markRefreshSuccessful(at: Instant): AppResult<Unit> {
            markCalls++
            lastMarked = at
            return AppResult.Success(Unit)
        }
    }

    private class FakeCredentialRepository(initial: TmdbCredentialStatus) : TmdbCredentialRepository {
        override val status: StateFlow<TmdbCredentialStatus> = MutableStateFlow(initial)
        override suspend fun refreshLocalStatus() = Unit
        override suspend fun validateAndSave(input: String): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun revalidateStored(): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun remove(): AppResult<Unit> = AppResult.Success(Unit)
        override fun cancelValidation() = Unit
    }
}
