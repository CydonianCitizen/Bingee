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
    val refreshes = mutableListOf<Triple<ExternalMediaRef, MediaType, Boolean>>()

    override fun observeDetails(reference: ExternalMediaRef): Flow<AppResult<CachedMediaDetails?>> =
        cache.map { current ->
            if (reference.source == MediaSource.TMDB) {
                AppResult.Success(current[reference])
            } else {
                AppResult.Failure(AppError.UnsupportedData)
            }
        }

    override suspend fun refreshDetails(
        reference: ExternalMediaRef,
        mediaType: MediaType,
        force: Boolean
    ): AppResult<Unit> {
        refreshes += Triple(reference, mediaType, force)
        refreshFailure?.let { return AppResult.Failure(it) }
        return AppResult.Success(Unit)
    }
}
