package com.cydoniancitizen.bingee.domain.repository

import com.cydoniancitizen.bingee.core.model.ContinueWatchingItem
import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.LibraryEntry
import com.cydoniancitizen.bingee.core.model.LibraryQuery
import com.cydoniancitizen.bingee.core.model.MediaSearchResult
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.core.model.PersonalViewingEntry
import com.cydoniancitizen.bingee.core.result.AppResult
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

interface LibraryRepository {
    fun observeEntries(query: LibraryQuery = LibraryQuery()): Flow<AppResult<List<LibraryEntry>>>

    fun observeEntry(ref: ExternalMediaRef, mediaType: MediaType): Flow<AppResult<LibraryEntry?>>

    /** Library titles as (reference, type): TMDB reuses one ID for a movie and an unrelated series. */
    fun observeMembershipRefs(): Flow<AppResult<Set<Pair<ExternalMediaRef, MediaType>>>>

    fun observePersonalViewing(): Flow<AppResult<List<PersonalViewingEntry>>>

    fun observeContinueWatching(): Flow<AppResult<List<ContinueWatchingItem>>>

    suspend fun add(result: MediaSearchResult): AppResult<LibraryEntry>

    suspend fun add(ref: ExternalMediaRef, mediaType: MediaType): AppResult<LibraryEntry>

    suspend fun remove(ref: ExternalMediaRef, mediaType: MediaType): AppResult<Unit>

    suspend fun setFavorite(ref: ExternalMediaRef, mediaType: MediaType, isFavorite: Boolean): AppResult<Unit>

    suspend fun setFavorite(result: MediaSearchResult, isFavorite: Boolean): AppResult<Unit>

    suspend fun setWatchedDate(ref: ExternalMediaRef, mediaType: MediaType, watchedDate: LocalDate?): AppResult<Unit>

    suspend fun setSeriesAbandoned(ref: ExternalMediaRef, isAbandoned: Boolean): AppResult<Unit>
}
