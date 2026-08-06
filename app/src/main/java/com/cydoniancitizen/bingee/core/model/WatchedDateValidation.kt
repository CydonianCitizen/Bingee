package com.cydoniancitizen.bingee.core.model

import java.time.LocalDate

enum class WatchedDateChoice {
    TODAY,
    RELEASE_DATE,
    CUSTOM_DATE
}

sealed interface WatchedDateValidationResult {
    data object Valid : WatchedDateValidationResult
    data class FutureDateRejected(val date: LocalDate, val today: LocalDate) : WatchedDateValidationResult
    data class DatePrecedesReleaseRejected(val date: LocalDate, val releaseDate: LocalDate) :
        WatchedDateValidationResult
}

fun validateWatchedDate(
    watchedDate: LocalDate?,
    releaseDate: LocalDate?,
    today: LocalDate = LocalDate.now()
): WatchedDateValidationResult {
    if (watchedDate == null) return WatchedDateValidationResult.Valid
    if (watchedDate.isAfter(today)) {
        return WatchedDateValidationResult.FutureDateRejected(watchedDate, today)
    }
    if (releaseDate != null && watchedDate.isBefore(releaseDate)) {
        return WatchedDateValidationResult.DatePrecedesReleaseRejected(watchedDate, releaseDate)
    }
    return WatchedDateValidationResult.Valid
}

fun WatchedDateValidationResult.isValid(): Boolean = this is WatchedDateValidationResult.Valid
