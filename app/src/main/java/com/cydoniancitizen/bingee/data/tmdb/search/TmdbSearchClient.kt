package com.cydoniancitizen.bingee.data.tmdb.search

import com.cydoniancitizen.bingee.core.model.MediaSearchCategory
import com.cydoniancitizen.bingee.core.model.MediaSearchPage
import com.cydoniancitizen.bingee.core.model.MediaSearchQuery
import com.cydoniancitizen.bingee.core.result.AppError
import com.cydoniancitizen.bingee.core.result.AppResult
import com.cydoniancitizen.bingee.data.credential.TmdbCredentialStore
import com.google.gson.JsonParseException
import com.google.gson.stream.MalformedJsonException
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import retrofit2.Response

internal class TmdbSearchClient @Inject constructor(
    private val credentialStore: TmdbCredentialStore,
    private val service: TmdbSearchService
) {
    suspend fun search(query: MediaSearchQuery): AppResult<MediaSearchPage> {
        val credential = when (val stored = credentialStore.read()) {
            is AppResult.Success -> stored.value ?: return AppResult.Failure(AppError.Unauthorized)
            is AppResult.Failure -> return stored
        }
        val authorization = "Bearer ${credential.reveal()}"
        return execute {
            when (query.category) {
                MediaSearchCategory.MOVIES -> service.searchMovies(
                    authorization = authorization,
                    query = query.query,
                    includeAdult = false,
                    language = query.language,
                    page = query.page
                ).mapBody { TmdbMovieSearchMapper.map(it, query.page) }

                MediaSearchCategory.TV_SERIES -> service.searchTvSeries(
                    authorization = authorization,
                    query = query.query,
                    includeAdult = false,
                    language = query.language,
                    page = query.page
                ).mapBody { TmdbTvSearchMapper.map(it, query.page) }
            }
        }
    }

    private suspend fun execute(request: suspend () -> AppResult<MediaSearchPage>): AppResult<MediaSearchPage> = try {
        request()
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

private inline fun <T, R> Response<T>.mapBody(transform: (T) -> R): AppResult<R> = when {
    isSuccessful -> body()?.let { AppResult.Success(transform(it)) }
        ?: AppResult.Failure(AppError.InvalidRemoteResponse)
    code() == 401 || code() == 403 -> AppResult.Failure(AppError.Unauthorized)
    code() == 429 -> AppResult.Failure(AppError.RateLimited)
    code() in 500..599 -> AppResult.Failure(AppError.RemoteServiceFailure)
    code() == 400 || code() == 422 -> AppResult.Failure(AppError.InvalidRemoteResponse)
    else -> AppResult.Failure(AppError.Unknown)
}
