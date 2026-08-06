package com.cydoniancitizen.bingee.data.jikan.search

import com.cydoniancitizen.bingee.core.common.AnimeFeatureAvailability
import com.cydoniancitizen.bingee.core.model.MediaSearchPage
import com.cydoniancitizen.bingee.core.model.MediaSearchQuery
import com.cydoniancitizen.bingee.core.result.AppError
import com.cydoniancitizen.bingee.core.result.AppResult
import com.cydoniancitizen.bingee.domain.repository.AnimeRepository
import javax.inject.Inject

internal class DefaultAnimeRepository @Inject constructor(
    private val client: JikanSearchClient,
    private val availability: AnimeFeatureAvailability
) : AnimeRepository {
    override suspend fun search(query: MediaSearchQuery): AppResult<MediaSearchPage> {
        if (!availability.isAvailable) return AppResult.Failure(AppError.FeatureUnavailable)
        return client.search(query)
    }
}
