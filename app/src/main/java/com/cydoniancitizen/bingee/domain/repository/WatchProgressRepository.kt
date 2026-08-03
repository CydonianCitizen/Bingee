package com.cydoniancitizen.bingee.domain.repository

import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.MovieWatchState
import com.cydoniancitizen.bingee.core.result.AppResult
import kotlinx.coroutines.flow.Flow

interface WatchProgressRepository {
    fun observeMovie(reference: ExternalMediaRef): Flow<AppResult<MovieWatchState>>

    suspend fun markEpisodeWatched(episodeRef: ExternalMediaRef): AppResult<Unit>

    suspend fun markEpisodeUnwatched(episodeRef: ExternalMediaRef): AppResult<Unit>

    suspend fun markSeasonWatched(seasonRef: ExternalMediaRef): AppResult<Unit>

    suspend fun markSeasonUnwatched(seasonRef: ExternalMediaRef): AppResult<Unit>

    suspend fun markMovieWatched(reference: ExternalMediaRef): AppResult<Unit>

    suspend fun markMovieUnwatched(reference: ExternalMediaRef): AppResult<Unit>
}
