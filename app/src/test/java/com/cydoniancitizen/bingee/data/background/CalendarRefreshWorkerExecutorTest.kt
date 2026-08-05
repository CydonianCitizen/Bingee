package com.cydoniancitizen.bingee.data.background

import com.cydoniancitizen.bingee.core.credential.TmdbCredentialStatus
import com.cydoniancitizen.bingee.core.model.BackgroundRefreshTarget
import com.cydoniancitizen.bingee.core.model.CalendarRefreshOutcome
import com.cydoniancitizen.bingee.core.model.CalendarRefreshSummary
import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.core.result.AppError
import com.cydoniancitizen.bingee.core.result.AppResult
import com.cydoniancitizen.bingee.domain.background.BackgroundWorkScheduler
import com.cydoniancitizen.bingee.domain.repository.BackgroundRefreshPlanner
import com.cydoniancitizen.bingee.domain.repository.CalendarRefreshCoordinator
import com.cydoniancitizen.bingee.domain.repository.TmdbCredentialRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarRefreshWorkerExecutorTest {
    private val target = BackgroundRefreshTarget(
        ExternalMediaRef(MediaSource.TMDB, "42"),
        MediaType.MOVIE
    )

    @Test
    fun emptyPlanSkipsAndMissingOrRejectedCredentialStillReachProviderAwareRefresh() = runTest {
        val emptyCoordinator = FakeCoordinator(summary(CalendarRefreshOutcome.COMPLETE_SUCCESS))
        assertEquals(
            WorkerRunDecision.SUCCESS,
            executor(FakePlanner(AppResult.Success(emptyList())), FakeCredential(), emptyCoordinator).execute(0)
        )
        assertEquals(0, emptyCoordinator.calls)

        listOf(TmdbCredentialStatus.NotConfigured, TmdbCredentialStatus.Rejected(false)).forEach { status ->
            val coordinator = FakeCoordinator(summary(CalendarRefreshOutcome.COMPLETE_SUCCESS))
            val credential = FakeCredential(
                initial = status,
                statusAfterRefresh = if (status is TmdbCredentialStatus.Rejected) TmdbCredentialStatus.Valid else null
            )
            assertEquals(
                WorkerRunDecision.SUCCESS,
                executor(
                    FakePlanner(AppResult.Success(listOf(target))),
                    credential,
                    coordinator
                ).execute(0)
            )
            assertEquals(1, coordinator.calls)
            assertEquals(1, credential.refreshCalls)
        }
    }

    @Test
    fun completeAndPartialSuccessEnqueueOneLocalEvaluation() = runTest {
        listOf(CalendarRefreshOutcome.COMPLETE_SUCCESS, CalendarRefreshOutcome.PARTIAL_SUCCESS).forEach { outcome ->
            val scheduler = FakeScheduler()
            val coordinator = FakeCoordinator(summary(outcome))
            assertEquals(
                WorkerRunDecision.SUCCESS,
                executor(
                    FakePlanner(AppResult.Success(listOf(target))),
                    FakeCredential(),
                    coordinator,
                    scheduler
                ).execute(0)
            )
            assertEquals(1, scheduler.immediateCalls)
            assertEquals(listOf(target), coordinator.targets)
        }
    }

    @Test
    fun workerKeepsGlobalRefreshBatchAtTwenty() = runTest {
        val planner = FakePlanner(AppResult.Success(listOf(target)))

        assertEquals(
            WorkerRunDecision.SUCCESS,
            executor(
                planner,
                FakeCredential(),
                FakeCoordinator(summary(CalendarRefreshOutcome.COMPLETE_SUCCESS))
            ).execute(0)
        )

        assertEquals(CalendarRefreshWorker.BATCH_SIZE, planner.requestedLimit)
    }

    @Test
    fun completeTransientFailuresRetryUntilCapAndDoNotEnqueueNotifications() = runTest {
        listOf(AppError.NetworkUnavailable, AppError.RateLimited, AppError.RemoteServiceFailure).forEach { error ->
            val scheduler = FakeScheduler()
            val executor = executor(
                FakePlanner(AppResult.Success(listOf(target))),
                FakeCredential(),
                FakeCoordinator(summary(CalendarRefreshOutcome.COMPLETE_FAILURE, error)),
                scheduler
            )
            assertEquals(WorkerRunDecision.RETRY, executor.execute(0))
            assertEquals(WorkerRunDecision.SUCCESS, executor.execute(2))
            assertEquals(0, scheduler.immediateCalls)
        }
    }

    @Test
    fun invalidPlannerConfigurationFailsAndCancellationPropagates() = runTest {
        assertEquals(
            WorkerRunDecision.FAILURE,
            executor(
                FakePlanner(AppResult.Failure(AppError.InvalidInput)),
                FakeCredential(),
                FakeCoordinator(summary(CalendarRefreshOutcome.NO_WORK))
            ).execute(0)
        )
        var cancelled = false
        try {
            executor(
                FakePlanner(AppResult.Success(listOf(target))),
                FakeCredential(cancelOnRefresh = true),
                FakeCoordinator(summary(CalendarRefreshOutcome.NO_WORK))
            ).execute(0)
        } catch (_: CancellationException) {
            cancelled = true
        }
        assertTrue(cancelled)
    }

    private fun executor(
        planner: BackgroundRefreshPlanner,
        credential: TmdbCredentialRepository,
        coordinator: FakeCoordinator,
        scheduler: FakeScheduler = FakeScheduler()
    ) = CalendarRefreshWorkerExecutor(planner, credential, coordinator, scheduler)

    private fun summary(outcome: CalendarRefreshOutcome, error: AppError? = null) = CalendarRefreshSummary(
        outcome,
        titlesConsidered = 1,
        operationsSucceeded = if (outcome == CalendarRefreshOutcome.COMPLETE_SUCCESS) 1 else 0,
        operationsFailed = if (outcome == CalendarRefreshOutcome.COMPLETE_FAILURE) 1 else 0,
        operationsSkipped = 0,
        representativeError = error
    )

    private class FakePlanner(private val result: AppResult<List<BackgroundRefreshTarget>>) :
        BackgroundRefreshPlanner {
        var requestedLimit: Int? = null

        override suspend fun plan(limit: Int): AppResult<List<BackgroundRefreshTarget>> {
            requestedLimit = limit
            return result
        }
    }

    private class FakeCredential(
        initial: TmdbCredentialStatus = TmdbCredentialStatus.Valid,
        private val cancelOnRefresh: Boolean = false,
        private val statusAfterRefresh: TmdbCredentialStatus? = null
    ) : TmdbCredentialRepository {
        private val mutableStatus = MutableStateFlow(initial)
        override val status: StateFlow<TmdbCredentialStatus> = mutableStatus
        var refreshCalls = 0

        override suspend fun refreshLocalStatus() {
            refreshCalls++
            if (cancelOnRefresh) throw CancellationException()
            statusAfterRefresh?.let { mutableStatus.value = it }
        }
        override suspend fun validateAndSave(input: String): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun revalidateStored(): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun remove(): AppResult<Unit> = AppResult.Success(Unit)
        override fun cancelValidation() = Unit
    }

    private class FakeCoordinator(private val value: CalendarRefreshSummary) : CalendarRefreshCoordinator {
        var calls = 0
        var targets: List<BackgroundRefreshTarget> = emptyList()
        override suspend fun refresh(): CalendarRefreshSummary = value
        override suspend fun refresh(targets: List<BackgroundRefreshTarget>): CalendarRefreshSummary {
            calls++
            this.targets = targets
            return value
        }
    }

    private class FakeScheduler : BackgroundWorkScheduler {
        var immediateCalls = 0
        override fun ensureCalendarRefresh() = Unit
        override fun reconcileNotificationWork(enabled: Boolean) = Unit
        override fun enqueueImmediateNotificationEvaluation() {
            immediateCalls++
        }
    }
}
