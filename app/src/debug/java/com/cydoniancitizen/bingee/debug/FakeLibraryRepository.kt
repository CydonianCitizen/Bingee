package com.cydoniancitizen.bingee.debug

import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.LibraryEntry
import com.cydoniancitizen.bingee.core.model.LibraryQuery
import com.cydoniancitizen.bingee.core.model.MediaSearchResult
import com.cydoniancitizen.bingee.core.model.organizeLibraryEntries
import com.cydoniancitizen.bingee.core.result.AppError
import com.cydoniancitizen.bingee.core.result.AppResult
import com.cydoniancitizen.bingee.domain.repository.LibraryRepository
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeLibraryRepository(
    initialEntries: List<LibraryEntry> = emptyList(),
    var writeFailure: AppError? = null,
    private val now: Instant = Instant.parse("2026-01-02T03:04:05Z")
) : LibraryRepository {
    private val entries = MutableStateFlow(initialEntries)

    override fun observeEntries(query: LibraryQuery): Flow<AppResult<List<LibraryEntry>>> = entries.map { current ->
        AppResult.Success(organizeLibraryEntries(current, query))
    }

    override fun observeEntryCount(): Flow<AppResult<Int>> = entries.map { AppResult.Success(it.size) }

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

    override suspend fun add(ref: ExternalMediaRef): AppResult<LibraryEntry> {
        writeFailure?.let { return AppResult.Failure(it) }
        val existing = entries.value.firstOrNull { it.mediaRef == ref }
            ?: return AppResult.Failure(AppError.MissingData)
        entries.value = entries.value.filterNot { it.mediaRef == ref } + existing
        return AppResult.Success(existing)
    }

    override suspend fun remove(ref: ExternalMediaRef): AppResult<Unit> {
        writeFailure?.let { return AppResult.Failure(it) }
        entries.value = entries.value.filterNot { it.mediaRef == ref }
        return AppResult.Success(Unit)
    }

    override suspend fun isInLibrary(ref: ExternalMediaRef): AppResult<Boolean> =
        AppResult.Success(entries.value.any { it.mediaRef == ref })

    override suspend fun setFavorite(ref: ExternalMediaRef, isFavorite: Boolean): AppResult<Unit> {
        writeFailure?.let { return AppResult.Failure(it) }
        entries.value = entries.value.map { entry ->
            if (entry.mediaRef == ref) entry.copy(isFavorite = isFavorite) else entry
        }
        return AppResult.Success(Unit)
    }

    override suspend fun setFavorite(result: MediaSearchResult, isFavorite: Boolean): AppResult<Unit> {
        writeFailure?.let { return AppResult.Failure(it) }
        val existing = entries.value.firstOrNull { it.mediaRef == result.externalRef }
        val updated = if (existing != null) {
            existing.copy(isFavorite = isFavorite)
        } else {
            LibraryEntry(
                mediaRef = result.externalRef,
                mediaType = result.mediaType,
                title = result.title,
                originalTitle = result.originalTitle,
                posterUrl = result.posterUrl,
                releaseDate = result.releaseDate,
                overview = result.overview,
                addedAt = now,
                isFavorite = isFavorite
            )
        }
        entries.value = entries.value.filterNot { it.mediaRef == result.externalRef } + updated
        return AppResult.Success(Unit)
    }

    override suspend fun setWatchedDate(ref: ExternalMediaRef, watchedDate: LocalDate?): AppResult<Unit> {
        writeFailure?.let { return AppResult.Failure(it) }
        entries.value = entries.value.map { entry ->
            if (entry.mediaRef == ref) entry.copy(watchedDate = watchedDate) else entry
        }
        return AppResult.Success(Unit)
    }
}
