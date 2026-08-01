package com.cydoniancitizen.bingee.core.model

import java.time.Instant

data class LibraryEntry(val mediaRef: ExternalMediaRef, val mediaType: MediaType, val addedAt: Instant)
