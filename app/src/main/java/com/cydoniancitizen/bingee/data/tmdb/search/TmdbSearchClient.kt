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
    private val service: TmdbSearchService,
    private val appearancePreferences: com.cydoniancitizen.bingee.data.settings.AppearancePreferences
) {
    suspend fun search(query: MediaSearchQuery, year: Int? = null): AppResult<MediaSearchPage> {
        val credential = when (val stored = credentialStore.read()) {
            is AppResult.Success -> stored.value ?: return AppResult.Failure(AppError.Unauthorized)
            is AppResult.Failure -> return stored
        }
        val authorization = "Bearer ${credential.reveal()}"
        val effectiveLanguage = if (query.language == MediaSearchQuery.DEFAULT_LANGUAGE) {
            appearancePreferences.getEffectiveTmdbLanguage()
        } else {
            query.language
        }
        return when (query.category) {
            MediaSearchCategory.MOVIES -> executeTmdbRequest(
                request = {
                    service.searchMovies(
                        authorization = authorization,
                        query = query.query,
                        includeAdult = false,
                        language = effectiveLanguage,
                        page = query.page,
                        primaryReleaseYear = year
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
                        language = effectiveLanguage,
                        page = query.page,
                        firstAirDateYear = year
                    )
                },
                transform = { TmdbTvSearchMapper.map(it, query.page) }
            )
        }
    }

    suspend fun findByExternalId(externalId: String, externalSource: String): AppResult<TmdbExternalIdMatches> {
        if (externalId.isBlank() || externalSource !in SUPPORTED_EXTERNAL_SOURCES) {
            return AppResult.Failure(AppError.InvalidInput)
        }
        val credential = when (val stored = credentialStore.read()) {
            is AppResult.Success -> stored.value ?: return AppResult.Failure(AppError.Unauthorized)
            is AppResult.Failure -> return stored
        }
        val language = appearancePreferences.getEffectiveTmdbLanguage()
        return executeTmdbRequest(
            request = {
                service.findByExternalId(
                    authorization = "Bearer ${credential.reveal()}",
                    externalId = externalId,
                    externalSource = externalSource,
                    language = language
                )
            },
            transform = TmdbFindMapper::map
        )
    }

    private companion object {
        val SUPPORTED_EXTERNAL_SOURCES = setOf("imdb_id", "tvdb_id")
    }
}
