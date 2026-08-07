package com.cydoniancitizen.bingee.data.library

import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.MediaSearchResult
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.data.library.local.ExternalRefEntity
import com.cydoniancitizen.bingee.data.library.local.LibraryItemWithRefs
import com.cydoniancitizen.bingee.data.library.local.MediaEntity
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LibraryMappersTest {
    private val now = Instant.parse("2026-08-01T10:00:00Z")

    @Test
    fun searchResultNormalizesListMetadataWithoutAddingDetailFields() {
        val entity =
            MediaSearchResult(
                externalRef = ExternalMediaRef(MediaSource.TMDB, "42"),
                mediaType = MediaType.MOVIE,
                title = "  Arrival  ",
                originalTitle = "  ",
                posterUrl = " https://image.example/42.jpg ",
                releaseDate = LocalDate.of(2016, 11, 11),
                overview = "  First contact.  "
            ).toMediaEntity(now)

        assertEquals("Arrival", entity.title)
        assertNull(entity.originalTitle)
        assertEquals("https://image.example/42.jpg", entity.posterUrl)
        assertEquals("First contact.", entity.overview)
        assertEquals(now, entity.createdAt)
        assertEquals(now, entity.metadataUpdatedAt)
    }

    @Test
    fun roomRelationMapsToDomainAndHonorsRequestedProviderRef() {
        val media = mediaEntity()
        val tmdb = ExternalRefEntity(7, MediaSource.TMDB, "42")
        val imdb = ExternalRefEntity(7, MediaSource.IMDB, "84")
        val row = LibraryItemWithRefs(
            media = media,
            addedAt = now,
            inLibrary = true,
            externalRefs = listOf(imdb, tmdb)
        )

        val entry = row.toDomain(preferredRef = tmdb.toDomain())

        assertEquals(ExternalMediaRef(MediaSource.TMDB, "42"), entry.mediaRef)
        assertEquals("Arrival", entry.title)
        assertEquals(MediaType.MOVIE, entry.mediaType)
        assertEquals(now, entry.addedAt)
    }

    @Test(expected = IllegalArgumentException::class)
    fun relationWithoutExternalIdentityFailsSafely() {
        LibraryItemWithRefs(
            media = mediaEntity(),
            addedAt = now,
            inLibrary = true,
            externalRefs = emptyList()
        ).toDomain()
    }

    private fun mediaEntity() = MediaEntity(
        localMediaId = 7,
        mediaType = MediaType.MOVIE,
        title = "Arrival",
        originalTitle = null,
        overview = null,
        posterUrl = null,
        releaseDate = LocalDate.of(2016, 11, 11),
        createdAt = now,
        metadataUpdatedAt = now
    )
}
