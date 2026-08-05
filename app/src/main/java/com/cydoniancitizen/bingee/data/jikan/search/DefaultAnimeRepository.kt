package com.cydoniancitizen.bingee.data.jikan.search

import com.cydoniancitizen.bingee.core.model.MediaSearchPage
import com.cydoniancitizen.bingee.core.model.MediaSearchQuery
import com.cydoniancitizen.bingee.core.result.AppResult
import com.cydoniancitizen.bingee.domain.repository.AnimeRepository
import javax.inject.Inject

internal class DefaultAnimeRepository @Inject constructor(private val client: JikanSearchClient) : AnimeRepository {
    override suspend fun search(query: MediaSearchQuery): AppResult<MediaSearchPage> = client.search(query)
}
