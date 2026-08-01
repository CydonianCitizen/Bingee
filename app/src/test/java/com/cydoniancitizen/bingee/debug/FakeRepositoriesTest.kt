package com.cydoniancitizen.bingee.debug

import com.cydoniancitizen.bingee.core.model.LibraryEntry
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.core.result.AppError
import com.cydoniancitizen.bingee.core.result.AppResult
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeRepositoriesTest {
    @Test
    fun mediaRepositoryReturnsFixedSuccessWithoutDelay() = runTest {
        val repository = FakeMediaRepository()

        val result = repository.search("fixed")

        assertEquals(AppResult.Success(FakeMediaData.searchResults), result)
        assertEquals(listOf("fixed"), repository.searchQueries)
    }

    @Test
    fun mediaRepositoryReturnsConfiguredFailureWithoutDelay() = runTest {
        val repository = FakeMediaRepository.failing(AppError.NetworkUnavailable)

        val result = repository.search("fixed")

        assertEquals(AppResult.Failure(AppError.NetworkUnavailable), result)
    }

    @Test
    fun libraryRepositoryEmitsDeterministicAddAndRemove() = runTest {
        val repository = FakeLibraryRepository()
        val entry =
            LibraryEntry(
                mediaRef = FakeMediaData.movieRef,
                mediaType = MediaType.MOVIE,
                addedAt = Instant.parse("2026-01-02T03:04:05Z")
            )

        assertEquals(AppResult.Success(Unit), repository.add(entry))
        assertEquals(listOf(entry), repository.observeEntries().first())
        assertEquals(entry, repository.observeEntry(entry.mediaRef).first())

        assertEquals(AppResult.Success(Unit), repository.remove(entry.mediaRef))
        assertTrue(repository.observeEntries().first().isEmpty())
    }
}
