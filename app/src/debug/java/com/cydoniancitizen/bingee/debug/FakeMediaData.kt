package com.cydoniancitizen.bingee.debug

import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.MediaSearchPage
import com.cydoniancitizen.bingee.core.model.MediaSearchResult
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import java.time.LocalDate

object FakeMediaData {
    val movieRef = ExternalMediaRef(MediaSource.TMDB, "550")
    val seriesRef = ExternalMediaRef(MediaSource.TMDB, "1399")

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
