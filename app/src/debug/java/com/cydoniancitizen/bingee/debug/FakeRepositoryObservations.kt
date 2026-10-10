package com.cydoniancitizen.bingee.debug

import com.cydoniancitizen.bingee.core.model.ContinueWatchingItem
import com.cydoniancitizen.bingee.core.model.LibraryEntry
import com.cydoniancitizen.bingee.core.model.LibraryMediaFilter
import com.cydoniancitizen.bingee.core.model.LibraryQuery
import com.cydoniancitizen.bingee.core.model.ReleaseEvent
import com.cydoniancitizen.bingee.core.model.toContinueWatchingItem
import com.cydoniancitizen.bingee.core.result.AppResult
import com.cydoniancitizen.bingee.domain.policy.ContinueWatchingPolicy
import com.cydoniancitizen.bingee.domain.repository.LibraryRepository
import com.cydoniancitizen.bingee.domain.repository.ReleaseCalendarRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Fake-only projections; production repositories implement these reads with dedicated Room queries. */
internal fun LibraryRepository.observeFakeContinueWatching(): Flow<AppResult<List<ContinueWatchingItem>>> =
    observeEntries(LibraryQuery(mediaFilter = LibraryMediaFilter.TV_SERIES)).map { result ->
        when (result) {
            is AppResult.Success -> AppResult.Success(
                ContinueWatchingPolicy.select(result.value.mapNotNull(LibraryEntry::toContinueWatchingItem))
            )
            is AppResult.Failure -> result
        }
    }

internal fun ReleaseCalendarRepository.observeFakeEvents(
    fromDate: LocalDate,
    throughDate: LocalDate
): Flow<AppResult<List<ReleaseEvent>>> = observeEvents(fromDate).map { result ->
    when (result) {
        is AppResult.Success -> AppResult.Success(result.value.filter { !it.eventDate.isAfter(throughDate) })
        is AppResult.Failure -> result
    }
}
