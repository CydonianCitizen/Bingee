package com.cydoniancitizen.bingee.data.importexport

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal interface BackupFileGateway {
    suspend fun read(uri: Uri): BackupParseResult
    suspend fun write(uri: Uri, bytes: ByteArray): BackupFailureKind?
    suspend fun share(bytes: ByteArray): BackupFailureKind?
}

@Singleton
internal class BackupShareFileStore @Inject constructor(@ApplicationContext context: Context) {
    private val directory = File(context.cacheDir, SHARE_DIRECTORY)

    fun cleanupStale() {
        directory.listFiles()?.forEach { file -> file.delete() }
    }

    fun create(bytes: ByteArray): File {
        directory.mkdirs()
        cleanupStale()
        return File(directory, SHARE_FILENAME).also { file ->
            file.outputStream().use { output -> output.write(bytes) }
        }
    }

    private companion object {
        const val SHARE_DIRECTORY = "backup_exports"
        const val SHARE_FILENAME = "bingee-backup-share.json"
    }
}

@Singleton
internal class AndroidBackupFileGateway @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val shareFileStore: BackupShareFileStore
) : BackupFileGateway {
    override suspend fun read(uri: Uri): BackupParseResult = withContext(Dispatchers.IO) {
        try {
            val stream = context.contentResolver.openInputStream(uri)
                ?: return@withContext BackupParseResult.Failure(BackupParseFailure(BackupFailureKind.UNREADABLE))
            stream.use { BackupJsonCodec.parse(it) }
        } catch (_: IOException) {
            BackupParseResult.Failure(BackupParseFailure(BackupFailureKind.UNREADABLE))
        } catch (_: SecurityException) {
            BackupParseResult.Failure(BackupParseFailure(BackupFailureKind.UNREADABLE))
        }
    }

    override suspend fun write(uri: Uri, bytes: ByteArray): BackupFailureKind? = withContext(Dispatchers.IO) {
        try {
            val output = context.contentResolver.openOutputStream(uri, "wt")
                ?: return@withContext BackupFailureKind.WRITE_FAILED
            output.use {
                it.write(bytes)
                it.flush()
            }
            null
        } catch (_: IOException) {
            BackupFailureKind.WRITE_FAILED
        } catch (_: SecurityException) {
            BackupFailureKind.WRITE_FAILED
        }
    }

    override suspend fun share(bytes: ByteArray): BackupFailureKind? = withContext(Dispatchers.IO) {
        val file = try {
            shareFileStore.create(bytes)
        } catch (_: IOException) {
            return@withContext BackupFailureKind.WRITE_FAILED
        }
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.backup-files",
                file
            )
            val send = Intent(Intent.ACTION_SEND).apply {
                type = BACKUP_MIME_TYPE
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newRawUri("backup", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            null
        } catch (_: Exception) {
            file.delete()
            BackupFailureKind.WRITE_FAILED
        }
    }
}
