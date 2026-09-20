package com.cydoniancitizen.bingee.core.result

sealed interface AppResult<out T> {
    data class Success<T>(val value: T) : AppResult<T>

    data class Failure(val error: AppError) : AppResult<Nothing>
}

fun AppResult<*>.errorOrNull(): AppError? = (this as? AppResult.Failure)?.error
