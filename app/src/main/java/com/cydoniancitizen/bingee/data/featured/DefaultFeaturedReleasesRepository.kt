package com.cydoniancitizen.bingee.data.featured

import com.cydoniancitizen.bingee.core.model.MediaSearchResult
import com.cydoniancitizen.bingee.core.result.AppResult
import com.cydoniancitizen.bingee.data.credential.TmdbCredentialStore
import com.cydoniancitizen.bingee.data.tmdb.executeTmdbRequest
import com.cydoniancitizen.bingee.data.tmdb.search.TmdbMovieSearchMapper
import com.cydoniancitizen.bingee.data.tmdb.search.TmdbSearchService
import com.cydoniancitizen.bingee.data.tmdb.search.TmdbTvSearchMapper
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

    override suspend fun getFeaturedReleases(): AppResult<List<MediaSearchResult>> {
        val credential = when (val stored = credentialStore.read()) {
            is AppResult.Success -> stored.value ?: return AppResult.Success(emptyList())
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

        // Interleave & remove duplicates, bound result count to 10
        val combined = mutableListOf<MediaSearchResult>()
        val seen = mutableSetOf<String>()

        val maxLen = maxOf(moviesList.size, tvList.size)
        for (i in 0 until maxLen) {
            if (i < moviesList.size) {
                val item = moviesList[i]
                val key = "${item.externalRef.source}:${item.externalRef.externalId}"
                if (seen.add(key)) combined.add(item)
            }
            if (i < tvList.size) {
                val item = tvList[i]
                val key = "${item.externalRef.source}:${item.externalRef.externalId}"
                if (seen.add(key)) combined.add(item)
            }
            if (combined.size >= 10) break
        }

        return AppResult.Success(combined.take(10))
    }
}
