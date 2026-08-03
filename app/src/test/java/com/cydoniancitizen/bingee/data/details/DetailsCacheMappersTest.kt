package com.cydoniancitizen.bingee.data.details

import com.cydoniancitizen.bingee.core.model.CacheFreshness
import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.Genre
import com.cydoniancitizen.bingee.core.model.MediaDetails
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.core.model.ProductionStatus
import com.cydoniancitizen.bingee.data.library.local.CachedDetailsRelation
import com.cydoniancitizen.bingee.data.library.local.ExternalRefEntity
import com.cydoniancitizen.bingee.data.library.local.MediaDetailsEntity
import com.cydoniancitizen.bingee.data.library.local.MediaEntity
import com.cydoniancitizen.bingee.data.library.local.MediaGenreEntity
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DetailsCacheMappersTest {
    private val now = Instant.parse("2026-08-03T12:00:00Z")
    private val ref = ExternalMediaRef(MediaSource.TMDB, "550")
    private val policy = CacheFreshnessPolicy(Clock.fixed(now, ZoneOffset.UTC))

    @Test
    fun canonicalRowWithoutDetailsIsNoCache() {
        assertNull(relation(details = null).toDomain(ref, policy))
    }

    @Test
    fun movieCacheMapsOptionalMetadataStatusAndOrderedGenres() {
        val cached = requireNotNull(
            relation(
                details = detailEntity(
                    status = ProductionStatus.RELEASED.name,
                    runtime = 121
                ),
                genres = listOf(
                    MediaGenreEntity(1, 2, "Third"),
                    MediaGenreEntity(1, 0, "First"),
                    MediaGenreEntity(1, 1, "Second")
                )
            ).toDomain(ref, policy)
        )

        assertEquals(MediaType.MOVIE, cached.details.mediaType)
        assertEquals(Duration.ofMinutes(121), cached.details.runtime)
        assertEquals(listOf("First", "Second", "Third"), cached.details.genres.map { it.name })
        assertEquals(ProductionStatus.RELEASED, cached.details.productionStatus)
        assertEquals(CacheFreshness.FRESH, cached.freshness)
    }

    @Test
    fun malformedPersistedStatusAndCountsDegradeSafely() {
        val cached = requireNotNull(
            relation(
                mediaType = MediaType.SERIES,
                details = detailEntity(
                    status = "provider-body-value",
                    episodeRuntime = -1,
                    seasons = -2,
                    episodes = 0
                )
            ).toDomain(ref, policy)
        )

        assertEquals(ProductionStatus.UNKNOWN, cached.details.productionStatus)
        assertNull(cached.details.episodeRuntime)
        assertNull(cached.details.numberOfSeasons)
        assertEquals(0, cached.details.numberOfEpisodes)
    }

    @Test
    fun domainWriteStoresNoLocalIdAndKeepsTimestampSeparateFromDomain() {
        val details = MediaDetails(
            externalRef = ref,
            mediaType = MediaType.MOVIE,
            title = " Movie ",
            runtime = Duration.ofMinutes(90),
            productionStatus = ProductionStatus.RELEASED,
            genres = listOf(Genre("Drama"), Genre("Comedy"))
        )

        val write = details.toCacheWrite(now)

        assertEquals(0, write.media.localMediaId)
        assertEquals(0, write.details.localMediaId)
        assertEquals(now, write.details.detailsFetchedAt)
        assertEquals(listOf(0, 1), write.genres.map { it.genreOrder })
        assertEquals(listOf("Drama", "Comedy"), write.genres.map { it.name })
    }

    private fun relation(
        mediaType: MediaType = MediaType.MOVIE,
        details: MediaDetailsEntity?,
        genres: List<MediaGenreEntity> = emptyList()
    ) = CachedDetailsRelation(
        media = MediaEntity(
            localMediaId = 1,
            mediaType = mediaType,
            title = "Cached",
            originalTitle = null,
            overview = null,
            posterUrl = null,
            releaseDate = LocalDate.of(2020, 1, 1),
            createdAt = now,
            metadataUpdatedAt = now
        ),
        details = details,
        genres = genres,
        externalRefs = listOf(ExternalRefEntity(1, MediaSource.TMDB, "550"))
    )

    private fun detailEntity(
        status: String,
        runtime: Int? = null,
        episodeRuntime: Int? = null,
        seasons: Int? = null,
        episodes: Int? = null
    ) = MediaDetailsEntity(
        localMediaId = 1,
        backdropUrl = null,
        productionStatus = status,
        originalLanguage = null,
        runtimeMinutes = runtime,
        episodeRuntimeMinutes = episodeRuntime,
        numberOfSeasons = seasons,
        numberOfEpisodes = episodes,
        detailsFetchedAt = now.minusSeconds(60)
    )
}
