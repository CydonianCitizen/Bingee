package com.cydoniancitizen.bingee.data.tmdb.series

import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.MediaSearchQuery
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.result.AppError
import com.cydoniancitizen.bingee.core.result.AppResult
import com.cydoniancitizen.bingee.data.credential.TmdbCredentialStore
import com.cydoniancitizen.bingee.data.tmdb.executeTmdbRequest
import javax.inject.Inject

internal interface TmdbSeasonRemoteDataSource {
    suspend fun load(tmdbId: Long, seasonNumber: Int): AppResult<TmdbSeasonPayload>
}

internal class TmdbSeasonClient @Inject constructor(
    private val credentialStore: TmdbCredentialStore,
    private val service: TmdbSeasonService,
    private val appearancePreferences: com.cydoniancitizen.bingee.data.settings.AppearancePreferences
) : TmdbSeasonRemoteDataSource {
    override suspend fun load(tmdbId: Long, seasonNumber: Int): AppResult<TmdbSeasonPayload> {
        if (seasonNumber < 0) return AppResult.Failure(AppError.InvalidInput)
        if (tmdbId <= 0) return AppResult.Failure(AppError.InvalidInput)
        val credential = when (val stored = credentialStore.read()) {
            is AppResult.Success -> stored.value ?: return AppResult.Failure(AppError.Unauthorized)
            is AppResult.Failure -> return stored
        }
        val language = appearancePreferences.getEffectiveTmdbLanguage()
        return executeTmdbRequest(
            request = {
                service.seasonDetails(
                    authorization = "Bearer ${credential.reveal()}",
                    seriesId = tmdbId,
                    seasonNumber = seasonNumber,
                    language = language
                )
            },
            transform = {
                requireNotNull(
                    TmdbSeasonDetailsMapper.map(
                        ExternalMediaRef(MediaSource.TMDB, tmdbId.toString()),
                        seasonNumber,
                        it
                    )
                )
            }
        )
    }
}
