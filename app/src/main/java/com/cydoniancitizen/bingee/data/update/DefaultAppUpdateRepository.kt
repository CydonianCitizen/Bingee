package com.cydoniancitizen.bingee.data.update

import com.cydoniancitizen.bingee.domain.repository.AppUpdateRepository
import com.cydoniancitizen.bingee.domain.repository.AppUpdateResult
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class DefaultAppUpdateRepository @Inject constructor(private val releaseService: GitHubReleaseService) :
    AppUpdateRepository {

    override suspend fun checkForUpdates(installedVersionName: String): AppUpdateResult {
        val installedSemVer = SemanticVersion.parse(installedVersionName)
            ?: return AppUpdateResult.Error("Unable to parse installed version")

        val releaseDto = try {
            releaseService.getLatestRelease()
        } catch (e: Exception) {
            return AppUpdateResult.Error(e.message ?: "Network error")
        }

        if (releaseDto.draft || releaseDto.prerelease) {
            return AppUpdateResult.Error("No stable release found")
        }

        val rawTag = releaseDto.tagName ?: return AppUpdateResult.Error("Missing tag name in release response")
        val latestSemVer = SemanticVersion.parse(rawTag)
            ?: return AppUpdateResult.Error("Malformed release version: $rawTag")

        val releaseUrl = releaseDto.htmlUrl ?: DEFAULT_RELEASE_URL

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

    private companion object {
        const val DEFAULT_RELEASE_URL = "https://github.com/CydonianCitizen/Bingee/releases"
    }
}
