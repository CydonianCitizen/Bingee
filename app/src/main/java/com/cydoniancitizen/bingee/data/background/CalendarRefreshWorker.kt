package com.cydoniancitizen.bingee.data.background

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.cydoniancitizen.bingee.core.model.CalendarRefreshOutcome
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.result.AppError
import com.cydoniancitizen.bingee.core.result.AppResult
import com.cydoniancitizen.bingee.domain.background.BackgroundWorkScheduler
import com.cydoniancitizen.bingee.domain.repository.BackgroundRefreshPlanner
import com.cydoniancitizen.bingee.domain.repository.CalendarRefreshCoordinator
import com.cydoniancitizen.bingee.domain.repository.TmdbCredentialRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import javax.inject.Inject
import kotlinx.coroutines.CancellationException

@HiltWorker
internal class CalendarRefreshWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParameters: WorkerParameters,
    private val executor: CalendarRefreshWorkerExecutor
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result = executor.execute(runAttemptCount).toResult()

    internal companion object {
        const val BATCH_SIZE = 20
        const val MAX_ATTEMPTS = 3
    }
}

internal class CalendarRefreshWorkerExecutor @Inject constructor(
    private val planner: BackgroundRefreshPlanner,
    private val credentialRepository: TmdbCredentialRepository,
    private val refreshCoordinator: CalendarRefreshCoordinator,
    private val scheduler: BackgroundWorkScheduler
) {
    suspend fun execute(runAttemptCount: Int): WorkerRunDecision {
        val plan = when (val result = planner.plan(BATCH_SIZE)) {
            is AppResult.Success -> result.value
            is AppResult.Failure -> return workerRunDecision(result.error, runAttemptCount)
        }
        if (plan.isEmpty()) return WorkerRunDecision.SUCCESS

        if (plan.any { it.mediaRef.source == MediaSource.TMDB }) {
            try {
                credentialRepository.refreshLocalStatus()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
            }
        }

        val summary = refreshCoordinator.refresh(plan)
        return when (summary.outcome) {
            CalendarRefreshOutcome.COMPLETE_SUCCESS,
            CalendarRefreshOutcome.PARTIAL_SUCCESS -> {
                scheduler.enqueueImmediateNotificationEvaluation()
                WorkerRunDecision.SUCCESS
            }
            CalendarRefreshOutcome.NO_WORK,
            CalendarRefreshOutcome.CREDENTIAL_REQUIRED -> WorkerRunDecision.SUCCESS
            CalendarRefreshOutcome.COMPLETE_FAILURE ->
                workerRunDecision(summary.representativeError ?: AppError.Unknown, runAttemptCount)
        }
    }

    internal companion object {
        const val BATCH_SIZE = CalendarRefreshWorker.BATCH_SIZE
    }
}

internal enum class WorkerRunDecision { SUCCESS, RETRY, FAILURE }

internal fun workerRunDecision(error: AppError, runAttemptCount: Int): WorkerRunDecision = when {
    error == AppError.InvalidInput || error == AppError.UnsupportedData -> WorkerRunDecision.FAILURE
    error.isTransientWorkerError() && runAttemptCount + 1 < CalendarRefreshWorker.MAX_ATTEMPTS ->
        WorkerRunDecision.RETRY
    else -> WorkerRunDecision.SUCCESS
}

internal fun WorkerRunDecision.toResult(): ListenableWorker.Result = when (this) {
    WorkerRunDecision.SUCCESS -> ListenableWorker.Result.success()
    WorkerRunDecision.RETRY -> ListenableWorker.Result.retry()
    WorkerRunDecision.FAILURE -> ListenableWorker.Result.failure()
}

private fun AppError.isTransientWorkerError(): Boolean = when (this) {
    AppError.NetworkUnavailable,
    AppError.RateLimited,
    AppError.RemoteServiceFailure,
    AppError.LocalStorageFailure -> true
    else -> false
}
