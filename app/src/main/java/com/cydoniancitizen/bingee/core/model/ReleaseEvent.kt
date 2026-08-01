package com.cydoniancitizen.bingee.core.model

import java.time.Instant
import java.time.LocalDate

data class ReleaseEvent(
    val mediaRef: ExternalMediaRef,
    val mediaType: MediaType,
    val timing: ReleaseTiming,
    val title: String? = null
)

sealed interface ReleaseTiming {
    data class DateOnly(val date: LocalDate) : ReleaseTiming

    data class AtInstant(val instant: Instant) : ReleaseTiming
}
