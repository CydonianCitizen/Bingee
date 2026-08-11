package com.cydoniancitizen.bingee.data.update

import com.cydoniancitizen.bingee.core.result.AppError
import com.cydoniancitizen.bingee.domain.repository.AppUpdateRepository
import com.cydoniancitizen.bingee.domain.repository.AppUpdateResult
import java.io.IOException
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException

@Singleton
internal class DefaultAppUpdateRepository @Inject constructor(private val releaseService: GitHubReleaseService) :
    AppUpdateRepository {

    override suspend fun checkForUpdates(installedVersionName: String): AppUpdateResult {
        val installedSemVer = SemanticVersion.parse(installedVersionName)
            ?: return AppUpdateResult.Error(AppError.InvalidInput)

        val releaseDto = try {
            releaseService.getLatestRelease()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: IOException) {
            return AppUpdateResult.Error(AppError.NetworkUnavailable)
        } catch (http: HttpException) {
            return AppUpdateResult.Error(
                if (http.code() == 429) AppError.RateLimited else AppError.RemoteServiceFailure
            )
        } catch (_: Exception) {
            return AppUpdateResult.Error(AppError.Unknown)
        }

        if (releaseDto.draft || releaseDto.prerelease) {
            return AppUpdateResult.Error(AppError.InvalidRemoteResponse)
        }

        val rawTag = releaseDto.tagName ?: return AppUpdateResult.Error(AppError.InvalidRemoteResponse)
        val latestSemVer = SemanticVersion.parse(rawTag)
            ?: return AppUpdateResult.Error(AppError.InvalidRemoteResponse)

        val releaseUrl = releaseDto.htmlUrl
            ?.takeIf(::isValidGitHubReleaseUrl)
            ?: return AppUpdateResult.Error(AppError.InvalidRemoteResponse)

        return if (latestSemVer > installedSemVer) {
            AppUpdateResult.UpdateAvailable(
                installedVersion = installedSemVer.toString(),
                latestVersion = latestSemVer.toString(),
                releaseUrl = releaseUrl
            )
        } else {
            AppUpdateResult.UpToDate(
                installedVersion = installedSemVer.toString()
            )
        }
    }
}

internal fun isValidGitHubReleaseUrl(rawUrl: String): Boolean {
    val uri = runCatching { URI(rawUrl).normalize() }.getOrNull() ?: return false
    if (uri.scheme != "https" || uri.host != "github.com" || uri.port != -1 || uri.userInfo != null) return false
    val segments = uri.path.split('/').filter(String::isNotEmpty)
    return segments.size >= 3 &&
        segments[0].equals("CydonianCitizen", ignoreCase = true) &&
        segments[1].equals("Bingee", ignoreCase = true) &&
        segments[2].equals("releases", ignoreCase = true) &&
        segments.none { it == "." || it == ".." }
}
