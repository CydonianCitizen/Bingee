package com.cydoniancitizen.bingee.core.model

import com.cydoniancitizen.bingee.core.result.AppError

enum class CalendarRefreshOutcome {
    COMPLETE_SUCCESS,
    PARTIAL_SUCCESS,
    COMPLETE_FAILURE,
    NO_WORK,
    CREDENTIAL_REQUIRED
}

data class CalendarRefreshSummary(
    val outcome: CalendarRefreshOutcome,
    val titlesConsidered: Int,
    val operationsSucceeded: Int,
    val operationsFailed: Int,
    val operationsSkipped: Int,
    val representativeError: AppError? = null
) {
    init {
        require(titlesConsidered >= 0)
        require(operationsSucceeded >= 0)
        require(operationsFailed >= 0)
        require(operationsSkipped >= 0)
    }
}
