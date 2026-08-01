package com.cydoniancitizen.bingee.data.credential

import com.cydoniancitizen.bingee.core.credential.TmdbCredential
import com.cydoniancitizen.bingee.core.result.AppError
import com.cydoniancitizen.bingee.core.result.AppResult
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class EncryptedTmdbCredentialStore @Inject constructor(
    private val cipher: TmdbCredentialCipher,
    private val file: TmdbCredentialFile
) : TmdbCredentialStore {
    override suspend fun read(): AppResult<TmdbCredential?> = withContext(Dispatchers.IO) {
        try {
            val encrypted = file.read() ?: return@withContext AppResult.Success(null)
            AppResult.Success(TmdbCredential(cipher.decrypt(encrypted)))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            AppResult.Failure(AppError.CorruptedData)
        }
    }

    override suspend fun save(credential: TmdbCredential): AppResult<Unit> = withContext(Dispatchers.IO) {
        try {
            file.write(cipher.encrypt(credential.reveal()))
            AppResult.Success(Unit)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            AppResult.Failure(AppError.CorruptedData)
        }
    }

    override suspend fun delete(): AppResult<Unit> = withContext(Dispatchers.IO) {
        try {
            file.delete()
            cipher.deleteKey()
            AppResult.Success(Unit)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            AppResult.Failure(AppError.CorruptedData)
        }
    }
}

internal interface TmdbCredentialCipher {
    fun encrypt(value: String): ByteArray

    fun decrypt(value: ByteArray): String

    fun deleteKey()
}

internal interface TmdbCredentialFile {
    fun read(): ByteArray?

    fun write(value: ByteArray)

    fun delete()
}
