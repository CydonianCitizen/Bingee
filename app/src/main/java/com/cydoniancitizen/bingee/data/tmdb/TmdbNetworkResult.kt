package com.cydoniancitizen.bingee.data.tmdb

import com.cydoniancitizen.bingee.core.result.AppError
import com.cydoniancitizen.bingee.core.result.AppResult
import com.google.gson.JsonParseException
import com.google.gson.stream.MalformedJsonException
import java.io.IOException
import kotlinx.coroutines.CancellationException
import retrofit2.Response

internal suspend inline fun <T, R> executeTmdbRequest(
    crossinline request: suspend () -> Response<T>,
    transform: (T) -> R
): AppResult<R> = try {
    request().toAppResult(transform)
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: JsonParseException) {
    AppResult.Failure(AppError.InvalidRemoteResponse)
} catch (_: MalformedJsonException) {
    AppResult.Failure(AppError.InvalidRemoteResponse)
} catch (_: IllegalArgumentException) {
    AppResult.Failure(AppError.InvalidRemoteResponse)
} catch (_: IOException) {
    AppResult.Failure(AppError.NetworkUnavailable)
} catch (_: Exception) {
    AppResult.Failure(AppError.Unknown)
}

private inline fun <T, R> Response<T>.toAppResult(transform: (T) -> R): AppResult<R> = when {
    isSuccessful -> body()?.let { AppResult.Success(transform(it)) }
        ?: AppResult.Failure(AppError.InvalidRemoteResponse)
    code() == 401 || code() == 403 -> AppResult.Failure(AppError.Unauthorized)
    code() == 404 -> AppResult.Failure(AppError.MissingData)
    code() == 429 -> AppResult.Failure(AppError.RateLimited)
    code() in 500..599 -> AppResult.Failure(AppError.RemoteServiceFailure)
    code() == 400 || code() == 422 -> AppResult.Failure(AppError.InvalidRemoteResponse)
    else -> AppResult.Failure(AppError.Unknown)
}
