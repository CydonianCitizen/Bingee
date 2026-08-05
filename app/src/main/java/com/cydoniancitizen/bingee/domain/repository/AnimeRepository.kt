package com.cydoniancitizen.bingee.domain.repository

import com.cydoniancitizen.bingee.core.model.MediaSearchPage
import com.cydoniancitizen.bingee.core.model.MediaSearchQuery
import com.cydoniancitizen.bingee.core.result.AppResult

/** Jikan-only boundary; it deliberately has no TMDB credential dependency. */
interface AnimeRepository {
    suspend fun search(query: MediaSearchQuery): AppResult<MediaSearchPage>
}
