package com.cydoniancitizen.bingee.domain.repository

sealed interface AppUpdateResult {
    data class UpToDate(val installedVersion: String) : AppUpdateResult
    data class UpdateAvailable(val installedVersion: String, val latestVersion: String, val releaseUrl: String) :
        AppUpdateResult
    data class Error(val message: String? = null) : AppUpdateResult
}

interface AppUpdateRepository {
    suspend fun checkForUpdates(installedVersionName: String): AppUpdateResult
}
