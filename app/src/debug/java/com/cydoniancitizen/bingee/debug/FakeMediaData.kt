package com.cydoniancitizen.bingee.debug

import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.MediaDetails
import com.cydoniancitizen.bingee.core.model.MediaSearchResult
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import java.time.Duration
import java.time.LocalDate

object FakeMediaData {
    val movieRef = ExternalMediaRef(MediaSource.TMDB, "550")
    val seriesRef = ExternalMediaRef(MediaSource.JIKAN, "1")

    val searchResults =
        listOf(
            MediaSearchResult(
                externalRef = movieRef,
                mediaType = MediaType.MOVIE,
                title = "Fixed Test Movie",
                releaseDate = LocalDate.of(2024, 1, 15)
            ),
            MediaSearchResult(
                externalRef = seriesRef,
                mediaType = MediaType.SERIES,
                title = "Fixed Test Series",
                releaseDate = LocalDate.of(2023, 4, 2)
            )
        )

    val movieDetails =
        MediaDetails(
            externalRef = movieRef,
            mediaType = MediaType.MOVIE,
            title = "Fixed Test Movie",
            overview = "Deterministic preview and test data.",
            releaseDate = LocalDate.of(2024, 1, 15),
            runtime = Duration.ofMinutes(100),
            genres = listOf("Drama")
        )
}
