package com.cydoniancitizen.bingee.data.credential

import com.cydoniancitizen.bingee.core.credential.TmdbCredential
import com.cydoniancitizen.bingee.core.credential.TmdbCredentialStatus
import com.cydoniancitizen.bingee.core.credential.TmdbCredentialValidation
import com.cydoniancitizen.bingee.core.credential.TmdbCredentialValidator
import com.cydoniancitizen.bingee.core.result.AppError
import com.cydoniancitizen.bingee.core.result.AppResult
import com.cydoniancitizen.bingee.data.tmdb.auth.TmdbCredentialRemoteValidator
import com.cydoniancitizen.bingee.domain.repository.TmdbCredentialRepository
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
internal class DefaultTmdbCredentialRepository @Inject constructor(
    private val store: TmdbCredentialStore,
    private val validator: TmdbCredentialValidator,
    private val remoteValidator: TmdbCredentialRemoteValidator
) : TmdbCredentialRepository {
    private val generation = AtomicLong()
    private val commitMutex = Mutex()
    private val mutableStatus = MutableStateFlow<TmdbCredentialStatus>(TmdbCredentialStatus.Checking)

    @Volatile
    private var fallbackStatus: TmdbCredentialStatus = TmdbCredentialStatus.NotConfigured

    @Volatile
    private var hasStoredCredential = false

    override val status: StateFlow<TmdbCredentialStatus> = mutableStatus.asStateFlow()

    override suspend fun refreshLocalStatus() {
        generation.incrementAndGet()
        when (val stored = store.read()) {
            is AppResult.Success -> {
                hasStoredCredential = stored.value != null
                setStableStatus(
                    if (stored.value == null) {
                        TmdbCredentialStatus.NotConfigured
                    } else {
                        TmdbCredentialStatus.Valid
                    }
                )
            }

            is AppResult.Failure -> setStableStatus(TmdbCredentialStatus.StorageUnreadable)
        }
    }

    override suspend fun validateAndSave(input: String): AppResult<Unit> =
        when (val local = validator.validate(input)) {
            is TmdbCredentialValidation.Invalid -> AppResult.Failure(AppError.InvalidInput)
            is TmdbCredentialValidation.Valid -> validateAndSave(local.credential)
        }

    override suspend fun revalidateStored(): AppResult<Unit> = when (val stored = store.read()) {
        is AppResult.Failure -> {
            setStableStatus(TmdbCredentialStatus.StorageUnreadable)
            stored
        }

        is AppResult.Success -> {
            val credential = stored.value
            if (credential == null) {
                hasStoredCredential = false
                setStableStatus(TmdbCredentialStatus.NotConfigured)
                AppResult.Failure(AppError.MissingData)
            } else if (validator.validate(credential.reveal()) is TmdbCredentialValidation.Invalid) {
                setStableStatus(TmdbCredentialStatus.StorageUnreadable)
                AppResult.Failure(AppError.CorruptedData)
            } else {
                validateAndSave(credential)
            }
        }
    }

    override suspend fun remove(): AppResult<Unit> {
        generation.incrementAndGet()
        return when (val result = store.delete()) {
            is AppResult.Success -> {
                hasStoredCredential = false
                setStableStatus(TmdbCredentialStatus.NotConfigured)
                result
            }

            is AppResult.Failure -> {
                setStableStatus(TmdbCredentialStatus.StorageUnreadable)
                result
            }
        }
    }

    override fun cancelValidation() {
        generation.incrementAndGet()
        if (mutableStatus.value is TmdbCredentialStatus.Validating) {
            mutableStatus.value = fallbackStatus
        }
    }

    private suspend fun validateAndSave(credential: TmdbCredential): AppResult<Unit> {
        val requestGeneration = generation.incrementAndGet()
        val previousCredential =
            if (hasStoredCredential) {
                when (val stored = store.read()) {
                    is AppResult.Success -> stored.value
                    is AppResult.Failure -> {
                        setStableStatus(TmdbCredentialStatus.StorageUnreadable)
                        return stored
                    }
                }
            } else {
                null
            }
        if (mutableStatus.value !is TmdbCredentialStatus.Validating) {
            fallbackStatus = mutableStatus.value
        }
        mutableStatus.value = TmdbCredentialStatus.Validating(hasStoredCredential)

        val remoteResult = remoteValidator.validate(credential)
        if (generation.get() != requestGeneration) {
            return remoteResult
        }

        return when (remoteResult) {
            is AppResult.Success ->
                saveCurrent(requestGeneration, credential, previousCredential)
            is AppResult.Failure -> {
                val nextStatus =
                    if (remoteResult.error == AppError.Unauthorized) {
                        TmdbCredentialStatus.Rejected(hasStoredCredential)
                    } else {
                        TmdbCredentialStatus.TemporarilyUnverifiable(
                            error = remoteResult.error,
                            hasStoredCredential = hasStoredCredential
                        )
                    }
                setStableStatus(nextStatus)
                remoteResult
            }
        }
    }

    private suspend fun saveCurrent(
        requestGeneration: Long,
        credential: TmdbCredential,
        previousCredential: TmdbCredential?
    ): AppResult<Unit> = commitMutex.withLock {
        if (generation.get() != requestGeneration) {
            return@withLock AppResult.Success(Unit)
        }
        val saved = store.save(credential)
        if (generation.get() != requestGeneration) {
            return@withLock rollback(previousCredential)
        }
        when (saved) {
            is AppResult.Success -> {
                hasStoredCredential = true
                setStableStatus(TmdbCredentialStatus.Valid)
                saved
            }

            is AppResult.Failure -> {
                setStableStatus(TmdbCredentialStatus.StorageUnreadable)
                saved
            }
        }
    }

    private suspend fun rollback(previousCredential: TmdbCredential?): AppResult<Unit> =
        if (previousCredential == null) store.delete() else store.save(previousCredential)

    private fun setStableStatus(status: TmdbCredentialStatus) {
        fallbackStatus = status
        mutableStatus.value = status
    }
}
