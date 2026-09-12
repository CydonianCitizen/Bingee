package com.cydoniancitizen.bingee.domain.repository

import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.core.model.PersonalRating
import com.cydoniancitizen.bingee.core.result.AppResult
import kotlinx.coroutines.flow.Flow

interface RatingRepository {
    fun observeRating(reference: ExternalMediaRef, mediaType: MediaType): Flow<AppResult<PersonalRating?>>

    suspend fun setRating(reference: ExternalMediaRef, mediaType: MediaType, rating: PersonalRating): AppResult<Unit>

    suspend fun removeRating(reference: ExternalMediaRef, mediaType: MediaType): AppResult<Unit>
}
