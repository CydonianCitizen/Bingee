package com.cydoniancitizen.bingee.data.featured

import com.cydoniancitizen.bingee.core.model.MediaSearchResult
import com.cydoniancitizen.bingee.core.result.AppResult
import com.cydoniancitizen.bingee.data.credential.TmdbCredentialStore
import com.cydoniancitizen.bingee.data.tmdb.executeTmdbRequest
import com.cydoniancitizen.bingee.data.tmdb.search.TmdbMovieSearchMapper
import com.cydoniancitizen.bingee.data.tmdb.search.TmdbSearchService
import com.cydoniancitizen.bingee.data.tmdb.search.TmdbTvSearchMapper
import com.cydoniancitizen.bingee.domain.repository.FeaturedReleases
import com.cydoniancitizen.bingee.domain.repository.FeaturedReleasesRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

@Singleton
internal class DefaultFeaturedReleasesRepository @Inject constructor(
    private val credentialStore: TmdbCredentialStore,
    private val service: TmdbSearchService,
    private val appearancePreferences: com.cydoniancitizen.bingee.data.settings.AppearancePreferences
) : FeaturedReleasesRepository {

    override suspend fun getFeaturedReleases(): AppResult<FeaturedReleases> {
        val credential = when (val stored = credentialStore.read()) {
            is AppResult.Success -> stored.value ?: return AppResult.Success(FeaturedReleases())
            is AppResult.Failure -> return stored
        }
        val auth = "Bearer ${credential.reveal()}"
        val language = appearancePreferences.getEffectiveTmdbLanguage()

        val (moviesResult, tvResult) = coroutineScope {
            val movies = async {
                executeTmdbRequest(
                    request = {
                        service.discoverMovies(
                            authorization = auth,
                            includeAdult = false,
                            sortBy = "popularity.desc",
                            voteCountGte = 10,
                            language = language,
                            page = 1
                        )
                    },
                    transform = { TmdbMovieSearchMapper.map(it, 1) }
                )
            }
            val tv = async {
                executeTmdbRequest(
                    request = {
                        service.discoverTvSeries(
                            authorization = auth,
                            includeAdult = false,
                            sortBy = "popularity.desc",
                            voteCountGte = 5,
                            language = language,
                            page = 1
                        )
                    },
                    transform = { TmdbTvSearchMapper.map(it, 1) }
                )
            }
            movies.await() to tv.await()
        }

        val moviesList = (moviesResult as? AppResult.Success)?.value?.results.orEmpty()
        val tvList = (tvResult as? AppResult.Success)?.value?.results.orEmpty()

        if (moviesResult is AppResult.Failure && tvResult is AppResult.Failure) {
            return AppResult.Failure(moviesResult.error)
        }

        return AppResult.Success(
            FeaturedReleases(
                movies = moviesList.take(FEATURED_ROW_LIMIT),
                series = tvList.take(FEATURED_ROW_LIMIT)
            )
        )
    }

    private companion object {
        /**
         * How many titles each Home row carries. A TMDB discover page returns 20, so one request
         * per media type fills a row that is meant to be scrolled rather than counted. Raising it
         * past 20 would buy a second round trip per row for content nobody scrolls to; lowering it
         * below about 15 makes the row short enough that the horizontal scroll stops reading as one.
         */
        const val FEATURED_ROW_LIMIT = 20
    }
}
