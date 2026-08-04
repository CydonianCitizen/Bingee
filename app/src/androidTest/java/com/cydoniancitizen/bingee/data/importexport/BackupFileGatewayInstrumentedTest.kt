package com.cydoniancitizen.bingee.data.importexport

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupFileGatewayInstrumentedTest {
    private lateinit var context: Context
    private lateinit var store: BackupShareFileStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        store = BackupShareFileStore(context)
    }

    @After
    fun tearDown() {
        store.cleanupStale()
    }

    @Test
    fun providerExposesOnlyBackupCachePathAndContentUriReads() {
        val bytes = "synthetic backup".toByteArray()
        val file = store.create(bytes)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.backup-files", file)

        assertEquals("content", uri.scheme)
        assertEquals("${context.packageName}.backup-files", uri.authority)
        val actual = context.contentResolver.openInputStream(uri).use { requireNotNull(it).readBytes() }
        assertArrayEquals(bytes, actual)

        val unexposed = File(context.cacheDir, "not-a-backup.json").also { it.writeText("private") }
        var rejected = false
        try {
            FileProvider.getUriForFile(context, "${context.packageName}.backup-files", unexposed)
        } catch (_: IllegalArgumentException) {
            rejected = true
        } finally {
            unexposed.delete()
        }
        assertTrue("provider must reject files outside backup_exports", rejected)
    }

    @Test
    fun shareIntentGrantsReadOnlyAccess() {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.backup-files",
            store.create(byteArrayOf(1, 2, 3))
        )
        val intent = buildBackupShareIntent(uri)

        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals(BACKUP_MIME_TYPE, intent.type)
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertFalse(intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION != 0)
        assertEquals(uri, intent.clipData?.getItemAt(0)?.uri)
    }

    @Test
    fun staleShareFilesAreRemovedBeforeNewShareFile() {
        val stale = File(context.cacheDir, "backup_exports/old.json").also {
            it.parentFile?.mkdirs()
            it.writeText("stale")
        }
        store.create(byteArrayOf(9))
        assertFalse(stale.exists())
    }

    @Test
    fun gatewayReadsProviderUriAndClosesStream() = runBlocking {
        val bytes = "{\"synthetic\":true}".toByteArray()
        val file = store.create(bytes)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.backup-files", file)
        val result = AndroidBackupFileGateway(context, store).read(uri)

        assertTrue(result is BackupParseResult.Failure)
        assertEquals(BackupFailureKind.INVALID_STRUCTURE, (result as BackupParseResult.Failure).failure.kind)
    }
}
