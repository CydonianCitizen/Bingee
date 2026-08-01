package com.cydoniancitizen.bingee.debug

import com.cydoniancitizen.bingee.core.model.LibraryEntry
import com.cydoniancitizen.bingee.core.model.MediaSearchCategory
import com.cydoniancitizen.bingee.core.model.MediaSearchQuery
import com.cydoniancitizen.bingee.core.model.MediaSearchResult
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.core.result.AppError
import com.cydoniancitizen.bingee.core.result.AppResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class FakeRepositoriesTest {
    @Test
    fun mediaRepositoryReturnsFixedSuccessWithoutDelay() = runTest {
        val repository = FakeMediaRepository()

        val query = MediaSearchQuery("fixed", MediaSearchCategory.MOVIES)
        val result = repository.search(query)

        assertEquals(AppResult.Success(FakeMediaData.firstPage), result)
        assertEquals(listOf(query), repository.searchQueries)
    }

    @Test
    fun mediaRepositoryReturnsConfiguredFailureWithoutDelay() = runTest {
        val repository = FakeMediaRepository.failing(AppError.NetworkUnavailable)

        val result =
            repository.search(MediaSearchQuery("fixed", MediaSearchCategory.MOVIES))

        assertEquals(AppResult.Failure(AppError.NetworkUnavailable), result)
    }

    @Test
    fun libraryRepositoryEmitsDeterministicAddAndRemove() = runTest {
        val repository = FakeLibraryRepository()
        val searchResult =
            MediaSearchResult(
                externalRef = FakeMediaData.movieRef,
                mediaType = MediaType.MOVIE,
                title = "Fixed movie"
            )

        val added = repository.add(searchResult) as AppResult.Success
        val entry = added.value
        assertEquals(AppResult.Success(listOf(entry)), repository.observeEntries().first())
        assertEquals(AppResult.Success(entry), repository.observeEntry(entry.mediaRef).first())

        assertEquals(AppResult.Success(Unit), repository.remove(entry.mediaRef))
        assertEquals(AppResult.Success(emptyList<LibraryEntry>()), repository.observeEntries().first())
    }
}
