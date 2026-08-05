package com.cydoniancitizen.bingee.data.imports.tvtime

import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.MediaSearchCategory
import com.cydoniancitizen.bingee.core.model.MediaSearchQuery
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.core.result.AppResult
import com.cydoniancitizen.bingee.data.tmdb.details.TmdbDetailsRemoteDataSource
import com.cydoniancitizen.bingee.data.tmdb.search.TmdbSearchClient
import com.cydoniancitizen.bingee.data.tmdb.series.TmdbSeasonRemoteDataSource
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class DefaultTvTimeTmdbGateway @Inject constructor(
    private val searchClient: TmdbSearchClient,
    private val detailsSource: TmdbDetailsRemoteDataSource,
    private val seasonSource: TmdbSeasonRemoteDataSource
) : TvTimeTmdbGateway {
    override suspend fun findMedia(
        identity: String,
        namespace: String,
        mediaType: MediaType
    ): AppResult<List<TmdbImportCandidate>> {
        if (mediaType == MediaType.MOVIE && namespace != "IMDB") return AppResult.Success(emptyList())
        val externalSource = externalSource(namespace) ?: return AppResult.Success(emptyList())
        return when (val result = searchClient.findByExternalId(identity, externalSource)) {
            is AppResult.Failure -> result
            is AppResult.Success -> AppResult.Success(
                when (mediaType) {
                    MediaType.MOVIE -> result.value.movies
                    MediaType.SERIES -> result.value.series
                }.map(::toCandidate)
            )
        }
    }

    override suspend fun findEpisodes(
        identity: String,
        namespace: String
    ): AppResult<List<TmdbImportEpisodeCandidate>> {
        val externalSource = externalSource(namespace) ?: return AppResult.Success(emptyList())
        return when (val result = searchClient.findByExternalId(identity, externalSource)) {
            is AppResult.Failure -> result
            is AppResult.Success -> AppResult.Success(
                result.value.episodes.mapNotNull { episode ->
                    val seriesRef = episode.seriesRef ?: return@mapNotNull null
                    val season = episode.seasonNumber ?: return@mapNotNull null
                    val number = episode.episodeNumber ?: return@mapNotNull null
                    TmdbImportEpisodeCandidate(
                        externalRef = episode.externalRef,
                        seriesRef = seriesRef,
                        seasonNumber = season,
                        episodeNumber = number,
                        title = episode.title,
                        airDate = episode.airDate
                    )
                }
            )
        }
    }

    override suspend fun searchMedia(
        mediaType: MediaType,
        title: String,
        year: Int?
    ): AppResult<List<TmdbImportCandidate>> {
        val query = MediaSearchQuery(
            query = title.trim(),
            category = when (mediaType) {
                MediaType.MOVIE -> MediaSearchCategory.MOVIES
                MediaType.SERIES -> MediaSearchCategory.TV_SERIES
            }
        )
        return when (val result = searchClient.search(query, year)) {
            is AppResult.Failure -> result
            is AppResult.Success -> AppResult.Success(
                result.value.results.take(TvTimeImportLimits.MAX_CANDIDATES).map(::toCandidate)
            )
        }
    }

    override suspend fun loadDetails(
        candidate: TmdbImportCandidate
    ): AppResult<com.cydoniancitizen.bingee.data.tmdb.details.TmdbMediaDetailsPayload> =
        detailsSource.load(candidate.externalRef, candidate.mediaType)

    override suspend fun loadSeason(
        seriesRef: ExternalMediaRef,
        seasonNumber: Int
    ): AppResult<com.cydoniancitizen.bingee.data.tmdb.series.TmdbSeasonPayload> =
        seasonSource.load(seriesRef, seasonNumber)

    private fun externalSource(namespace: String): String? = when (namespace) {
        "IMDB" -> "imdb_id"
        "TVDB" -> "tvdb_id"
        else -> null
    }
}

private fun toCandidate(result: com.cydoniancitizen.bingee.core.model.MediaSearchResult): TmdbImportCandidate =
    TmdbImportCandidate(
        externalRef = result.externalRef,
        mediaType = result.mediaType,
        title = result.title,
        originalTitle = result.originalTitle,
        year = result.releaseDate?.year,
        posterUrl = result.posterUrl,
        overview = result.overview
    )
