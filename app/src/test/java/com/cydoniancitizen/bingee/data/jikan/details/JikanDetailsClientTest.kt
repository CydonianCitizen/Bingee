package com.cydoniancitizen.bingee.data.jikan.details

import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.result.AppError
import com.cydoniancitizen.bingee.core.result.AppResult
import com.cydoniancitizen.bingee.data.jikan.JikanDelay
import com.cydoniancitizen.bingee.data.jikan.JikanRequestGate
import com.cydoniancitizen.bingee.data.jikan.search.JikanSearchService
import com.google.gson.Gson
import java.time.Clock
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class JikanDetailsClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: JikanDetailsClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val service = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(OkHttpClient())
            .addConverterFactory(GsonConverterFactory.create(Gson()))
            .build()
            .create(JikanSearchService::class.java)
        client = JikanDetailsClient(service, JikanRequestGate(Clock.systemUTC(), JikanDelay {}))
    }

    @After
    fun tearDown() = server.close()

    @Test
    fun fullDetailsUseIsolatedEndpointWithoutAuthorization() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"data":{"mal_id":52991,"title":"Frieren","type":"TV","status":"Finished Airing","relations":[]}}"""
            )
        )

        val result = client.load(ExternalMediaRef(MediaSource.JIKAN, "52991"))
        val request = server.takeRequest()

        assertEquals("/anime/52991/full", request.requestUrl?.encodedPath)
        assertNull(request.getHeader("Authorization"))
        assertEquals("52991", (result as AppResult.Success).value.externalRef.externalId)
    }

    @Test
    fun rateLimitMalformedAndIdentityMismatchAreProviderLocalFailures() = runTest {
        server.enqueue(MockResponse().setResponseCode(429).setBody("private"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("not-json"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"data":{"mal_id":2,"title":"Other"}}"""))
        val ref = ExternalMediaRef(MediaSource.JIKAN, "1")

        assertEquals(AppResult.Failure(AppError.RateLimited), client.load(ref))
        assertEquals(AppResult.Failure(AppError.InvalidRemoteResponse), client.load(ref))
        assertEquals(AppResult.Failure(AppError.InvalidRemoteResponse), client.load(ref))
        assertEquals(
            AppResult.Failure(AppError.InvalidInput),
            client.load(ExternalMediaRef(MediaSource.TMDB, "1"))
        )
    }
}
