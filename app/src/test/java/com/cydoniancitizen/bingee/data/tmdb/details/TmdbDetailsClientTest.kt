package com.cydoniancitizen.bingee.data.tmdb.details

import com.cydoniancitizen.bingee.core.credential.TmdbCredential
import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.MediaSource
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

class TmdbDetailsClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: TmdbDetailsClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val service = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(OkHttpClient())
            .addConverterFactory(GsonConverterFactory.create(Gson()))
            .build()
            .create(TmdbDetailsService::class.java)
        client = TmdbDetailsClient(FakeCredentialStore(), service, FakeAppearancePreferences())
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun movieRequestUsesOfficialPathLanguageAndProtectedAuthorizationBoundary() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(movieBody))

        val result = client.load(tmdb("550"), MediaType.MOVIE)
        val request = server.takeRequest()

        assertEquals("/3/movie/550", request.requestUrl?.encodedPath)
        assertEquals("en-US", request.requestUrl?.queryParameter("language"))
        val authorization = request.getHeader("Authorization")
        assertNotNull(authorization)
        assertTrue(authorization?.startsWith("Bearer ") == true)
        assertEquals(TEST_CREDENTIAL.length + "Bearer ".length, authorization?.length)
        assertTrue(result is AppResult.Success)
        assertFalse(result.toString().contains(TEST_CREDENTIAL))
    }

    @Test
    fun tvRequestUsesSeparateOfficialEndpointAndDto() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(tvBody))

        val result = client.load(tmdb("1399"), MediaType.SERIES)
        val request = server.takeRequest()

        assertEquals("/3/tv/1399", request.requestUrl?.encodedPath)
        assertEquals(MediaType.SERIES, (result as AppResult.Success).value.details.mediaType)
    }

    @Test
    fun httpFailuresAndMalformedJsonMapToSafeErrors() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("{}"))
        server.enqueue(MockResponse().setResponseCode(429).setBody("{}"))
        server.enqueue(MockResponse().setResponseCode(503).setBody("{}"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("not-json"))

        assertEquals(AppResult.Failure(AppError.Unauthorized), client.load(tmdb("1"), MediaType.MOVIE))
        assertEquals(AppResult.Failure(AppError.RateLimited), client.load(tmdb("1"), MediaType.MOVIE))
        assertEquals(AppResult.Failure(AppError.RemoteServiceFailure), client.load(tmdb("1"), MediaType.MOVIE))
        assertEquals(AppResult.Failure(AppError.InvalidRemoteResponse), client.load(tmdb("1"), MediaType.MOVIE))
    }

    @Test
    fun unsupportedSourceAndInvalidIdMakeNoRequest() = runTest {
        assertEquals(
            AppResult.Failure(AppError.UnsupportedData),
            client.load(ExternalMediaRef(MediaSource.JIKAN, "550"), MediaType.MOVIE)
        )
        assertEquals(AppResult.Failure(AppError.InvalidInput), client.load(tmdb("bad"), MediaType.MOVIE))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun missingCredentialIsUnauthorizedWithoutRequest() = runTest {
        val service = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(TmdbDetailsService::class.java)
        val noCredential = TmdbDetailsClient(FakeCredentialStore(null), service, FakeAppearancePreferences())

        assertEquals(AppResult.Failure(AppError.Unauthorized), noCredential.load(tmdb("1"), MediaType.MOVIE))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun connectionFailureMapsToNetworkUnavailable() = runTest {
        server.close()
        assertEquals(
            AppResult.Failure(AppError.NetworkUnavailable),
            client.load(tmdb("1"), MediaType.SERIES)
        )
    }

    private fun tmdb(id: String) = ExternalMediaRef(MediaSource.TMDB, id)

    private class FakeCredentialStore(private val credential: TmdbCredential? = TmdbCredential(TEST_CREDENTIAL)) :
        TmdbCredentialStore {
        override suspend fun read(): AppResult<TmdbCredential?> = AppResult.Success(credential)
        override suspend fun save(credential: TmdbCredential): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun delete(): AppResult<Unit> = AppResult.Success(Unit)
    }

    private companion object {
        const val TEST_CREDENTIAL = "unit_test_detail_credential_not_secret"
        val movieBody = """
            {"id":550,"title":"Movie","original_title":"Movie","overview":"Overview",
             "poster_path":"/p.jpg","backdrop_path":"/b.jpg","release_date":"1999-01-01",
             "genres":[{"id":18,"name":"Drama"}],"status":"Released","runtime":120,
             "original_language":"en"}
        """.trimIndent()
        val tvBody = """
            {"id":1399,"name":"TV","original_name":"TV","overview":"Overview",
             "poster_path":null,"backdrop_path":null,"first_air_date":"2011-01-01",
             "genres":[],"status":"Ended","episode_run_time":[50],
             "number_of_seasons":2,"number_of_episodes":20,"original_language":"en"}
        """.trimIndent()
    }

    private class FakeAppearancePreferences : AppearancePreferences {
        override fun observeTheme(): Flow<AppTheme> = flowOf(AppTheme.SYSTEM_DEFAULT)
        override suspend fun setTheme(theme: AppTheme) {}
        override fun observeLanguage(): Flow<AppLanguage> = flowOf(AppLanguage.ENGLISH)
        override suspend fun setLanguage(language: AppLanguage) {}
        override suspend fun getEffectiveTmdbLanguage(): String = "en-US"
    }
}
