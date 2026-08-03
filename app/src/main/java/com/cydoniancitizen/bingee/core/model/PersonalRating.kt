package com.cydoniancitizen.bingee.core.model

data class PersonalRating(val value: Int) {
    init {
        require(value in MIN_VALUE..MAX_VALUE) { "Personal rating must be between 1 and 10" }
    }

    companion object {
        const val MIN_VALUE = 1
        const val MAX_VALUE = 10
    }
}
