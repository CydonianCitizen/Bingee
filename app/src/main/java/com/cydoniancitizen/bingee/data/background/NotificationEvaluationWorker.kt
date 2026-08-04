package com.cydoniancitizen.bingee.data.background

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.cydoniancitizen.bingee.domain.notification.NotificationDispatchCoordinator
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import javax.inject.Inject

@HiltWorker
internal class NotificationEvaluationWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParameters: WorkerParameters,
    private val executor: NotificationEvaluationWorkerExecutor
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result = executor.execute(runAttemptCount).toResult()

    internal companion object {
        const val MAX_ATTEMPTS = 3
    }
}

internal class NotificationEvaluationWorkerExecutor @Inject constructor(
    private val coordinator: NotificationDispatchCoordinator
) {
    suspend fun execute(runAttemptCount: Int): WorkerRunDecision {
        val summary = coordinator.dispatch()
        return if (shouldRetryNotificationEvaluation(summary.transientFailure, runAttemptCount)) {
            WorkerRunDecision.RETRY
        } else {
            WorkerRunDecision.SUCCESS
        }
    }
}

internal fun shouldRetryNotificationEvaluation(transientFailure: Boolean, runAttemptCount: Int): Boolean =
    transientFailure && runAttemptCount + 1 < NotificationEvaluationWorker.MAX_ATTEMPTS
