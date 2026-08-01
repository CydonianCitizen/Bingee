package com.cydoniancitizen.bingee.debug

import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.LibraryEntry
import com.cydoniancitizen.bingee.core.model.MediaSearchResult
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.core.result.AppError
import com.cydoniancitizen.bingee.core.result.AppResult
import com.cydoniancitizen.bingee.domain.repository.LibraryRepository
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeLibraryRepository(
    initialEntries: List<LibraryEntry> = emptyList(),
    var writeFailure: AppError? = null,
    private val now: Instant = Instant.parse("2026-01-02T03:04:05Z")
) : LibraryRepository {
    private val entries = MutableStateFlow(initialEntries)

    override fun observeEntries(mediaType: MediaType?): Flow<AppResult<List<LibraryEntry>>> = entries.map { current ->
        AppResult.Success(current.filter { mediaType == null || it.mediaType == mediaType })
    }

    override fun observeEntry(ref: ExternalMediaRef): Flow<AppResult<LibraryEntry?>> =
        entries.map { current -> AppResult.Success(current.firstOrNull { it.mediaRef == ref }) }

    override fun observeMembershipRefs(): Flow<AppResult<Set<ExternalMediaRef>>> =
        entries.map { current -> AppResult.Success(current.mapTo(linkedSetOf()) { it.mediaRef }) }

    override suspend fun add(result: MediaSearchResult): AppResult<LibraryEntry> {
        writeFailure?.let { return AppResult.Failure(it) }
        val existing = entries.value.firstOrNull { it.mediaRef == result.externalRef }
        val entry =
            LibraryEntry(
                mediaRef = result.externalRef,
                mediaType = result.mediaType,
                title = result.title,
                originalTitle = result.originalTitle,
                posterUrl = result.posterUrl,
                releaseDate = result.releaseDate,
                overview = result.overview,
                addedAt = existing?.addedAt ?: now
            )
        entries.value = entries.value.filterNot { it.mediaRef == entry.mediaRef } + entry
        return AppResult.Success(entry)
    }

    override suspend fun remove(ref: ExternalMediaRef): AppResult<Unit> {
        writeFailure?.let { return AppResult.Failure(it) }
        entries.value = entries.value.filterNot { it.mediaRef == ref }
        return AppResult.Success(Unit)
    }

    override suspend fun isInLibrary(ref: ExternalMediaRef): AppResult<Boolean> =
        AppResult.Success(entries.value.any { it.mediaRef == ref })
}
