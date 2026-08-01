package com.cydoniancitizen.bingee.debug

import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.MediaDetails
import com.cydoniancitizen.bingee.core.model.MediaSearchResult
import com.cydoniancitizen.bingee.core.result.AppError
import com.cydoniancitizen.bingee.core.result.AppResult
import com.cydoniancitizen.bingee.domain.repository.MediaRepository

class FakeMediaRepository(
    var searchResult: AppResult<List<MediaSearchResult>> =
        AppResult.Success(FakeMediaData.searchResults),
    var detailsResult: AppResult<MediaDetails> = AppResult.Success(FakeMediaData.movieDetails)
) : MediaRepository {
    private val recordedSearchQueries = mutableListOf<String>()

    val searchQueries: List<String>
        get() = recordedSearchQueries.toList()

    override suspend fun search(query: String): AppResult<List<MediaSearchResult>> {
        recordedSearchQueries += query
        return searchResult
    }

    override suspend fun getDetails(ref: ExternalMediaRef): AppResult<MediaDetails> = detailsResult

    companion object {
        fun failing(error: AppError): FakeMediaRepository = FakeMediaRepository(
            searchResult = AppResult.Failure(error),
            detailsResult = AppResult.Failure(error)
        )
    }
}
