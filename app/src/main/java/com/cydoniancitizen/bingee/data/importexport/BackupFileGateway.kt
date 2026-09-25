package com.cydoniancitizen.bingee.data.importexport

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal fun buildBackupShareIntent(uri: Uri): Intent = Intent(Intent.ACTION_SEND).apply {
    type = BACKUP_MIME_TYPE
    putExtra(Intent.EXTRA_STREAM, uri)
    clipData = ClipData.newRawUri("backup", uri)
    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

@Singleton
internal class BackupShareFileStore @Inject constructor(@ApplicationContext context: Context) {
    private val directory = File(context.cacheDir, SHARE_DIRECTORY)

    fun cleanupStale() {
        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1)
        directory.listFiles()?.forEach { file ->
            if (file.isFile && file.lastModified() < cutoff) file.delete()
        }
    }

    fun create(bytes: ByteArray): File {
        directory.mkdirs()
        cleanupStale()
        val file = File.createTempFile(SHARE_FILENAME_PREFIX, ".json", directory)
        try {
            file.outputStream().use { output -> output.write(bytes) }
        } catch (failure: IOException) {
            file.delete()
            throw failure
        }
        return file
    }

    private companion object {
        const val SHARE_DIRECTORY = "backup_exports"
        const val SHARE_FILENAME_PREFIX = "bingee-backup-share-"
    }
}

@Singleton
internal class BackupFileGateway @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val shareFileStore: BackupShareFileStore
) {
    suspend fun read(uri: Uri): BackupParseResult = withContext(Dispatchers.IO) {
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

    suspend fun write(uri: Uri, bytes: ByteArray): BackupFailureKind? = withContext(Dispatchers.IO) {
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

    suspend fun share(bytes: ByteArray): BackupFailureKind? = withContext(Dispatchers.IO) {
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
            val send = buildBackupShareIntent(uri)
            context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            null
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            file.delete()
            BackupFailureKind.WRITE_FAILED
        }
    }
}
