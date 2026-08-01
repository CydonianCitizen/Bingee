package com.cydoniancitizen.bingee.testutil

import com.cydoniancitizen.bingee.core.credential.TmdbCredentialStatus
import com.cydoniancitizen.bingee.core.result.AppError
import com.cydoniancitizen.bingee.core.result.AppResult
import com.cydoniancitizen.bingee.data.settings.FirstRunPreferences
import com.cydoniancitizen.bingee.domain.repository.TmdbCredentialRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(val dispatcher: TestDispatcher = StandardTestDispatcher()) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}

internal class FakeCredentialRepository(
    initialStatus: TmdbCredentialStatus = TmdbCredentialStatus.NotConfigured,
    var refreshStatus: TmdbCredentialStatus = initialStatus
) : TmdbCredentialRepository {
    private val mutableStatus = MutableStateFlow(initialStatus)
    override val status: StateFlow<TmdbCredentialStatus> = mutableStatus
    val validationResults = ArrayDeque<AppResult<Unit>>()
    var validationGate: CompletableDeferred<Unit>? = null
    var validateCalls = 0
    var revalidateCalls = 0
    var removeCalls = 0
    var cancelCalls = 0

    override suspend fun refreshLocalStatus() {
        mutableStatus.value = refreshStatus
    }

    override suspend fun validateAndSave(input: String): AppResult<Unit> {
        validateCalls++
        val hadStored =
            mutableStatus.value == TmdbCredentialStatus.Valid ||
                (
                    mutableStatus.value is TmdbCredentialStatus.TemporarilyUnverifiable &&
                        (mutableStatus.value as TmdbCredentialStatus.TemporarilyUnverifiable)
                            .hasStoredCredential
                    )
        mutableStatus.value = TmdbCredentialStatus.Validating(hadStored)
        validationGate?.await()
        val result = validationResults.removeFirstOrNull() ?: AppResult.Success(Unit)
        mutableStatus.value = result.toStatus(hadStored)
        return result
    }

    override suspend fun revalidateStored(): AppResult<Unit> {
        revalidateCalls++
        return validateAndSave("fake_internal")
    }

    override suspend fun remove(): AppResult<Unit> {
        removeCalls++
        mutableStatus.value = TmdbCredentialStatus.NotConfigured
        return AppResult.Success(Unit)
    }

    override fun cancelValidation() {
        cancelCalls++
        mutableStatus.value = TmdbCredentialStatus.NotConfigured
    }

    fun emit(status: TmdbCredentialStatus) {
        mutableStatus.value = status
    }

    private fun AppResult<Unit>.toStatus(hadStored: Boolean): TmdbCredentialStatus = when (this) {
        is AppResult.Success -> TmdbCredentialStatus.Valid
        is AppResult.Failure ->
            if (error == AppError.Unauthorized) {
                TmdbCredentialStatus.Rejected(hadStored)
            } else {
                TmdbCredentialStatus.TemporarilyUnverifiable(error, hadStored)
            }
    }
}

internal class FakeFirstRunPreferences(private val complete: Boolean = false) : FirstRunPreferences {
    var markCalls = 0

    override suspend fun isOnboardingComplete(): Boolean = complete

    override suspend fun markOnboardingComplete() {
        markCalls++
    }
}
