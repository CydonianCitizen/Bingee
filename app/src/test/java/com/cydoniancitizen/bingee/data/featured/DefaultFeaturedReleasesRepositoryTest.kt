package com.cydoniancitizen.bingee.data.featured

import com.cydoniancitizen.bingee.core.credential.TmdbCredential
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.core.result.AppResult
import com.cydoniancitizen.bingee.data.credential.TmdbCredentialStore
import com.cydoniancitizen.bingee.data.settings.AppLanguage
import com.cydoniancitizen.bingee.data.settings.AppTheme
import com.cydoniancitizen.bingee.data.settings.AppearancePreferences
import com.cydoniancitizen.bingee.data.tmdb.search.TmdbFindResponseDto
import com.cydoniancitizen.bingee.data.tmdb.search.TmdbMovieSearchResponseDto
import com.cydoniancitizen.bingee.data.tmdb.search.TmdbMovieSearchResultDto
import com.cydoniancitizen.bingee.data.tmdb.search.TmdbSearchService
import com.cydoniancitizen.bingee.data.tmdb.search.TmdbTvSearchResponseDto
import com.cydoniancitizen.bingee.data.tmdb.search.TmdbTvSearchResultDto
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultFeaturedReleasesRepositoryTest {
    @Test
    fun movieAndTvRequestsStartConcurrentlyAndKeepExistingOrder() = runTest {
        val moviesStarted = CompletableDeferred<Unit>()
        val tvStarted = CompletableDeferred<Unit>()
        val service = FakeSearchService(
            onMovies = {
                moviesStarted.complete(Unit)
                tvStarted.await()
                movieResponse(1, 3)
            },
            onTv = {
                tvStarted.complete(Unit)
                moviesStarted.await()
                tvResponse(2)
            }
        )
        val repository = repository(service)

        val result = async { repository.getFeaturedReleases() }
        moviesStarted.await()
        tvStarted.await()

        assertEquals(
            listOf(MediaType.MOVIE, MediaType.SERIES, MediaType.MOVIE),
            (result.await() as AppResult.Success).value.map { it.mediaType }
        )
    }

    private fun repository(service: TmdbSearchService) = DefaultFeaturedReleasesRepository(
        credentialStore = FakeCredentialStore(),
        service = service,
        appearancePreferences = FakeAppearancePreferences()
    )

    private fun movieResponse(vararg ids: Long) = Response.success(
        TmdbMovieSearchResponseDto(
            page = 1,
            results = ids.map { id ->
                TmdbMovieSearchResultDto(
                    id = id,
                    title = "Movie $id",
                    originalTitle = null,
                    posterPath = null,
                    releaseDate = LocalDate.of(2026, 1, 1).toString(),
                    overview = null
                )
            },
            totalPages = 1,
            totalResults = ids.size
        )
    )

    private fun tvResponse(vararg ids: Long) = Response.success(
        TmdbTvSearchResponseDto(
            page = 1,
            results = ids.map { id ->
                TmdbTvSearchResultDto(
                    id = id,
                    name = "TV $id",
                    originalName = null,
                    posterPath = null,
                    firstAirDate = LocalDate.of(2026, 1, 1).toString(),
                    overview = null
                )
            },
            totalPages = 1,
            totalResults = ids.size
        )
    )

    private class FakeCredentialStore : TmdbCredentialStore {
        override suspend fun read() = AppResult.Success<TmdbCredential?>(TmdbCredential("test"))
        override suspend fun save(credential: TmdbCredential) = AppResult.Success(Unit)
        override suspend fun delete() = AppResult.Success(Unit)
    }

    private class FakeAppearancePreferences : AppearancePreferences {
        override fun observeTheme(): Flow<AppTheme> = flowOf(AppTheme.SYSTEM_DEFAULT)
        override suspend fun setTheme(theme: AppTheme) = Unit
        override fun observeLanguage(): Flow<AppLanguage> = flowOf(AppLanguage.ENGLISH)
        override suspend fun setLanguage(language: AppLanguage) = Unit
        override suspend fun getEffectiveTmdbLanguage() = "en-US"
    }

    private class FakeSearchService(
        private val onMovies: suspend () -> Response<TmdbMovieSearchResponseDto>,
        private val onTv: suspend () -> Response<TmdbTvSearchResponseDto>
    ) : TmdbSearchService {
        override suspend fun searchMovies(
            authorization: String,
            query: String,
            includeAdult: Boolean,
            language: String,
            page: Int,
            primaryReleaseYear: Int?
        ) = Response.success(TmdbMovieSearchResponseDto(1, emptyList(), 1, 0))

        override suspend fun searchTvSeries(
            authorization: String,
            query: String,
            includeAdult: Boolean,
            language: String,
            page: Int,
            firstAirDateYear: Int?
        ) = Response.success(TmdbTvSearchResponseDto(1, emptyList(), 1, 0))

        override suspend fun findByExternalId(
            authorization: String,
            externalId: String,
            externalSource: String,
            language: String
        ) = Response.success(TmdbFindResponseDto(null, null, null))

        override suspend fun discoverMovies(
            authorization: String,
            region: String?,
            includeAdult: Boolean,
            sortBy: String,
            voteCountGte: Int,
            language: String,
            page: Int
        ) = onMovies()

        override suspend fun discoverTvSeries(
            authorization: String,
            includeAdult: Boolean,
            sortBy: String,
            voteCountGte: Int,
            language: String,
            page: Int
        ) = onTv()
    }
}
