package com.cydoniancitizen.bingee.core.model

import java.time.LocalDate

data class MediaSearchResult(
    val externalRef: ExternalMediaRef,
    val mediaType: MediaType,
    val title: String,
    val originalTitle: String? = null,
    val releaseDate: LocalDate? = null,
    val overview: String? = null
) {
    init {
        require(title.isNotBlank()) { "Media title must not be blank" }
    }
}
