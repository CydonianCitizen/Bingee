package com.cydoniancitizen.bingee.data.jikan

import com.cydoniancitizen.bingee.core.result.AppError
import com.cydoniancitizen.bingee.core.result.AppResult
import com.google.gson.JsonParseException
import com.google.gson.stream.MalformedJsonException
import java.io.IOException
import kotlinx.coroutines.CancellationException
import retrofit2.Response

internal suspend fun <T : Any, R> jikanCall(
    gate: JikanRequestGate,
    request: suspend () -> Response<T>,
    map: (T) -> R
): AppResult<R> = try {
    gate.awaitTurn()
    val response = request()
    when {
        response.isSuccessful -> response.body()?.let { AppResult.Success(map(it)) }
            ?: AppResult.Failure(AppError.InvalidRemoteResponse)
        response.code() == 404 -> AppResult.Failure(AppError.MissingData)
        response.code() == 429 -> AppResult.Failure(AppError.RateLimited)
        response.code() in 500..599 -> AppResult.Failure(AppError.RemoteServiceFailure)
        else -> AppResult.Failure(AppError.InvalidRemoteResponse)
    }
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: JsonParseException) {
    AppResult.Failure(AppError.InvalidRemoteResponse)
} catch (_: MalformedJsonException) {
    AppResult.Failure(AppError.InvalidRemoteResponse)
} catch (_: IOException) {
    AppResult.Failure(AppError.NetworkUnavailable)
} catch (_: RuntimeException) {
    AppResult.Failure(AppError.InvalidRemoteResponse)
}
