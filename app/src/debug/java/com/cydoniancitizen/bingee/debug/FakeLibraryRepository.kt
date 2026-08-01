package com.cydoniancitizen.bingee.debug

import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.LibraryEntry
import com.cydoniancitizen.bingee.core.result.AppError
import com.cydoniancitizen.bingee.core.result.AppResult
import com.cydoniancitizen.bingee.domain.repository.LibraryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeLibraryRepository(initialEntries: List<LibraryEntry> = emptyList(), var writeFailure: AppError? = null) :
    LibraryRepository {
    private val entries = MutableStateFlow(initialEntries)

    override fun observeEntries(): Flow<List<LibraryEntry>> = entries

    override fun observeEntry(ref: ExternalMediaRef): Flow<LibraryEntry?> =
        entries.map { current -> current.firstOrNull { it.mediaRef == ref } }

    override suspend fun add(entry: LibraryEntry): AppResult<Unit> {
        writeFailure?.let { return AppResult.Failure(it) }
        entries.value = entries.value.filterNot { it.mediaRef == entry.mediaRef } + entry
        return AppResult.Success(Unit)
    }

    override suspend fun remove(ref: ExternalMediaRef): AppResult<Unit> {
        writeFailure?.let { return AppResult.Failure(it) }
        entries.value = entries.value.filterNot { it.mediaRef == ref }
        return AppResult.Success(Unit)
    }
}
