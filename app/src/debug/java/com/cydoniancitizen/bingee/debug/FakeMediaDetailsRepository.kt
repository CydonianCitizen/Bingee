package com.cydoniancitizen.bingee.debug

import com.cydoniancitizen.bingee.core.model.CachedMediaDetails
import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.core.result.AppError
import com.cydoniancitizen.bingee.core.result.AppResult
import com.cydoniancitizen.bingee.domain.repository.MediaDetailsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeMediaDetailsRepository(
    initial: Map<ExternalMediaRef, CachedMediaDetails> = emptyMap(),
    var refreshFailure: AppError? = null
) : MediaDetailsRepository {
    private val cache = MutableStateFlow(initial)
    val refreshes = mutableListOf<Triple<Long, MediaType, Boolean>>()

    override fun observeDetails(tmdbId: Long): Flow<AppResult<CachedMediaDetails?>> = cache.map { current ->
        AppResult.Success(current[ExternalMediaRef(MediaSource.TMDB, tmdbId.toString())])
    }

    override suspend fun refreshDetails(tmdbId: Long, mediaType: MediaType, force: Boolean): AppResult<Unit> {
        refreshes += Triple(tmdbId, mediaType, force)
        refreshFailure?.let { return AppResult.Failure(it) }
        return AppResult.Success(Unit)
    }
}
