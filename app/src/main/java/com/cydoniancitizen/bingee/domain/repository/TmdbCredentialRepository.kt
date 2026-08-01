package com.cydoniancitizen.bingee.domain.repository

import com.cydoniancitizen.bingee.core.credential.TmdbCredentialStatus
import com.cydoniancitizen.bingee.core.result.AppResult
import kotlinx.coroutines.flow.StateFlow

interface TmdbCredentialRepository {
    val status: StateFlow<TmdbCredentialStatus>

    suspend fun refreshLocalStatus()

    suspend fun validateAndSave(input: String): AppResult<Unit>

    suspend fun revalidateStored(): AppResult<Unit>

    suspend fun remove(): AppResult<Unit>

    fun cancelValidation()
}
