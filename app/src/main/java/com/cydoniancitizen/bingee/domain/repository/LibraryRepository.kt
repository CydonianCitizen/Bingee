package com.cydoniancitizen.bingee.domain.repository

import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.LibraryEntry
import com.cydoniancitizen.bingee.core.result.AppResult
import kotlinx.coroutines.flow.Flow

interface LibraryRepository {
    fun observeEntries(): Flow<List<LibraryEntry>>

    fun observeEntry(ref: ExternalMediaRef): Flow<LibraryEntry?>

    suspend fun add(entry: LibraryEntry): AppResult<Unit>

    suspend fun remove(ref: ExternalMediaRef): AppResult<Unit>
}
