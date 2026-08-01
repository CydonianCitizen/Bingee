package com.cydoniancitizen.bingee.domain.repository

import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.MediaDetails
import com.cydoniancitizen.bingee.core.model.MediaSearchResult
import com.cydoniancitizen.bingee.core.result.AppResult

interface MediaRepository {
    suspend fun search(query: String): AppResult<List<MediaSearchResult>>

    suspend fun getDetails(ref: ExternalMediaRef): AppResult<MediaDetails>
}
