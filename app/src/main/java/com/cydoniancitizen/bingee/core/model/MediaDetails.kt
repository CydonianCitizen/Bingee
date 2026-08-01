package com.cydoniancitizen.bingee.core.model

import java.time.Duration
import java.time.LocalDate

data class MediaDetails(
    val externalRef: ExternalMediaRef,
    val mediaType: MediaType,
    val title: String,
    val originalTitle: String? = null,
    val overview: String? = null,
    val releaseDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val runtime: Duration? = null,
    val genres: List<String> = emptyList()
) {
    init {
        require(title.isNotBlank()) { "Media title must not be blank" }
    }
}
