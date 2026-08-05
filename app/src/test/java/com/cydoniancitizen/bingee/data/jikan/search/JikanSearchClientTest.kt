package com.cydoniancitizen.bingee.data.jikan.search

import com.cydoniancitizen.bingee.core.model.MediaSearchCategory
import com.cydoniancitizen.bingee.core.model.MediaSearchQuery
import com.cydoniancitizen.bingee.core.result.AppError
import com.cydoniancitizen.bingee.core.result.AppResult
import com.cydoniancitizen.bingee.data.jikan.JikanDelay
import com.cydoniancitizen.bingee.data.jikan.JikanRequestGate
import com.google.gson.Gson
import java.time.Clock
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class JikanSearchClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: JikanSearchClient

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
        client = JikanSearchClient(service, JikanRequestGate(Clock.systemUTC(), JikanDelay {}))
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun searchUsesJikanPathPaginationSfwAndNoAuthorization() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))

        val result = client.search(MediaSearchQuery("Frieren", MediaSearchCategory.ANIME, page = 2))
        val request = server.takeRequest()

        assertEquals("/anime", request.requestUrl?.encodedPath)
        assertEquals("Frieren", request.requestUrl?.queryParameter("q"))
        assertEquals("2", request.requestUrl?.queryParameter("page"))
        assertEquals("true", request.requestUrl?.queryParameter("sfw"))
        assertNull(request.getHeader("Authorization"))
        assertEquals("52991", (result as AppResult.Success).value.results.single().externalRef.externalId)
    }

    @Test
    fun rateLimitAndMalformedPayloadMapWithoutBodyLeakage() = runTest {
        server.enqueue(MockResponse().setResponseCode(429).setBody("private provider response"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("not-json"))
        val query = MediaSearchQuery("Frieren", MediaSearchCategory.ANIME)

        assertEquals(AppResult.Failure(AppError.RateLimited), client.search(query))
        val malformed = client.search(query)
        assertEquals(AppResult.Failure(AppError.InvalidRemoteResponse), malformed)
        assertFalse(malformed.toString().contains("private provider response"))
    }

    private companion object {
        val body = """
            {"data":[{"mal_id":52991,"title":"Sousou no Frieren","images":{"jpg":{"image_url":"https://image/frieren.jpg"}}}],"pagination":{"last_visible_page":3,"has_next_page":true}}
        """.trimIndent()
    }
}
