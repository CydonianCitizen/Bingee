package com.cydoniancitizen.bingee.data.tmdb.auth

import com.cydoniancitizen.bingee.core.credential.TmdbCredential
import com.cydoniancitizen.bingee.core.result.AppError
import com.cydoniancitizen.bingee.core.result.AppResult
import com.google.gson.JsonParseException
import com.google.gson.stream.MalformedJsonException
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException

internal interface TmdbCredentialRemoteValidator {
    suspend fun validate(credential: TmdbCredential): AppResult<Unit>
}

internal class TmdbCredentialValidationClient @Inject constructor(private val service: TmdbAuthenticationService) :
    TmdbCredentialRemoteValidator {
    override suspend fun validate(credential: TmdbCredential): AppResult<Unit> = try {
        val response = service.validate("Bearer ${credential.reveal()}")
        when {
            response.isSuccessful && response.body()?.success == true ->
                AppResult.Success(Unit)

            response.isSuccessful -> AppResult.Failure(AppError.InvalidRemoteResponse)
            response.code() == 401 || response.code() == 403 ->
                AppResult.Failure(AppError.Unauthorized)

            response.code() == 429 -> AppResult.Failure(AppError.RateLimited)
            response.code() in 500..599 -> AppResult.Failure(AppError.RemoteServiceFailure)
            else -> AppResult.Failure(AppError.Unknown)
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: JsonParseException) {
        AppResult.Failure(AppError.InvalidRemoteResponse)
    } catch (_: MalformedJsonException) {
        AppResult.Failure(AppError.InvalidRemoteResponse)
    } catch (_: IOException) {
        AppResult.Failure(AppError.NetworkUnavailable)
    } catch (_: Exception) {
        AppResult.Failure(AppError.Unknown)
    }
}
