package com.cydoniancitizen.bingee.data.jikan.search

import com.cydoniancitizen.bingee.core.model.MediaSearchPage
import com.cydoniancitizen.bingee.core.model.MediaSearchQuery
import com.cydoniancitizen.bingee.core.result.AppError
import com.cydoniancitizen.bingee.core.result.AppResult
import com.cydoniancitizen.bingee.data.jikan.JikanRequestGate
import com.cydoniancitizen.bingee.data.jikan.jikanCall
import javax.inject.Inject

internal class JikanSearchClient @Inject constructor(
    private val service: JikanSearchService,
    private val requestGate: JikanRequestGate
) {
    suspend fun search(query: MediaSearchQuery): AppResult<MediaSearchPage> {
        if (query.category != com.cydoniancitizen.bingee.core.model.MediaSearchCategory.ANIME) {
            return AppResult.Failure(AppError.InvalidInput)
        }
        return jikanCall(requestGate, { service.searchAnime(query.query, query.page) }) {
            JikanSearchMapper.map(it, query.page)
        }
    }
}
