package com.cydoniancitizen.bingee.data.jikan.search

import com.cydoniancitizen.bingee.core.common.TestingAnimeFeatureAvailability
import com.cydoniancitizen.bingee.core.model.MediaSearchCategory
import com.cydoniancitizen.bingee.core.model.MediaSearchQuery
import com.cydoniancitizen.bingee.core.result.AppError
import com.cydoniancitizen.bingee.core.result.AppResult
import com.cydoniancitizen.bingee.data.jikan.JikanDelay
import com.cydoniancitizen.bingee.data.jikan.JikanRequestGate
import java.time.Clock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Retrofit

class DefaultAnimeRepositoryTest {
    @Test
    fun searchReturnsFeatureUnavailableWhenDisabled() = runTest {
        val dummyService = Retrofit.Builder()
            .baseUrl("https://localhost/")
            .build()
            .create(JikanSearchService::class.java)
        val dummyClient = JikanSearchClient(dummyService, JikanRequestGate(Clock.systemUTC(), JikanDelay {}))
        val repository = DefaultAnimeRepository(
            client = dummyClient,
            availability = TestingAnimeFeatureAvailability(isAvailable = false)
        )

        val query = MediaSearchQuery.from("Naruto", MediaSearchCategory.ANIME)!!
        val result = repository.search(query)

        assertTrue(result is AppResult.Failure)
        assertEquals(AppError.FeatureUnavailable, (result as AppResult.Failure).error)
    }
}
