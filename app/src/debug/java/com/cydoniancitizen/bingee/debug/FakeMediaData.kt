package com.cydoniancitizen.bingee.debug

import com.cydoniancitizen.bingee.core.model.CacheFreshness
import com.cydoniancitizen.bingee.core.model.CachedMediaDetails
import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.Genre
import com.cydoniancitizen.bingee.core.model.MediaDetails
import com.cydoniancitizen.bingee.core.model.MediaSearchPage
import com.cydoniancitizen.bingee.core.model.MediaSearchResult
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.core.model.ProductionStatus
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

object FakeMediaData {
    val movieRef = ExternalMediaRef(MediaSource.TMDB, "550")
    val seriesRef = ExternalMediaRef(MediaSource.TMDB, "1399")
    val fixedNow: Instant = Instant.parse("2026-08-03T12:00:00Z")

    val movieDetails = MediaDetails(
        externalRef = movieRef,
        mediaType = MediaType.MOVIE,
        title = "Fixed Test Movie With a Long Accessible Title",
        originalTitle = "Original Fixed Movie",
        overview = "A long deterministic overview used for previews without network, Room, or credentials. ".repeat(4),
        posterUrl = "https://image.tmdb.org/t/p/w500/fixed-movie.jpg",
        backdropUrl = "https://image.tmdb.org/t/p/w780/fixed-backdrop.jpg",
        releaseDate = LocalDate.of(2024, 1, 15),
        productionStatus = ProductionStatus.RELEASED,
        originalLanguage = "en",
        runtime = Duration.ofMinutes(121),
        genres = listOf(Genre("Drama"), Genre("Science Fiction"))
    )

    val seriesDetails = MediaDetails(
        externalRef = seriesRef,
        mediaType = MediaType.SERIES,
        title = "Fixed Test Series",
        overview = "Series overview",
        releaseDate = LocalDate.of(2023, 4, 2),
        productionStatus = ProductionStatus.RETURNING_SERIES,
        originalLanguage = "en",
        episodeRuntime = Duration.ofMinutes(52),
        numberOfSeasons = 4,
        numberOfEpisodes = 36,
        genres = listOf(Genre("Drama"))
    )

    val freshMovieDetails = CachedMediaDetails(movieDetails, fixedNow, CacheFreshness.FRESH)
    val staleSeriesDetails = CachedMediaDetails(
        seriesDetails,
        fixedNow.minusSeconds(25 * 60 * 60),
        CacheFreshness.STALE
    )

    val searchResults =
        listOf(
            MediaSearchResult(
                externalRef = movieRef,
                mediaType = MediaType.MOVIE,
                title = "Fixed Test Movie",
                originalTitle = "Original Fixed Movie",
                posterUrl = "https://image.tmdb.org/t/p/w342/fixed-movie.jpg",
                releaseDate = LocalDate.of(2024, 1, 15)
            ),
            MediaSearchResult(
                externalRef = seriesRef,
                mediaType = MediaType.SERIES,
                title = "Fixed Test Series",
                posterUrl = null,
                releaseDate = LocalDate.of(2023, 4, 2)
            )
        )

    val firstPage =
        MediaSearchPage(
            results = searchResults,
            page = 1,
            totalPages = 2,
            totalResults = 3
        )

    val finalPage =
        MediaSearchPage(
            results =
            listOf(
                MediaSearchResult(
                    externalRef = ExternalMediaRef(MediaSource.TMDB, "42"),
                    mediaType = MediaType.MOVIE,
                    title = "Fixed Final Result",
                    releaseDate = LocalDate.of(2022, 9, 1)
                )
            ),
            page = 2,
            totalPages = 2,
            totalResults = 3
        )
}
