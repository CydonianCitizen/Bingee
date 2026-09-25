package com.cydoniancitizen.bingee.data.importexport

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupFileGatewayInstrumentedTest {
    private lateinit var context: Context
    private lateinit var store: BackupShareFileStore
    private val testFiles = mutableListOf<File>()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        store = BackupShareFileStore(context)
    }

    @After
    fun tearDown() {
        testFiles.forEach { it.delete() }
    }

    private fun createShareFile(bytes: ByteArray): File = store.create(bytes).also { testFiles.add(it) }

    @Test
    fun providerExposesOnlyBackupCachePathAndContentUriReads() {
        val bytes = "synthetic backup".toByteArray()
        val file = createShareFile(bytes)
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
            createShareFile(byteArrayOf(1, 2, 3))
        )
        val intent = buildBackupShareIntent(uri)

        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals(BACKUP_MIME_TYPE, intent.type)
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertFalse(intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION != 0)
        assertEquals(uri, intent.clipData?.getItemAt(0)?.uri)
    }

    @Test
    fun firstShareUriStillReadsOriginalBytesAfterSecondShare() {
        val firstBytes = "first backup".toByteArray()
        val firstFile = createShareFile(firstBytes)
        val firstUri = FileProvider.getUriForFile(context, "${context.packageName}.backup-files", firstFile)
        val secondBytes = "second backup".toByteArray()
        val secondFile = createShareFile(secondBytes)
        val secondUri = FileProvider.getUriForFile(context, "${context.packageName}.backup-files", secondFile)

        assertNotEquals(firstUri, secondUri)
        val delayedFirstRead = context.contentResolver.openInputStream(firstUri).use { requireNotNull(it).readBytes() }
        val secondRead = context.contentResolver.openInputStream(secondUri).use { requireNotNull(it).readBytes() }
        assertArrayEquals(firstBytes, delayedFirstRead)
        assertArrayEquals(secondBytes, secondRead)
    }

    @Test
    fun onlyOldShareFilesAreRemovedBeforeNewShareFile() {
        val directory = File(context.cacheDir, "backup_exports").apply { mkdirs() }
        val stale = File.createTempFile("stale-", ".json", directory).also {
            testFiles.add(it)
            it.writeText("stale")
            assertTrue(it.setLastModified(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(2)))
        }
        val recent = File.createTempFile("recent-", ".json", directory).also {
            testFiles.add(it)
            it.writeText("recent")
        }

        createShareFile(byteArrayOf(9))

        assertFalse(stale.exists())
        assertTrue(recent.exists())
    }

    @Test
    fun gatewayReadsProviderUriAndClosesStream() = runBlocking {
        val bytes = "{\"synthetic\":true}".toByteArray()
        val file = createShareFile(bytes)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.backup-files", file)
        val result = BackupFileGateway(context, store).read(uri)

        assertTrue(result is BackupParseResult.Failure)
        assertEquals(BackupFailureKind.INVALID_STRUCTURE, (result as BackupParseResult.Failure).failure.kind)
    }
}
