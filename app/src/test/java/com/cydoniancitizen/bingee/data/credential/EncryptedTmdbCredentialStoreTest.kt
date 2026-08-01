package com.cydoniancitizen.bingee.data.credential

import com.cydoniancitizen.bingee.core.credential.TmdbCredential
import com.cydoniancitizen.bingee.core.result.AppError
import com.cydoniancitizen.bingee.core.result.AppResult
import java.io.File
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EncryptedTmdbCredentialStoreTest {
    @Test
    fun missingFileReturnsNoCredential() = runTest {
        val store = EncryptedTmdbCredentialStore(FakeCipher(), FakeFile())

        val result = store.read()

        assertTrue(result is AppResult.Success)
        assertNull((result as AppResult.Success).value)
    }

    @Test
    fun saveSupportsInternalReadAndReplacement() = runTest {
        val file = FakeFile()
        val store = EncryptedTmdbCredentialStore(FakeCipher(), file)

        store.save(TmdbCredential("fake_first"))
        store.save(TmdbCredential("fake_second"))
        val result = store.read()

        assertTrue(result is AppResult.Success)
        assertTrue((result as AppResult.Success).value?.reveal() == "fake_second")
        assertFalse(String(file.value!!, StandardCharsets.UTF_8).contains("fake_second"))
    }

    @Test
    fun deletionRemovesCiphertextAndKey() = runTest {
        val file = FakeFile()
        val cipher = FakeCipher()
        val store = EncryptedTmdbCredentialStore(cipher, file)
        store.save(TmdbCredential("fake_delete"))

        val result = store.delete()

        assertEquals(AppResult.Success(Unit), result)
        assertNull(file.value)
        assertTrue(cipher.keyDeleted)
    }

    @Test
    fun corruptedOrUnreadableValueReturnsSafeFailure() = runTest {
        val file = FakeFile(byteArrayOf(0))
        val store = EncryptedTmdbCredentialStore(FakeCipher(), file)

        val result = store.read()

        assertEquals(AppResult.Failure(AppError.CorruptedData), result)
        assertFalse(result.toString().contains("fake_"))
    }

    @Test
    fun productionStorageUsesNoBackupDirectoryAndBackupRulesCoverTransfer() {
        val projectDir = File(System.getProperty("user.dir") ?: ".")
        val source =
            File(
                projectDir,
                "src/main/java/com/cydoniancitizen/bingee/data/credential/" +
                    "NoBackupTmdbCredentialFile.kt"
            ).readText()
        val extractionRules = File(projectDir, "src/main/res/xml/data_extraction_rules.xml").readText()

        assertTrue(source.contains("noBackupFilesDir"))
        assertTrue(extractionRules.contains("<cloud-backup>"))
        assertTrue(extractionRules.contains("<device-transfer>"))
        assertFalse(extractionRules.contains(NoBackupTmdbCredentialFile.FILE_NAME))
    }

    private class FakeCipher : TmdbCredentialCipher {
        var keyDeleted = false

        override fun encrypt(value: String): ByteArray = value.reversed().toByteArray(StandardCharsets.UTF_8)

        override fun decrypt(value: ByteArray): String {
            require(value.size > 1)
            return String(value, StandardCharsets.UTF_8).reversed()
        }

        override fun deleteKey() {
            keyDeleted = true
        }
    }

    private class FakeFile(initial: ByteArray? = null) : TmdbCredentialFile {
        var value: ByteArray? = initial

        override fun read(): ByteArray? = value?.copyOf()

        override fun write(value: ByteArray) {
            this.value = value.copyOf()
        }

        override fun delete() {
            value = null
        }
    }
}
