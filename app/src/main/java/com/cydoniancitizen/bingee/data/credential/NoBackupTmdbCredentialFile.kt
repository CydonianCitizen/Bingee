package com.cydoniancitizen.bingee.data.credential

import android.content.Context
import android.util.AtomicFile
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

internal class NoBackupTmdbCredentialFile @Inject constructor(@ApplicationContext context: Context) :
    TmdbCredentialFile {
    private val atomicFile = AtomicFile(File(context.noBackupFilesDir, FILE_NAME))

    override fun read(): ByteArray? = if (atomicFile.baseFile.exists()) atomicFile.readFully() else null

    override fun write(value: ByteArray) {
        val output = atomicFile.startWrite()
        try {
            output.write(value)
            atomicFile.finishWrite(output)
        } catch (failure: Exception) {
            atomicFile.failWrite(output)
            throw failure
        }
    }

    override fun delete() {
        atomicFile.delete()
    }

    internal companion object {
        const val FILE_NAME = "tmdb_credential.bin"
    }
}
