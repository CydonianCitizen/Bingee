package com.cydoniancitizen.bingee.core.model

import java.time.Instant
import java.time.LocalDate

data class LibraryEntry(
    val mediaRef: ExternalMediaRef,
    val mediaType: MediaType,
    val title: String,
    val originalTitle: String? = null,
    val posterUrl: String? = null,
    val releaseDate: LocalDate? = null,
    val overview: String? = null,
    val addedAt: Instant,
    val progress: LibraryProgress = LibraryProgress.Unavailable,
    val personalRating: PersonalRating? = null
) {
    init {
        require(title.isNotBlank()) { "Library title must not be blank" }
    }

    val libraryState: LibraryState get() = progress.deriveLibraryState()
}
