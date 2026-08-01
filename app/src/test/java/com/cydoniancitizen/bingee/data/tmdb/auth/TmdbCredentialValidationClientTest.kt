package com.cydoniancitizen.bingee.data.tmdb.auth

import com.cydoniancitizen.bingee.core.credential.TmdbCredential
import com.cydoniancitizen.bingee.core.result.AppError
import com.cydoniancitizen.bingee.core.result.AppResult
import com.google.gson.Gson
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

class TmdbCredentialValidationClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: TmdbCredentialValidationClient

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
                .create(TmdbAuthenticationService::class.java)
        client = TmdbCredentialValidationClient(service)
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun successfulValidationAttachesBearerCredentialWithoutExposingIt() = kotlinx.coroutines.test.runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{\"success\":true}"))

        val result = client.validate(TmdbCredential(FAKE_CREDENTIAL))
        val request = server.takeRequest()
        val authorization = request.getHeader("Authorization")

        assertEquals(AppResult.Success(Unit), result)
        assertTrue(authorization?.startsWith("Bearer ") == true)
        assertTrue(authorization?.length == FAKE_CREDENTIAL.length + "Bearer ".length)
        assertFalse(result.toString().contains(FAKE_CREDENTIAL))
    }

    @Test
    fun unauthorizedCredentialIsRejectedSafely() = kotlinx.coroutines.test.runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("{\"status_code\":7}"))

        val result = client.validate(TmdbCredential(FAKE_CREDENTIAL))

        assertEquals(AppResult.Failure(AppError.Unauthorized), result)
        assertFalse(result.toString().contains(FAKE_CREDENTIAL))
    }

    @Test
    fun rateLimitAndServerFailureRemainDistinct() = kotlinx.coroutines.test.runTest {
        server.enqueue(MockResponse().setResponseCode(429))
        server.enqueue(MockResponse().setResponseCode(503))

        assertEquals(
            AppResult.Failure(AppError.RateLimited),
            client.validate(TmdbCredential(FAKE_CREDENTIAL))
        )
        assertEquals(
            AppResult.Failure(AppError.RemoteServiceFailure),
            client.validate(TmdbCredential(FAKE_CREDENTIAL))
        )
    }

    @Test
    fun malformedSuccessfulResponseIsSafeAndRetryable() = kotlinx.coroutines.test.runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("not-json"))

        val result = client.validate(TmdbCredential(FAKE_CREDENTIAL))

        assertEquals(AppResult.Failure(AppError.InvalidRemoteResponse), result)
        assertFalse(result.toString().contains(FAKE_CREDENTIAL))
    }

    @Test
    fun connectionFailureMapsToNetworkUnavailable() = kotlinx.coroutines.test.runTest {
        server.close()

        val result = client.validate(TmdbCredential(FAKE_CREDENTIAL))

        assertEquals(AppResult.Failure(AppError.NetworkUnavailable), result)
        assertFalse(result.toString().contains(FAKE_CREDENTIAL))
    }

    private companion object {
        const val FAKE_CREDENTIAL = "fake_test-token.private"
    }
}
