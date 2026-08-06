package com.cydoniancitizen.bingee.data.tmdb.details

import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.MediaDetails
import com.cydoniancitizen.bingee.core.model.MediaSearchQuery
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.core.model.Season
import com.cydoniancitizen.bingee.core.result.AppError
import com.cydoniancitizen.bingee.core.result.AppResult
import com.cydoniancitizen.bingee.data.credential.TmdbCredentialStore
import com.cydoniancitizen.bingee.data.tmdb.executeTmdbRequest
import com.cydoniancitizen.bingee.data.tmdb.series.TmdbSeasonSummaryMapper
import javax.inject.Inject

internal data class TmdbMediaDetailsPayload(val details: MediaDetails, val seasons: List<Season> = emptyList())

internal interface TmdbDetailsRemoteDataSource {
    suspend fun load(reference: ExternalMediaRef, mediaType: MediaType): AppResult<TmdbMediaDetailsPayload>
}

internal class TmdbDetailsClient @Inject constructor(
    private val credentialStore: TmdbCredentialStore,
    private val service: TmdbDetailsService,
    private val appearancePreferences: com.cydoniancitizen.bingee.data.settings.AppearancePreferences
) : TmdbDetailsRemoteDataSource {
    override suspend fun load(reference: ExternalMediaRef, mediaType: MediaType): AppResult<TmdbMediaDetailsPayload> {
        if (reference.source != MediaSource.TMDB) return AppResult.Failure(AppError.UnsupportedData)
        val providerId = reference.externalId.trim().toLongOrNull()?.takeIf { it > 0 }
            ?: return AppResult.Failure(AppError.InvalidInput)
        val credential = when (val stored = credentialStore.read()) {
            is AppResult.Success -> stored.value ?: return AppResult.Failure(AppError.Unauthorized)
            is AppResult.Failure -> return stored
        }
        val authorization = "Bearer ${credential.reveal()}"
        val language = appearancePreferences.getEffectiveTmdbLanguage()
        return when (mediaType) {
            MediaType.MOVIE -> executeTmdbRequest(
                request = {
                    service.movieDetails(authorization, providerId, language)
                },
                transform = { TmdbMediaDetailsPayload(requireNotNull(TmdbMovieDetailsMapper.map(it))) }
            )
            MediaType.SERIES -> executeTmdbRequest(
                request = {
                    service.tvDetails(authorization, providerId, language)
                },
                transform = {
                    val details = requireNotNull(TmdbTvDetailsMapper.map(it))
                    TmdbMediaDetailsPayload(
                        details = details,
                        seasons = TmdbSeasonSummaryMapper.mapAll(details.externalRef, it.seasons)
                    )
                }
            )

            MediaType.ANIME -> AppResult.Failure(AppError.UnsupportedData)
        }
    }
}
