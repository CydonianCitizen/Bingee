package com.cydoniancitizen.bingee.data.tmdb.search

import com.cydoniancitizen.bingee.core.credential.TmdbCredential
import com.cydoniancitizen.bingee.core.model.MediaSearchCategory
import com.cydoniancitizen.bingee.core.model.MediaSearchQuery
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.core.result.AppError
import com.cydoniancitizen.bingee.core.result.AppResult
import com.cydoniancitizen.bingee.data.credential.TmdbCredentialStore
import com.cydoniancitizen.bingee.data.settings.AppLanguage
import com.cydoniancitizen.bingee.data.settings.AppTheme
import com.cydoniancitizen.bingee.data.settings.AppearancePreferences
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class TmdbMediaRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repository: DefaultMediaRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val service =
            Retrofit.Builder()
                .baseUrl(server.url("/"))
                .client(OkHttpClient())
                .addConverterFactory(GsonConverterFactory.create(Gson()))
                .build()
                .create(TmdbSearchService::class.java)
        repository =
            DefaultMediaRepository(
                TmdbSearchClient(FakeCredentialStore(), service, FakeAppearancePreferences())
            )
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun movieRequestUsesDocumentedPathParametersAndAuthorizationBoundary() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "page": 2,
                  "results": [{
                    "id": 11,
                    "title": "Star Wars",
                    "original_title": "Star Wars",
                    "poster_path": "/star.jpg",
                    "release_date": "1977-05-25",
                    "overview": "Space opera"
                  }],
                  "total_pages": 5,
                  "total_results": 92
                }
                """.trimIndent()
            )
        )

        val result =
            repository.search(
                MediaSearchQuery(
                    query = "Star Wars & Beyond",
                    category = MediaSearchCategory.MOVIES,
                    page = 2
                )
            )
        val request = server.takeRequest()

        assertEquals("/3/search/movie", request.requestUrl?.encodedPath)
        assertEquals("Star Wars & Beyond", request.requestUrl?.queryParameter("query"))
        assertEquals("false", request.requestUrl?.queryParameter("include_adult"))
        assertEquals("en-US", request.requestUrl?.queryParameter("language"))
        assertEquals("2", request.requestUrl?.queryParameter("page"))
        val authorization = request.getHeader("Authorization")
        assertNotNull(authorization)
        assertTrue(authorization?.startsWith("Bearer ") == true)
        assertFalse(result.toString().contains(TEST_CREDENTIAL))

        val page = (result as AppResult.Success).value
        assertEquals(2, page.page)
        assertEquals(5, page.totalPages)
        assertEquals(92, page.totalResults)
        assertEquals(MediaType.MOVIE, page.results.single().mediaType)
    }

    @Test
    fun tvRequestUsesSeparateEndpointAndDto() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "page": 1,
                  "results": [{
                    "id": 1399,
                    "name": "Game of Thrones",
                    "original_name": "Game of Thrones",
                    "poster_path": null,
                    "first_air_date": "2011-04-17",
                    "overview": null
                  }],
                  "total_pages": 1,
                  "total_results": 1
                }
                """.trimIndent()
            )
        )

        val result =
            repository.search(
                MediaSearchQuery("Game of Thrones", MediaSearchCategory.TV_SERIES)
            )
        val request = server.takeRequest()

        assertEquals("/3/search/tv", request.requestUrl?.encodedPath)
        val page = (result as AppResult.Success).value
        assertEquals(MediaType.SERIES, page.results.single().mediaType)
        assertEquals("1399", page.results.single().externalRef.externalId)
    }

    @Test
    fun emptyResponseReturnsSuccessfulDomainPage() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"page":1,"results":[],"total_pages":1,"total_results":0}"""
            )
        )

        val result =
            repository.search(MediaSearchQuery("none", MediaSearchCategory.MOVIES))

        val page = (result as AppResult.Success).value
        assertTrue(page.results.isEmpty())
        assertEquals(0, page.totalResults)
    }

    @Test
    fun unauthorizedRateLimitAndServerErrorsMapSafely() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"status_code":7}"""))
        server.enqueue(MockResponse().setResponseCode(429).setBody("""{"status_code":25}"""))
        server.enqueue(MockResponse().setResponseCode(503).setBody("""{"status_code":9}"""))
        val query = MediaSearchQuery("fixed", MediaSearchCategory.MOVIES)

        assertEquals(AppResult.Failure(AppError.Unauthorized), repository.search(query))
        assertEquals(AppResult.Failure(AppError.RateLimited), repository.search(query))
        assertEquals(AppResult.Failure(AppError.RemoteServiceFailure), repository.search(query))
    }

    @Test
    fun malformedJsonMapsToInvalidResponseWithoutLeakingBody() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("not-json"))

        val result =
            repository.search(MediaSearchQuery("fixed", MediaSearchCategory.MOVIES))

        assertEquals(AppResult.Failure(AppError.InvalidRemoteResponse), result)
        assertFalse(result.toString().contains("not-json"))
    }

    @Test
    fun connectionFailureMapsToNetworkUnavailable() = runTest {
        server.close()

        val result =
            repository.search(MediaSearchQuery("fixed", MediaSearchCategory.TV_SERIES))

        assertEquals(AppResult.Failure(AppError.NetworkUnavailable), result)
    }

    private class FakeCredentialStore : TmdbCredentialStore {
        override suspend fun read(): AppResult<TmdbCredential?> = AppResult.Success(TmdbCredential(TEST_CREDENTIAL))

        override suspend fun save(credential: TmdbCredential): AppResult<Unit> = AppResult.Success(Unit)

        override suspend fun delete(): AppResult<Unit> = AppResult.Success(Unit)
    }

    private companion object {
        const val TEST_CREDENTIAL = "unit_test_credential_not_secret"
    }

    private class FakeAppearancePreferences : AppearancePreferences {
        override fun observeTheme(): Flow<AppTheme> = flowOf(AppTheme.SYSTEM_DEFAULT)
        override suspend fun setTheme(theme: AppTheme) {}
        override fun observeLanguage(): Flow<AppLanguage> = flowOf(AppLanguage.ENGLISH)
        override suspend fun setLanguage(language: AppLanguage) {}
        override suspend fun getEffectiveTmdbLanguage(): String = "en-US"
    }
}
