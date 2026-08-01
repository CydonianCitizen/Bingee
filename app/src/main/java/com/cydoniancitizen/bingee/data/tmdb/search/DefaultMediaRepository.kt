package com.cydoniancitizen.bingee.data.tmdb.search

import com.cydoniancitizen.bingee.core.model.MediaSearchPage
import com.cydoniancitizen.bingee.core.model.MediaSearchQuery
import com.cydoniancitizen.bingee.core.result.AppResult
import com.cydoniancitizen.bingee.domain.repository.MediaRepository
import javax.inject.Inject

internal class DefaultMediaRepository @Inject constructor(private val client: TmdbSearchClient) : MediaRepository {
    override suspend fun search(query: MediaSearchQuery): AppResult<MediaSearchPage> = client.search(query)
}
