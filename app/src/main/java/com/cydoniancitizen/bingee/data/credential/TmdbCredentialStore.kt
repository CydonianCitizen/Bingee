package com.cydoniancitizen.bingee.data.credential

import com.cydoniancitizen.bingee.core.credential.TmdbCredential
import com.cydoniancitizen.bingee.core.result.AppResult

internal interface TmdbCredentialStore {
    suspend fun read(): AppResult<TmdbCredential?>

    suspend fun save(credential: TmdbCredential): AppResult<Unit>

    suspend fun delete(): AppResult<Unit>
}
