package com.cydoniancitizen.bingee.data.library

import com.cydoniancitizen.bingee.core.model.AnimeFormat
import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.LibraryEntry
import com.cydoniancitizen.bingee.core.model.LibraryProgress
import com.cydoniancitizen.bingee.core.model.MediaSearchResult
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.data.jikan.search.JikanAnimeSearchResultDto
import com.cydoniancitizen.bingee.data.jikan.search.JikanSearchMapper
import com.cydoniancitizen.bingee.domain.model.AnimeFormatClassifier
import com.cydoniancitizen.bingee.data.settings.ProfileCategory
import com.cydoniancitizen.bingee.feature.profile.belongsToCategory
import com.cydoniancitizen.bingee.feature.profile.isWatched
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JikanWatchLaterLibraryTest {

    @Test
    fun jikanMovieToWatchLaterMovies() {
        val dto = JikanAnimeSearchResultDto(
            malId = 5001,
            title = "Jikan Movie Title",
            titleEnglish = "Jikan Movie English",
            titleJapanese = "劇場版アニメ",
            type = "Movie"
        )
        val searchResult = JikanSearchMapper.mapResult(dto)
        assertNotNull(searchResult)
        assertEquals(MediaSource.JIKAN, searchResult!!.externalRef.source)
        assertEquals("5001", searchResult.externalRef.externalId)
        assertEquals(MediaType.MOVIE, searchResult.mediaType)
        assertEquals(AnimeFormat.MOVIE, searchResult.animeFormat)

        val now = Instant.now()
        val libraryEntry = LibraryEntry(
            mediaRef = searchResult.externalRef,
            mediaType = searchResult.mediaType,
            title = searchResult.title,
            originalTitle = searchResult.originalTitle,
            posterUrl = searchResult.posterUrl,
            releaseDate = searchResult.releaseDate,
            overview = searchResult.overview,
            addedAt = now,
            progress = LibraryProgress.Unavailable,
            personalRating = null,
            isFavorite = false,
            watchedDate = null,
            inLibrary = true
        )

        assertEquals(MediaSource.JIKAN, libraryEntry.mediaRef.source)
        assertEquals("5001", libraryEntry.mediaRef.externalId)
        assertEquals(MediaType.MOVIE, libraryEntry.mediaType)
        assertFalse(libraryEntry.isWatched())
        assertNull(libraryEntry.watchedDate)
        assertFalse(libraryEntry.isFavorite)
        assertTrue(libraryEntry.belongsToCategory(ProfileCategory.MOVIES))
        assertFalse(libraryEntry.belongsToCategory(ProfileCategory.TV_SERIES))
    }

    @Test
    fun jikanSeriesToWatchLaterTVSeries() {
        val dto = JikanAnimeSearchResultDto(
            malId = 5002,
            title = "Jikan TV Title",
            titleEnglish = null,
            titleJapanese = "テレビアニメ",
            type = "TV"
        )
        val searchResult = JikanSearchMapper.mapResult(dto)
        assertNotNull(searchResult)
        assertEquals(MediaSource.JIKAN, searchResult!!.externalRef.source)
        assertEquals("5002", searchResult.externalRef.externalId)
        assertEquals(MediaType.SERIES, searchResult.mediaType)
        assertEquals(AnimeFormat.TV, searchResult.animeFormat)

        val now = Instant.now()
        val libraryEntry = LibraryEntry(
            mediaRef = searchResult.externalRef,
            mediaType = searchResult.mediaType,
            title = searchResult.title,
            originalTitle = searchResult.originalTitle,
            posterUrl = searchResult.posterUrl,
            releaseDate = searchResult.releaseDate,
            overview = searchResult.overview,
            addedAt = now,
            progress = LibraryProgress.Unavailable,
            personalRating = null,
            isFavorite = false,
            watchedDate = null,
            inLibrary = true
        )

        assertEquals(MediaSource.JIKAN, libraryEntry.mediaRef.source)
        assertEquals("5002", libraryEntry.mediaRef.externalId)
        assertEquals(MediaType.SERIES, libraryEntry.mediaType)
        assertFalse(libraryEntry.isWatched())
        assertNull(libraryEntry.watchedDate)
        assertFalse(libraryEntry.isFavorite)
        assertTrue(libraryEntry.belongsToCategory(ProfileCategory.TV_SERIES))
        assertFalse(libraryEntry.belongsToCategory(ProfileCategory.MOVIES))
    }

    @Test
    fun onaOvaSpecialClassification() {
        val formats = mapOf(
            "ONA" to AnimeFormat.ONA,
            "OVA" to AnimeFormat.OVA,
            "Special" to AnimeFormat.SPECIAL,
            "TV Special" to AnimeFormat.TV_SPECIAL
        )

        formats.forEach { (rawType, expectedFormat) ->
            val parsedFormat = AnimeFormatClassifier.parseFormat(rawType)
            assertEquals(expectedFormat, parsedFormat)
            val mediaType = AnimeFormatClassifier.toMediaType(parsedFormat)
            assertEquals(MediaType.SERIES, mediaType)

            val dto = JikanAnimeSearchResultDto(
                malId = 6000,
                title = "Anime $rawType",
                type = rawType
            )
            val result = JikanSearchMapper.mapResult(dto)
            assertNotNull(result)
            assertEquals(MediaType.SERIES, result!!.mediaType)
            assertEquals(expectedFormat, result.animeFormat)
        }
    }

    @Test
    fun providerIdentityPreservedAndNoWatchedOrFavoriteStateCreated() {
        val dto = JikanAnimeSearchResultDto(
            malId = 9999,
            title = "Identity Preservation Test",
            type = "TV"
        )
        val searchResult = JikanSearchMapper.mapResult(dto)!!
        assertEquals(MediaSource.JIKAN, searchResult.externalRef.source)
        assertEquals("9999", searchResult.externalRef.externalId)

        val entry = LibraryEntry(
            mediaRef = searchResult.externalRef,
            mediaType = searchResult.mediaType,
            title = searchResult.title,
            originalTitle = null,
            posterUrl = null,
            releaseDate = LocalDate.of(2024, 1, 1),
            overview = "Overview",
            addedAt = Instant.now(),
            progress = LibraryProgress.Unavailable,
            personalRating = null,
            isFavorite = false,
            watchedDate = null,
            inLibrary = true
        )

        assertEquals(MediaSource.JIKAN, entry.mediaRef.source)
        assertEquals("9999", entry.mediaRef.externalId)
        assertFalse(entry.isWatched())
        assertNull(entry.watchedDate)
        assertFalse(entry.isFavorite)
    }

    @Test
    fun repeatedAddIsIdempotent() {
        val ref = ExternalMediaRef(MediaSource.JIKAN, "777")
        val set = mutableSetOf<ExternalMediaRef>()

        val addedFirst = set.add(ref)
        val addedSecond = set.add(ref)

        assertTrue(addedFirst)
        assertFalse(addedSecond)
        assertEquals(1, set.size)
        assertTrue(set.contains(ref))
    }

    @Test
    fun removalFromWatchLaterPreservesMetadataNeededForDetails() {
        val ref = ExternalMediaRef(MediaSource.JIKAN, "888")
        val now = Instant.now()
        val entry = LibraryEntry(
            mediaRef = ref,
            mediaType = MediaType.SERIES,
            title = "Jikan Series Title",
            originalTitle = "原題",
            posterUrl = "https://example.com/poster.jpg",
            releaseDate = LocalDate.of(2023, 4, 1),
            overview = "Detailed overview text",
            addedAt = now,
            progress = LibraryProgress.Unavailable,
            personalRating = null,
            isFavorite = false,
            watchedDate = null,
            inLibrary = true
        )

        val removedEntry = entry.copy(inLibrary = false)

        assertFalse(removedEntry.inLibrary)
        assertEquals(ref, removedEntry.mediaRef)
        assertEquals(MediaSource.JIKAN, removedEntry.mediaRef.source)
        assertEquals("888", removedEntry.mediaRef.externalId)
        assertEquals("Jikan Series Title", removedEntry.title)
        assertEquals("原題", removedEntry.originalTitle)
        assertEquals("https://example.com/poster.jpg", removedEntry.posterUrl)
        assertEquals(LocalDate.of(2023, 4, 1), removedEntry.releaseDate)
        assertEquals("Detailed overview text", removedEntry.overview)
    }
}
