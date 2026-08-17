package com.cydoniancitizen.bingee.domain.repository

import com.cydoniancitizen.bingee.core.model.ContinueWatchingItem
import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.LibraryEntry
import com.cydoniancitizen.bingee.core.model.LibraryMediaFilter
import com.cydoniancitizen.bingee.core.model.LibraryProgress
import com.cydoniancitizen.bingee.core.model.LibraryQuery
import com.cydoniancitizen.bingee.core.model.MediaSearchResult
import com.cydoniancitizen.bingee.core.model.PersonalViewingEntry
import com.cydoniancitizen.bingee.core.result.AppResult
import com.cydoniancitizen.bingee.domain.policy.ContinueWatchingPolicy
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface LibraryRepository {
    fun observeEntries(query: LibraryQuery = LibraryQuery()): Flow<AppResult<List<LibraryEntry>>>

    fun observeEntryCount(): Flow<AppResult<Int>>

    fun observeEntry(ref: ExternalMediaRef): Flow<AppResult<LibraryEntry?>>

    fun observeMembershipRefs(): Flow<AppResult<Set<ExternalMediaRef>>>

    fun observePersonalViewing(): Flow<AppResult<List<PersonalViewingEntry>>>

    fun observeContinueWatching(): Flow<AppResult<List<ContinueWatchingItem>>> =
        observeEntries(LibraryQuery(mediaFilter = LibraryMediaFilter.TV_SERIES)).map { result ->
            when (result) {
                is AppResult.Success -> AppResult.Success(
                    ContinueWatchingPolicy.select(result.value.mapNotNull(LibraryEntry::toContinueWatchingItem))
                )
                is AppResult.Failure -> result
            }
        }

    suspend fun add(result: MediaSearchResult): AppResult<LibraryEntry>

    suspend fun add(ref: ExternalMediaRef): AppResult<LibraryEntry>

    suspend fun remove(ref: ExternalMediaRef): AppResult<Unit>

    suspend fun isInLibrary(ref: ExternalMediaRef): AppResult<Boolean>

    suspend fun setFavorite(ref: ExternalMediaRef, isFavorite: Boolean): AppResult<Unit>

    suspend fun setFavorite(result: MediaSearchResult, isFavorite: Boolean): AppResult<Unit>

    suspend fun setWatchedDate(ref: ExternalMediaRef, watchedDate: LocalDate?): AppResult<Unit>

    suspend fun setSeriesAbandoned(ref: ExternalMediaRef, isAbandoned: Boolean): AppResult<Unit> =
        AppResult.Failure(com.cydoniancitizen.bingee.core.result.AppError.UnsupportedData)
}

private fun LibraryEntry.toContinueWatchingItem(): ContinueWatchingItem? = (progress as? LibraryProgress.Series)?.let {
    ContinueWatchingItem(
        mediaRef = mediaRef,
        mediaType = mediaType,
        title = title,
        posterUrl = posterUrl,
        progress = it.progress,
        nextEpisode = null,
        updatedAt = null,
        isAbandoned = isAbandoned,
        inLibrary = inLibrary
    )
}
