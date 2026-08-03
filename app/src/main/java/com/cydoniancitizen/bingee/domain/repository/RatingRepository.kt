package com.cydoniancitizen.bingee.domain.repository

import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.PersonalRating
import com.cydoniancitizen.bingee.core.result.AppResult
import kotlinx.coroutines.flow.Flow

interface RatingRepository {
    fun observeRating(reference: ExternalMediaRef): Flow<AppResult<PersonalRating?>>

    suspend fun setRating(reference: ExternalMediaRef, rating: PersonalRating): AppResult<Unit>

    suspend fun removeRating(reference: ExternalMediaRef): AppResult<Unit>
}
