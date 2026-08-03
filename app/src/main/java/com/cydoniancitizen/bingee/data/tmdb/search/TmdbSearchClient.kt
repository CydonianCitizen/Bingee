package com.cydoniancitizen.bingee.data.tmdb.search

import com.cydoniancitizen.bingee.core.model.MediaSearchCategory
import com.cydoniancitizen.bingee.core.model.MediaSearchPage
import com.cydoniancitizen.bingee.core.model.MediaSearchQuery
import com.cydoniancitizen.bingee.core.result.AppError
import com.cydoniancitizen.bingee.core.result.AppResult
import com.cydoniancitizen.bingee.data.credential.TmdbCredentialStore
import com.cydoniancitizen.bingee.data.tmdb.executeTmdbRequest
import javax.inject.Inject

internal class TmdbSearchClient @Inject constructor(
    private val credentialStore: TmdbCredentialStore,
    private val service: TmdbSearchService
) {
    suspend fun search(query: MediaSearchQuery): AppResult<MediaSearchPage> {
        val credential = when (val stored = credentialStore.read()) {
            is AppResult.Success -> stored.value ?: return AppResult.Failure(AppError.Unauthorized)
            is AppResult.Failure -> return stored
        }
        val authorization = "Bearer ${credential.reveal()}"
        return when (query.category) {
            MediaSearchCategory.MOVIES -> executeTmdbRequest(
                request = {
                    service.searchMovies(
                        authorization = authorization,
                        query = query.query,
                        includeAdult = false,
                        language = query.language,
                        page = query.page
                    )
                },
                transform = { TmdbMovieSearchMapper.map(it, query.page) }
            )

            MediaSearchCategory.TV_SERIES -> executeTmdbRequest(
                request = {
                    service.searchTvSeries(
                        authorization = authorization,
                        query = query.query,
                        includeAdult = false,
                        language = query.language,
                        page = query.page
                    )
                },
                transform = { TmdbTvSearchMapper.map(it, query.page) }
            )
        }
    }
}
