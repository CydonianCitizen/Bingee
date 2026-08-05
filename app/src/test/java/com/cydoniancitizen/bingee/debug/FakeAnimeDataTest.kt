package com.cydoniancitizen.bingee.debug

import com.cydoniancitizen.bingee.core.model.AnimeFormat
import com.cydoniancitizen.bingee.core.model.AnimeProgressState
import com.cydoniancitizen.bingee.core.model.AnimeStatus
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.core.result.AppError
import com.cydoniancitizen.bingee.core.result.AppResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeAnimeDataTest {
    @Test
    fun searchFixturesCoverSuccessEmptyAndProviderFailures() {
        assertEquals(MediaType.ANIME, FakeAnimeData.searchResult.mediaType)
        assertEquals(MediaSource.JIKAN, FakeAnimeData.searchResult.externalRef.source)
        assertEquals(2, FakeAnimeData.searchPage.results.size)
        assertTrue(FakeAnimeData.emptySearch.results.isEmpty())
        assertEquals(
            AppResult.Failure(AppError.NetworkUnavailable),
            FakeAnimeData.networkUnavailable
        )
        assertEquals(AppResult.Failure(AppError.RateLimited), FakeAnimeData.rateLimited)
    }

    @Test
    fun detailFixturesCoverCacheOptionalMetadataEnumsAndFormats() {
        assertEquals(AnimeStatus.AIRING, FakeAnimeData.ongoingAnime.status)
        assertEquals(AnimeStatus.FINISHED, FakeAnimeData.completedAnime.status)
        assertEquals(AnimeFormat.MOVIE, FakeAnimeData.movieAnime.format)
        assertEquals(AnimeFormat.UNKNOWN, FakeAnimeData.unknownFormat.format)
        assertEquals(AnimeStatus.UNKNOWN, FakeAnimeData.unknownStatus.status)
        assertNull(FakeAnimeData.missingOptionalFields.englishTitle)
        assertNull(FakeAnimeData.unknownTotalDetails.episodeCount)
        assertEquals(2, FakeAnimeData.relatedAnime.relations.size)
        assertEquals(2, FakeAnimeData.cachedDetails.details.relations.size)
        assertNull(FakeAnimeData.staleDetails.details.providerScore)
    }

    @Test
    fun progressLibraryIdentityRatingAndPremiereFixturesStayQualified() {
        assertEquals(AnimeProgressState.IN_PROGRESS, FakeAnimeData.knownTotalProgress.state(12))
        assertEquals(AnimeProgressState.IN_PROGRESS, FakeAnimeData.unknownTotalProgress.state(null))
        assertEquals(AnimeProgressState.COMPLETED, FakeAnimeData.completedProgress.state(12))
        assertEquals(AnimeProgressState.COMPLETED, FakeAnimeData.movieProgress.state(1))
        assertNotEquals(FakeAnimeData.collisionTmdbRef, FakeAnimeData.collisionJikanRef)
        assertEquals(MediaSource.TMDB, FakeAnimeData.collisionTmdbRef.source)
        assertEquals(MediaSource.JIKAN, FakeAnimeData.collisionJikanRef.source)
        assertEquals(MediaType.ANIME, FakeAnimeData.animePremiere.mediaType)
        assertEquals(2, FakeAnimeData.mixedLibraryEntries.count { it.mediaType == MediaType.ANIME })
        assertEquals(8, FakeAnimeData.localRating.value)
    }
}
