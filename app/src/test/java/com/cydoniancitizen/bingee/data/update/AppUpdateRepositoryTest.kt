package com.cydoniancitizen.bingee.data.update

import com.cydoniancitizen.bingee.domain.repository.AppUpdateResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

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
        val fakeService = FakeGitHubReleaseService(
            response = GitHubReleaseDto(
                tagName = "Bingee-v1.0.1-stable",
                htmlUrl = "https://github.com/CydonianCitizen/Bingee/releases/tag/Bingee-v1.0.1-stable"
            )
        )
        val repository = DefaultAppUpdateRepository(fakeService)

        val result = repository.checkForUpdates("1.0.1")

        assertTrue(result is AppUpdateResult.UpToDate)
        assertEquals("1.0.1", (result as AppUpdateResult.UpToDate).installedVersion)
    }

    @Test
    fun checkForUpdatesReturnsUpdateAvailableWhenNewerStableExists() = runTest {
        val fakeService = FakeGitHubReleaseService(
            response = GitHubReleaseDto(
                tagName = "Bingee-v1.1.0-stable",
                htmlUrl = "https://github.com/CydonianCitizen/Bingee/releases/tag/Bingee-v1.1.0-stable"
            )
        )
        val repository = DefaultAppUpdateRepository(fakeService)

        val result = repository.checkForUpdates("1.0.1")

        assertTrue(result is AppUpdateResult.UpdateAvailable)
        val updateAvailable = result as AppUpdateResult.UpdateAvailable
        assertEquals("1.0.1", updateAvailable.installedVersion)
        assertEquals("1.1.0", updateAvailable.latestVersion)
        assertEquals(
            "https://github.com/CydonianCitizen/Bingee/releases/tag/Bingee-v1.1.0-stable",
            updateAvailable.releaseUrl
        )
    }

    @Test
    fun checkForUpdatesIgnoresDraftAndPrereleaseReleases() = runTest {
        val fakeService = FakeGitHubReleaseService(
            response = GitHubReleaseDto(
                tagName = "Bingee-v2.0.0-beta",
                htmlUrl = "https://github.com/CydonianCitizen/Bingee/releases/tag/v2.0.0-beta",
                prerelease = true
            )
        )
        val repository = DefaultAppUpdateRepository(fakeService)

        val result = repository.checkForUpdates("1.0.1")

        assertTrue(result is AppUpdateResult.Error)
    }

    @Test
    fun checkForUpdatesHandlesMalformedVersionGracefully() = runTest {
        val fakeService = FakeGitHubReleaseService(
            response = GitHubReleaseDto(
                tagName = "invalid-release-tag",
                htmlUrl = "https://github.com/CydonianCitizen/Bingee/releases"
            )
        )
        val repository = DefaultAppUpdateRepository(fakeService)

        val result = repository.checkForUpdates("1.0.1")

        assertTrue(result is AppUpdateResult.Error)
    }

    @Test
    fun checkForUpdatesHandlesNetworkErrorGracefully() = runTest {
        val fakeService = FakeGitHubReleaseService(shouldThrow = true)
        val repository = DefaultAppUpdateRepository(fakeService)

        val result = repository.checkForUpdates("1.0.1")

        assertTrue(result is AppUpdateResult.Error)
        assertEquals("Network connection failed", (result as AppUpdateResult.Error).message)
    }

    private class FakeGitHubReleaseService(
        private val response: GitHubReleaseDto = GitHubReleaseDto(),
        private val shouldThrow: Boolean = false
    ) : GitHubReleaseService {
        override suspend fun getLatestRelease(): GitHubReleaseDto {
            if (shouldThrow) throw Exception("Network connection failed")
            return response
        }
    }
}
