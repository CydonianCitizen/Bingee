package com.cydoniancitizen.bingee.domain.repository

import com.cydoniancitizen.bingee.core.model.MediaSearchPage
import com.cydoniancitizen.bingee.core.model.MediaSearchQuery
import com.cydoniancitizen.bingee.core.result.AppResult

interface MediaRepository {
    suspend fun search(query: MediaSearchQuery): AppResult<MediaSearchPage>
}
