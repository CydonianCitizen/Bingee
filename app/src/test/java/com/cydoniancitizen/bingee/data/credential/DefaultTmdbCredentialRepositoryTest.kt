package com.cydoniancitizen.bingee.data.credential

import com.cydoniancitizen.bingee.core.credential.TmdbCredential
import com.cydoniancitizen.bingee.core.credential.TmdbCredentialStatus
import com.cydoniancitizen.bingee.core.credential.TmdbCredentialValidator
import com.cydoniancitizen.bingee.core.result.AppError
import com.cydoniancitizen.bingee.core.result.AppResult
import com.cydoniancitizen.bingee.data.tmdb.auth.TmdbCredentialRemoteValidator
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultTmdbCredentialRepositoryTest {
    @Test
    fun callerCancellationRestoresStableStatusAndAllowsAnotherValidation() = runTest {
        listOf(false, true).forEach { hasExisting ->
            val store = FakeStore(if (hasExisting) TmdbCredential("fake_existing") else null)
            val first = CompletableDeferred<AppResult<Unit>>()
            val second = CompletableDeferred<AppResult<Unit>>()
            val repository = repository(store, DeferredRemoteValidator(first, second))
            repository.refreshLocalStatus()
            val stable = repository.status.value
            val request = async {
                if (hasExisting) repository.revalidateStored() else repository.validateAndSave("fake_cancelled")
            }
            runCurrent()
            assertEquals(TmdbCredentialStatus.Validating(hasExisting), repository.status.value)
            request.cancelAndJoin()
            assertTrue(request.isCancelled)
            assertEquals(stable, repository.status.value)
            assertEquals(hasExisting, store.hasCredential())
            assertEquals(0, store.saveCount)

            second.complete(AppResult.Success(Unit))
            assertEquals(AppResult.Success(Unit), repository.validateAndSave("fake_next"))
            assertEquals(TmdbCredentialStatus.Valid, repository.status.value)
            assertTrue(store.matches("fake_next"))
        }
    }

    @Test
    fun cancellingSupersededCallerDoesNotResetNewerValidation() = runTest {
        val first = CompletableDeferred<AppResult<Unit>>()
        val second = CompletableDeferred<AppResult<Unit>>()
        val store = FakeStore()
        val repository = repository(store, DeferredRemoteValidator(first, second))
        repository.refreshLocalStatus()
        val old = async { repository.validateAndSave("fake_old") }
        runCurrent()
        val newer = async { repository.validateAndSave("fake_new") }
        runCurrent()
        old.cancelAndJoin()
        assertEquals(TmdbCredentialStatus.Validating(false), repository.status.value)
        second.complete(AppResult.Success(Unit))
        newer.await()
        assertEquals(TmdbCredentialStatus.Valid, repository.status.value)
        assertTrue(store.matches("fake_new"))
    }

    @Test
    fun cancellationAfterStorageWriteRollsBackBeforeRestoringStatus() = runTest {
        listOf(false, true).forEach { hasExisting ->
            val store = FakeStore(
                if (hasExisting) TmdbCredential("fake_existing") else null,
                saveGate = CompletableDeferred(),
                writeBeforeSaveGate = true
            )
            val repository = repository(store)
            repository.refreshLocalStatus()
            val stable = repository.status.value
            val request = async { repository.validateAndSave("fake_cancelled") }
            runCurrent()
            assertTrue(store.matches("fake_cancelled"))
            request.cancelAndJoin()
            assertEquals(stable, repository.status.value)
            assertEquals(hasExisting, store.hasCredential())
            if (hasExisting) assertTrue(store.matches("fake_existing"))
            repository.refreshLocalStatus()
            assertEquals(stable, repository.status.value)
        }
    }

    @Test
    fun cancellationAfterRemovalCannotRestoreOldStatusOrCredential() = runTest {
        val store = FakeStore(TmdbCredential("fake_existing"))
        val repository = repository(
            store,
            DeferredRemoteValidator(CompletableDeferred(), CompletableDeferred())
        )
        repository.refreshLocalStatus()
        val request = async { repository.validateAndSave("fake_cancelled") }
        runCurrent()
        repository.remove()
        request.cancelAndJoin()
        assertEquals(TmdbCredentialStatus.NotConfigured, repository.status.value)
        assertFalse(store.hasCredential())
    }

    @Test
    fun missingAndCorruptedStorageProduceSafeStates() = runTest {
        val missing = repository(FakeStore())
        missing.refreshLocalStatus()
        assertEquals(TmdbCredentialStatus.NotConfigured, missing.status.value)

        val corrupted = repository(FakeStore(readFailure = true))
        corrupted.refreshLocalStatus()
        assertEquals(TmdbCredentialStatus.StorageUnreadable, corrupted.status.value)
    }

    @Test
    fun localRejectionDoesNotCallNetwork() = runTest {
        val remote = QueueRemoteValidator()
        val repository = repository(FakeStore(), remote)

        val result = repository.validateAndSave("invalid token")

        assertEquals(AppResult.Failure(AppError.InvalidInput), result)
        assertEquals(0, remote.calls)
    }

    @Test
    fun successSavesCredentialAndReplacement() = runTest {
        val store = FakeStore()
        val remote = QueueRemoteValidator(AppResult.Success(Unit), AppResult.Success(Unit))
        val repository = repository(store, remote)

        repository.validateAndSave("fake_first")
        repository.validateAndSave("fake_second")

        assertEquals(TmdbCredentialStatus.Valid, repository.status.value)
        assertEquals(2, store.saveCount)
        assertTrue(store.matches("fake_second"))
    }

    @Test
    fun rejectionIsDistinctAndDoesNotSaveCandidate() = runTest {
        val store = FakeStore()
        val repository =
            repository(store, QueueRemoteValidator(AppResult.Failure(AppError.Unauthorized)))

        val result = repository.validateAndSave("fake_rejected")

        assertEquals(AppResult.Failure(AppError.Unauthorized), result)
        assertEquals(TmdbCredentialStatus.Rejected(false), repository.status.value)
        assertEquals(0, store.saveCount)
    }

    @Test
    fun temporaryFailurePreservesExistingCredential() = runTest {
        val store = FakeStore(TmdbCredential("fake_existing"))
        val repository =
            repository(store, QueueRemoteValidator(AppResult.Failure(AppError.NetworkUnavailable)))
        repository.refreshLocalStatus()

        repository.validateAndSave("fake_replacement")

        assertEquals(
            TmdbCredentialStatus.TemporarilyUnverifiable(
                AppError.NetworkUnavailable,
                hasStoredCredential = true
            ),
            repository.status.value
        )
        assertTrue(store.matches("fake_existing"))
        assertEquals(0, store.saveCount)
    }

    @Test
    fun removalCancelsValidationAndDeletesProtectedValue() = runTest {
        val store = FakeStore(TmdbCredential("fake_existing"))
        val repository = repository(store)

        val result = repository.remove()

        assertEquals(AppResult.Success(Unit), result)
        assertEquals(TmdbCredentialStatus.NotConfigured, repository.status.value)
        assertFalse(store.hasCredential())
        assertEquals(1, store.deleteCount)
    }

    @Test
    fun staleConcurrentValidationCannotOverwriteNewerCredential() = runTest {
        val first = CompletableDeferred<AppResult<Unit>>()
        val second = CompletableDeferred<AppResult<Unit>>()
        val store = FakeStore()
        val remote = DeferredRemoteValidator(first, second)
        val repository = repository(store, remote)

        val oldRequest = async { repository.validateAndSave("fake_old") }
        runCurrent()
        val newRequest = async { repository.validateAndSave("fake_new") }
        runCurrent()
        second.complete(AppResult.Success(Unit))
        runCurrent()
        first.complete(AppResult.Success(Unit))
        oldRequest.await()
        newRequest.await()

        assertEquals(1, store.saveCount)
        assertTrue(store.matches("fake_new"))
        assertEquals(TmdbCredentialStatus.Valid, repository.status.value)
    }

    @Test
    fun inputChangeDuringCommitRollsBackStaleCredential() = runTest {
        val saveGate = CompletableDeferred<Unit>()
        val store = FakeStore(saveGate = saveGate)
        val repository = repository(store, QueueRemoteValidator(AppResult.Success(Unit)))
        repository.refreshLocalStatus()

        val request = async { repository.validateAndSave("fake_stale") }
        runCurrent()
        repository.cancelValidation()
        saveGate.complete(Unit)
        request.await()

        assertFalse(store.hasCredential())
        assertEquals(TmdbCredentialStatus.NotConfigured, repository.status.value)
    }

    @Test
    fun removalDuringReplacementCommitIsNotUndoneByTheRollback() = runTest {
        val saveGate = CompletableDeferred<Unit>()
        val store = FakeStore(TmdbCredential("fake_existing"), saveGate = saveGate)
        val repository = repository(store, QueueRemoteValidator(AppResult.Success(Unit)))
        repository.refreshLocalStatus()

        val replacement = async { repository.validateAndSave("fake_replacement") }
        runCurrent()
        val removal = async { repository.remove() }
        runCurrent()
        saveGate.complete(Unit)
        replacement.await()

        assertEquals(AppResult.Success(Unit), removal.await())
        assertFalse(store.hasCredential())
        assertEquals(TmdbCredentialStatus.NotConfigured, repository.status.value)
        repository.refreshLocalStatus()
        assertFalse(store.hasCredential())
        assertEquals(TmdbCredentialStatus.NotConfigured, repository.status.value)
    }

    @Test
    fun supersededCommitRollsBackBeforeANewerValidationReadsTheStore() = runTest {
        val saveGate = CompletableDeferred<Unit>()
        val store = FakeStore(TmdbCredential("fake_existing"), saveGate = saveGate)
        val remote = QueueRemoteValidator(AppResult.Success(Unit), AppResult.Failure(AppError.NetworkUnavailable))
        val repository = repository(store, remote)
        repository.refreshLocalStatus()

        val stale = async { repository.validateAndSave("fake_stale") }
        runCurrent()
        val newer = async { repository.validateAndSave("fake_newer") }
        runCurrent()
        saveGate.complete(Unit)
        stale.await()

        assertEquals(AppResult.Failure(AppError.NetworkUnavailable), newer.await())
        assertTrue(store.matches("fake_existing"))
        assertEquals(
            TmdbCredentialStatus.TemporarilyUnverifiable(AppError.NetworkUnavailable, hasStoredCredential = true),
            repository.status.value
        )
    }

    private fun repository(store: FakeStore, remote: TmdbCredentialRemoteValidator = QueueRemoteValidator()) =
        DefaultTmdbCredentialRepository(store, TmdbCredentialValidator(), remote)

    private class FakeStore(
        private var credential: TmdbCredential? = null,
        private val readFailure: Boolean = false,
        private val saveGate: CompletableDeferred<Unit>? = null,
        private val writeBeforeSaveGate: Boolean = false
    ) : TmdbCredentialStore {
        var saveCount = 0
        var deleteCount = 0

        override suspend fun read(): AppResult<TmdbCredential?> = if (readFailure) {
            AppResult.Failure(AppError.CorruptedData)
        } else {
            AppResult.Success(credential)
        }

        override suspend fun save(credential: TmdbCredential): AppResult<Unit> {
            if (!writeBeforeSaveGate) saveGate?.await()
            saveCount++
            this.credential = credential
            if (writeBeforeSaveGate && saveCount == 1) saveGate?.await()
            return AppResult.Success(Unit)
        }

        override suspend fun delete(): AppResult<Unit> {
            deleteCount++
            credential = null
            return AppResult.Success(Unit)
        }

        fun matches(expected: String): Boolean = credential?.reveal() == expected

        fun hasCredential(): Boolean = credential != null
    }

    private class QueueRemoteValidator(vararg results: AppResult<Unit>) : TmdbCredentialRemoteValidator {
        private val results = ArrayDeque(results.toList())
        var calls = 0

        override suspend fun validate(credential: TmdbCredential): AppResult<Unit> {
            calls++
            return results.removeFirstOrNull() ?: AppResult.Success(Unit)
        }
    }

    private class DeferredRemoteValidator(
        private val first: CompletableDeferred<AppResult<Unit>>,
        private val second: CompletableDeferred<AppResult<Unit>>
    ) : TmdbCredentialRemoteValidator {
        private var call = 0

        override suspend fun validate(credential: TmdbCredential): AppResult<Unit> =
            if (call++ == 0) first.await() else second.await()
    }
}
