package com.cydoniancitizen.bingee.debug

import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.LibraryEntry
import com.cydoniancitizen.bingee.core.model.LibraryProgress
import com.cydoniancitizen.bingee.core.model.LibraryQuery
import com.cydoniancitizen.bingee.core.model.MediaSearchResult
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.core.model.MovieWatchState
import com.cydoniancitizen.bingee.core.model.PersonalViewingEntry
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

    override fun observeEntry(ref: ExternalMediaRef, mediaType: MediaType): Flow<AppResult<LibraryEntry?>> =
        entries.map { current -> AppResult.Success(current.firstOrNull { it.isFor(ref, mediaType) }) }

    override fun observeMembershipRefs(): Flow<AppResult<Set<Pair<ExternalMediaRef, MediaType>>>> =
        entries.map { current -> AppResult.Success(current.mapTo(linkedSetOf()) { it.mediaRef to it.mediaType }) }

    override fun observePersonalViewing(): Flow<AppResult<List<PersonalViewingEntry>>> = entries.map { current ->
        AppResult.Success(current.mapNotNull(::toPersonalViewingEntry))
    }

    override suspend fun add(result: MediaSearchResult): AppResult<LibraryEntry> {
        writeFailure?.let { return AppResult.Failure(it) }
        val existing = entries.value.firstOrNull { it.isFor(result.externalRef, result.mediaType) }
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
        entries.value = entries.value.filterNot { it.isFor(entry.mediaRef, entry.mediaType) } + entry
        return AppResult.Success(entry)
    }

    override suspend fun add(ref: ExternalMediaRef, mediaType: MediaType): AppResult<LibraryEntry> {
        writeFailure?.let { return AppResult.Failure(it) }
        val existing = entries.value.firstOrNull { it.isFor(ref, mediaType) }
            ?: return AppResult.Failure(AppError.MissingData)
        entries.value = entries.value.filterNot { it.isFor(ref, mediaType) } + existing
        return AppResult.Success(existing)
    }

    override suspend fun remove(ref: ExternalMediaRef, mediaType: MediaType): AppResult<Unit> {
        writeFailure?.let { return AppResult.Failure(it) }
        entries.value = entries.value.filterNot { it.isFor(ref, mediaType) }
        return AppResult.Success(Unit)
    }

    override suspend fun isInLibrary(ref: ExternalMediaRef, mediaType: MediaType): AppResult<Boolean> =
        AppResult.Success(entries.value.any { it.isFor(ref, mediaType) })

    override suspend fun setFavorite(
        ref: ExternalMediaRef,
        mediaType: MediaType,
        isFavorite: Boolean
    ): AppResult<Unit> {
        writeFailure?.let { return AppResult.Failure(it) }
        entries.value = entries.value.map { entry ->
            if (entry.isFor(ref, mediaType)) entry.copy(isFavorite = isFavorite) else entry
        }
        return AppResult.Success(Unit)
    }

    override suspend fun setFavorite(result: MediaSearchResult, isFavorite: Boolean): AppResult<Unit> {
        writeFailure?.let { return AppResult.Failure(it) }
        val existing = entries.value.firstOrNull { it.isFor(result.externalRef, result.mediaType) }
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
        entries.value = entries.value.filterNot { it.isFor(result.externalRef, result.mediaType) } + updated
        return AppResult.Success(Unit)
    }

    override suspend fun setWatchedDate(
        ref: ExternalMediaRef,
        mediaType: MediaType,
        watchedDate: LocalDate?
    ): AppResult<Unit> {
        writeFailure?.let { return AppResult.Failure(it) }
        entries.value = entries.value.map { entry ->
            if (entry.isFor(ref, mediaType)) entry.copy(watchedDate = watchedDate) else entry
        }
        return AppResult.Success(Unit)
    }

    private fun toPersonalViewingEntry(entry: LibraryEntry): PersonalViewingEntry? {
        val movieWatchedAt = ((entry.progress as? LibraryProgress.Movie)?.state as? MovieWatchState.Watched)?.watchedAt
        val seriesProgress = (entry.progress as? LibraryProgress.Series)?.progress
        if (movieWatchedAt == null && seriesProgress?.watchedEpisodes == 0) return null
        return PersonalViewingEntry(
            mediaRef = entry.mediaRef,
            mediaType = entry.mediaType,
            title = entry.title,
            originalTitle = entry.originalTitle,
            posterUrl = entry.posterUrl,
            addedAt = entry.addedAt,
            inLibrary = entry.inLibrary,
            isFavorite = entry.isFavorite,
            isAbandoned = entry.isAbandoned,
            personalRating = entry.personalRating,
            movieWatchedAt = movieWatchedAt,
            watchedRegularEpisodes = seriesProgress?.watchedEpisodes ?: 0,
            seriesCompletedAt = now.takeIf {
                entry.mediaType == MediaType.SERIES && seriesProgress?.isComplete == true
            },
            watchedDate = entry.watchedDate
        )
    }
}

private fun LibraryEntry.isFor(ref: ExternalMediaRef, type: MediaType) = mediaRef == ref && mediaType == type
