package com.cydoniancitizen.bingee.data.update

import com.cydoniancitizen.bingee.core.result.AppError
import com.cydoniancitizen.bingee.domain.repository.AppUpdateResult
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class AppUpdateRepositoryTest {

    @Test
    fun semanticVersionParsingHandlesVariousFormats() {
        assertEquals(SemanticVersion(1, 0, 1), SemanticVersion.parse("Bingee-v1.0.1-stable"))
        assertEquals(SemanticVersion(1, 2, 0), SemanticVersion.parse("v1.2.0"))
        assertEquals(SemanticVersion(2, 0, 0), SemanticVersion.parse("2.0.0"))
        assertEquals(SemanticVersion(1, 1, 0), SemanticVersion.parse("Bingee-v1.1.0-rc1"))
        assertNull(SemanticVersion.parse("invalid-version"))
        assertNull(SemanticVersion.parse("v1.a.2"))
    }

    @Test
    fun semanticVersionComparisonWorksCorrectly() {
        val v100 = SemanticVersion(1, 0, 0)
        val v101 = SemanticVersion(1, 0, 1)
        val v110 = SemanticVersion(1, 1, 0)
        val v200 = SemanticVersion(2, 0, 0)

        assertTrue(v101 > v100)
        assertTrue(v110 > v101)
        assertTrue(v200 > v110)
        assertEquals(0, v101.compareTo(SemanticVersion(1, 0, 1)))
    }

    @Test
    fun checkForUpdatesReturnsUpToDateWhenInstalledIsEqualOrNewer() = runTest {
        val repository = DefaultAppUpdateRepository(
            FakeGitHubReleaseService(
                response = release("Bingee-v1.0.1-stable")
            )
        )

        val result = repository.checkForUpdates("1.0.1")

        assertEquals(AppUpdateResult.UpToDate("1.0.1"), result)
    }

    @Test
    fun checkForUpdatesReturnsUpdateAvailableWhenNewerStableExists() = runTest {
        val repository = DefaultAppUpdateRepository(
            FakeGitHubReleaseService(
                response = release("Bingee-v1.1.0-stable")
            )
        )

        val result = repository.checkForUpdates("1.0.1")

        assertEquals(
            AppUpdateResult.UpdateAvailable(
                installedVersion = "1.0.1",
                latestVersion = "1.1.0",
                releaseUrl = "https://github.com/CydonianCitizen/Bingee/releases/tag/Bingee-v1.1.0-stable"
            ),
            result
        )
    }

    @Test
    fun checkForUpdatesDoesNotTreatDraftOrPrereleaseAsStable() = runTest {
        val repository = DefaultAppUpdateRepository(
            FakeGitHubReleaseService(
                response = release("Bingee-v2.0.0-beta", prerelease = true)
            )
        )

        assertEquals(
            AppUpdateResult.Error(AppError.InvalidRemoteResponse),
            repository.checkForUpdates("1.0.1")
        )
    }

    @Test
    fun malformedReleaseResponseMapsToStableError() = runTest {
        val repository = DefaultAppUpdateRepository(
            FakeGitHubReleaseService(response = GitHubReleaseDto(tagName = "invalid-release-tag"))
        )

        assertEquals(
            AppUpdateResult.Error(AppError.InvalidRemoteResponse),
            repository.checkForUpdates("1.0.1")
        )
    }

    @Test
    fun networkFailureMapsWithoutExposingExceptionText() = runTest {
        val repository = DefaultAppUpdateRepository(
            FakeGitHubReleaseService(throwable = IOException("secret network details"))
        )

        assertEquals(
            AppUpdateResult.Error(AppError.NetworkUnavailable),
            repository.checkForUpdates("1.0.1")
        )
    }

    @Test
    fun githubHttpAndRateLimitFailuresMapCorrectly() = runTest {
        val rateLimited = DefaultAppUpdateRepository(
            FakeGitHubReleaseService(
                throwable = HttpException(Response.error<GitHubReleaseDto>(429, "".toResponseBody()))
            )
        )
        val apiFailure = DefaultAppUpdateRepository(
            FakeGitHubReleaseService(
                throwable = HttpException(Response.error<GitHubReleaseDto>(500, "".toResponseBody()))
            )
        )

        assertEquals(AppUpdateResult.Error(AppError.RateLimited), rateLimited.checkForUpdates("1.0.1"))
        assertEquals(AppUpdateResult.Error(AppError.RemoteServiceFailure), apiFailure.checkForUpdates("1.0.1"))
    }

    @Test
    fun cancellationPropagates() = runTest {
        val repository = DefaultAppUpdateRepository(
            FakeGitHubReleaseService(throwable = CancellationException("cancelled"))
        )

        try {
            repository.checkForUpdates("1.0.1")
            fail("CancellationException should propagate")
        } catch (_: CancellationException) {
        }
    }

    @Test
    fun releaseUrlsRequireHttpsGithubAndExpectedRepository() {
        assertTrue(isValidGitHubReleaseUrl("https://github.com/CydonianCitizen/Bingee/releases/tag/v1.1.0"))
        assertFalse(isValidGitHubReleaseUrl("http://github.com/CydonianCitizen/Bingee/releases/tag/v1.1.0"))
        assertFalse(isValidGitHubReleaseUrl("https://evil.example/CydonianCitizen/Bingee/releases/tag/v1.1.0"))
        assertFalse(isValidGitHubReleaseUrl("https://github.com/Other/Bingee/releases/tag/v1.1.0"))
    }

    @Test
    fun invalidReleaseUrlMapsToMalformedResponse() = runTest {
        val repository = DefaultAppUpdateRepository(
            FakeGitHubReleaseService(
                response = GitHubReleaseDto(
                    tagName = "Bingee-v1.1.0-stable",
                    htmlUrl = "http://github.com/CydonianCitizen/Bingee/releases/tag/v1.1.0"
                )
            )
        )

        assertEquals(
            AppUpdateResult.Error(AppError.InvalidRemoteResponse),
            repository.checkForUpdates("1.0.1")
        )
    }

    private fun release(tag: String, prerelease: Boolean = false) = GitHubReleaseDto(
        tagName = tag,
        htmlUrl = "https://github.com/CydonianCitizen/Bingee/releases/tag/$tag",
        prerelease = prerelease
    )

    private class FakeGitHubReleaseService(
        private val response: GitHubReleaseDto = GitHubReleaseDto(),
        private val throwable: Throwable? = null
    ) : GitHubReleaseService {
        override suspend fun getLatestRelease(): GitHubReleaseDto {
            throwable?.let { throw it }
            return response
        }
    }
}
