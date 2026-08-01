package com.cydoniancitizen.bingee.domain.repository

import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.LibraryEntry
import com.cydoniancitizen.bingee.core.model.MediaSearchResult
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.core.result.AppResult
import kotlinx.coroutines.flow.Flow

interface LibraryRepository {
    fun observeEntries(mediaType: MediaType? = null): Flow<AppResult<List<LibraryEntry>>>

    fun observeEntry(ref: ExternalMediaRef): Flow<AppResult<LibraryEntry?>>

    fun observeMembershipRefs(): Flow<AppResult<Set<ExternalMediaRef>>>

    suspend fun add(result: MediaSearchResult): AppResult<LibraryEntry>

    suspend fun remove(ref: ExternalMediaRef): AppResult<Unit>

    suspend fun isInLibrary(ref: ExternalMediaRef): AppResult<Boolean>
}
