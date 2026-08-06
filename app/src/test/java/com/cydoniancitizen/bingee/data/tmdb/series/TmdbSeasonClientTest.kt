package com.cydoniancitizen.bingee.data.tmdb.series

import com.cydoniancitizen.bingee.core.credential.TmdbCredential
import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.MediaSource
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class TmdbSeasonClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: TmdbSeasonClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val service = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(OkHttpClient())
            .addConverterFactory(GsonConverterFactory.create(Gson()))
            .build()
            .create(TmdbSeasonService::class.java)
        client = TmdbSeasonClient(FakeCredentialStore(), service, FakeAppearancePreferences())
    }

    @After
    fun tearDown() = server.close()

    @Test
    fun seasonRequestUsesExactPathLanguageAndNoAppendedResources() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(SUCCESS_BODY))

        val result = client.load(tmdb("1399"), 0)
        val request = server.takeRequest()

        assertEquals("/3/tv/1399/season/0", request.requestUrl?.encodedPath)
        assertEquals("en-US", request.requestUrl?.queryParameter("language"))
        assertEquals(null, request.requestUrl?.queryParameter("append_to_response"))
        assertTrue(request.getHeader("Authorization")?.startsWith("Bearer ") == true)
        assertTrue(result is AppResult.Success)
        assertEquals(0, (result as AppResult.Success).value.season.seasonNumber)
        assertEquals(1, result.value.episodes.size)
        assertFalse(result.toString().contains(TEST_CREDENTIAL))
    }

    @Test
    fun emptySeasonAndSafeHttpFailuresMapWithoutDtoLeakage() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(EMPTY_BODY))
        server.enqueue(MockResponse().setResponseCode(401).setBody("{}"))
        server.enqueue(MockResponse().setResponseCode(429).setBody("{}"))
        server.enqueue(MockResponse().setResponseCode(503).setBody("{}"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("not-json"))

        assertEquals(0, (client.load(tmdb("1"), 1) as AppResult.Success).value.episodes.size)
        assertEquals(AppResult.Failure(AppError.Unauthorized), client.load(tmdb("1"), 1))
        assertEquals(AppResult.Failure(AppError.RateLimited), client.load(tmdb("1"), 1))
        assertEquals(AppResult.Failure(AppError.RemoteServiceFailure), client.load(tmdb("1"), 1))
        assertEquals(AppResult.Failure(AppError.InvalidRemoteResponse), client.load(tmdb("1"), 1))
    }

    @Test
    fun unsupportedInputAndMissingCredentialDoNotCallService() = runTest {
        assertEquals(
            AppResult.Failure(AppError.UnsupportedData),
            client.load(ExternalMediaRef(MediaSource.JIKAN, "1"), 1)
        )
        assertEquals(AppResult.Failure(AppError.InvalidInput), client.load(tmdb("bad"), 1))
        assertEquals(AppResult.Failure(AppError.InvalidInput), client.load(tmdb("1"), -1))
        val noCredential = TmdbSeasonClient(FakeCredentialStore(null), service(), FakeAppearancePreferences())
        assertEquals(AppResult.Failure(AppError.Unauthorized), noCredential.load(tmdb("1"), 1))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun connectionFailureMapsToNetworkUnavailable() = runTest {
        server.close()
        assertEquals(AppResult.Failure(AppError.NetworkUnavailable), client.load(tmdb("1"), 1))
    }

    private fun service(): TmdbSeasonService = Retrofit.Builder()
        .baseUrl(server.url("/"))
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(TmdbSeasonService::class.java)

    private fun tmdb(id: String) = ExternalMediaRef(MediaSource.TMDB, id)

    private class FakeCredentialStore(private val value: TmdbCredential? = TmdbCredential(TEST_CREDENTIAL)) :
        TmdbCredentialStore {
        override suspend fun read(): AppResult<TmdbCredential?> = AppResult.Success(value)
        override suspend fun save(credential: TmdbCredential): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun delete(): AppResult<Unit> = AppResult.Success(Unit)
    }

    private companion object {
        const val TEST_CREDENTIAL = "unit_test_season_credential_not_secret"
        const val SUCCESS_BODY =
            """{"id":90,"season_number":0,"name":"Specials","episodes":[{"id":101,"season_number":0,"episode_number":1,"name":"Special","air_date":null}]}"""
        const val EMPTY_BODY = """{"id":91,"season_number":1,"name":"Season 1","episodes":[]}"""
    }

    private class FakeAppearancePreferences : AppearancePreferences {
        override fun observeTheme(): Flow<AppTheme> = flowOf(AppTheme.SYSTEM_DEFAULT)
        override suspend fun setTheme(theme: AppTheme) {}
        override fun observeLanguage(): Flow<AppLanguage> = flowOf(AppLanguage.ENGLISH)
        override suspend fun setLanguage(language: AppLanguage) {}
        override suspend fun getEffectiveTmdbLanguage(): String = "en-US"
    }
}
