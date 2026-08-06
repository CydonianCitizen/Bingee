package com.cydoniancitizen.bingee.data.importexport

import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

internal data class ExportedBackup(val bytes: ByteArray)

@Singleton
internal class BackupExporter @Inject constructor(private val dataStore: BackupDataStore, private val clock: Clock) {
    suspend fun export(): ExportedBackup {
        val bytes = dataStore.createPortableBackup()
        return ExportedBackup(bytes)
    }
}
