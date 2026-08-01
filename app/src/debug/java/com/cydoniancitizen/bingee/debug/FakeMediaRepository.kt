package com.cydoniancitizen.bingee.debug

import com.cydoniancitizen.bingee.core.model.MediaSearchPage
import com.cydoniancitizen.bingee.core.model.MediaSearchQuery
import com.cydoniancitizen.bingee.core.result.AppError
import com.cydoniancitizen.bingee.core.result.AppResult
import com.cydoniancitizen.bingee.domain.repository.MediaRepository

class FakeMediaRepository(
    private val resultsByPage: MutableMap<Int, AppResult<MediaSearchPage>> =
        mutableMapOf(
            1 to AppResult.Success(FakeMediaData.firstPage),
            2 to AppResult.Success(FakeMediaData.finalPage)
        )
) : MediaRepository {
    private val recordedSearchQueries = mutableListOf<MediaSearchQuery>()

    val searchQueries: List<MediaSearchQuery>
        get() = recordedSearchQueries.toList()

    override suspend fun search(query: MediaSearchQuery): AppResult<MediaSearchPage> {
        recordedSearchQueries += query
        return resultsByPage[query.page] ?: AppResult.Success(
            MediaSearchPage(emptyList(), query.page, query.page, 0)
        )
    }

    companion object {
        fun empty(): FakeMediaRepository = FakeMediaRepository(
            mutableMapOf(
                1 to AppResult.Success(MediaSearchPage(emptyList(), 1, 1, 0))
            )
        )

        fun failing(error: AppError): FakeMediaRepository = FakeMediaRepository(
            mutableMapOf(1 to AppResult.Failure(error))
        )

        fun paginationFailing(error: AppError): FakeMediaRepository = FakeMediaRepository(
            mutableMapOf(
                1 to AppResult.Success(FakeMediaData.firstPage),
                2 to AppResult.Failure(error)
            )
        )
    }
}
