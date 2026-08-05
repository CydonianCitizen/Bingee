package com.cydoniancitizen.bingee.data.imports.tvtime

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipFile
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal suspend fun copyBoundedTvTimeInput(source: InputStream, output: OutputStream) {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0L
    while (true) {
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        val read = source.read(buffer)
        if (read < 0) break
        total += read
        if (total > TvTimeImportLimits.MAX_COMPRESSED_INPUT_BYTES) {
            throw TvTimeArchiveFailure(TvTimeArchiveFailureKind.OVERSIZED_INPUT)
        }
        output.write(buffer, 0, read)
    }
}

internal enum class TvTimeArchiveEntryKind {
    JSON,
    HTML
}

internal data class TvTimeArchiveEntry(
    val index: Int,
    val kind: TvTimeArchiveEntryKind,
    val compressedSize: Long,
    val uncompressedSize: Long
)

internal interface TvTimeArchive : Closeable {
    val entries: List<TvTimeArchiveEntry>

    fun open(entry: TvTimeArchiveEntry): InputStream
}

internal sealed interface TvTimeArchiveResult<out T> {
    data class Success<T>(val value: T) : TvTimeArchiveResult<T>
    data class Failure(val failure: TvTimeArchiveFailure) : TvTimeArchiveResult<Nothing>
}

internal interface TvTimeZipGateway {
    suspend fun <T> withArchive(uri: Uri, block: suspend (TvTimeArchive) -> T): TvTimeArchiveResult<T>
}

internal fun validateTvTimeArchiveEntryName(name: String): TvTimeArchiveFailureKind? {
    if (name.indexOf('\u0000') >= 0) return TvTimeArchiveFailureKind.NULL_BYTE_PATH
    val slashName = name.replace('\\', '/')
    if (slashName.startsWith("//")) return TvTimeArchiveFailureKind.UNC_PATH
    if (slashName.startsWith('/')) return TvTimeArchiveFailureKind.ABSOLUTE_PATH
    if (Regex("^[A-Za-z]:.*").matches(slashName)) return TvTimeArchiveFailureKind.DRIVE_PATH
    if (slashName.split('/').any { it == "." || it == ".." || it.isEmpty() }) {
        return TvTimeArchiveFailureKind.PATH_TRAVERSAL
    }
    return null
}

@Singleton
internal class AndroidTvTimeZipGateway @Inject constructor(@param:ApplicationContext private val context: Context) :
    TvTimeZipGateway {
    override suspend fun <T> withArchive(uri: Uri, block: suspend (TvTimeArchive) -> T): TvTimeArchiveResult<T> =
        withContext(Dispatchers.IO) {
            val temporary = try {
                copyToPrivateCache(uri)
            } catch (failure: TvTimeArchiveFailure) {
                return@withContext TvTimeArchiveResult.Failure(failure)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                return@withContext TvTimeArchiveResult.Failure(
                    TvTimeArchiveFailure(TvTimeArchiveFailureKind.UNREADABLE_ZIP)
                )
            }

            try {
                val archive = openAndValidate(temporary)
                try {
                    TvTimeArchiveResult.Success(block(archive))
                } finally {
                    archive.close()
                }
            } catch (failure: TvTimeArchiveFailure) {
                TvTimeArchiveResult.Failure(failure)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: ZipException) {
                TvTimeArchiveResult.Failure(
                    TvTimeArchiveFailure(TvTimeArchiveFailureKind.MALFORMED_ARCHIVE)
                )
            } catch (_: IOException) {
                TvTimeArchiveResult.Failure(
                    TvTimeArchiveFailure(TvTimeArchiveFailureKind.UNREADABLE_ZIP)
                )
            } finally {
                temporary.delete()
            }
        }

    private suspend fun copyToPrivateCache(uri: Uri): File {
        val directory = context.cacheDir
        cleanupStaleCopies()
        val temporary = File.createTempFile("tv-time-import-", ".zip", directory)
        try {
            val input = context.contentResolver.openInputStream(uri)
                ?: throw TvTimeArchiveFailure(TvTimeArchiveFailureKind.UNREADABLE_ZIP)
            input.use { source ->
                temporary.outputStream().use { output ->
                    copyBoundedTvTimeInput(source, output)
                }
            }
            return temporary
        } catch (failure: TvTimeArchiveFailure) {
            temporary.delete()
            throw failure
        } catch (cancelled: CancellationException) {
            temporary.delete()
            throw cancelled
        } catch (_: Exception) {
            temporary.delete()
            throw TvTimeArchiveFailure(TvTimeArchiveFailureKind.UNREADABLE_ZIP)
        }
    }

    private fun openAndValidate(file: File): TvTimeArchive {
        val zip = openZip(file)
        return try {
            val rawEntries = ArrayList<ZipEntry>()
            val entries = ArrayList<TvTimeArchiveEntry>()
            val seenPaths = HashSet<String>()
            val seenCasePaths = HashSet<String>()
            var declaredTotal = 0L
            val enumeration = zip.entries()
            while (enumeration.hasMoreElements()) {
                if (rawEntries.size >= TvTimeImportLimits.MAX_ENTRY_COUNT) {
                    throw TvTimeArchiveFailure(TvTimeArchiveFailureKind.TOO_MANY_ENTRIES)
                }
                val entry = enumeration.nextElement()
                if (entry.size < 0 || entry.compressedSize < 0) {
                    throw TvTimeArchiveFailure(TvTimeArchiveFailureKind.MALFORMED_ARCHIVE)
                }
                if (entry.size > TvTimeImportLimits.MAX_ENTRY_UNCOMPRESSED_BYTES) {
                    throw TvTimeArchiveFailure(TvTimeArchiveFailureKind.OVERSIZED_ENTRY)
                }
                declaredTotal += entry.size
                if (declaredTotal > TvTimeImportLimits.MAX_TOTAL_UNCOMPRESSED_BYTES) {
                    throw TvTimeArchiveFailure(TvTimeArchiveFailureKind.OVERSIZED_TOTAL)
                }
                rawEntries += entry
            }
            rawEntries.forEachIndexed { index, entry ->
                validateEntry(zip, entry, seenPaths, seenCasePaths)
                entries += TvTimeArchiveEntry(
                    index = index,
                    kind = entryKind(entry.name),
                    compressedSize = entry.compressedSize,
                    uncompressedSize = entry.size
                )
            }
            if (entries.count { it.kind == TvTimeArchiveEntryKind.HTML } > 1) {
                throw TvTimeArchiveFailure(TvTimeArchiveFailureKind.UNSUPPORTED_LAYOUT)
            }
            ZipArchive(zip, rawEntries, entries)
        } catch (failure: TvTimeArchiveFailure) {
            zip.close()
            throw failure
        } catch (failure: ZipException) {
            zip.close()
            throw TvTimeArchiveFailure(failure.toArchiveFailureKind())
        } catch (_: Exception) {
            zip.close()
            throw TvTimeArchiveFailure(TvTimeArchiveFailureKind.MALFORMED_ARCHIVE)
        }
    }

    private fun validateEntry(
        zip: ZipFile,
        entry: ZipEntry,
        seenPaths: MutableSet<String>,
        seenCasePaths: MutableSet<String>
    ) {
        if (entry.isDirectory) throw TvTimeArchiveFailure(TvTimeArchiveFailureKind.UNSUPPORTED_LAYOUT)
        val name = entry.name
        validateTvTimeArchiveEntryName(name)?.let { throw TvTimeArchiveFailure(it) }
        val slashName = name.replace('\\', '/')
        val normalized = slashName.split('/').joinToString("/") { it.trim() }
        if (!seenPaths.add(normalized)) throw TvTimeArchiveFailure(TvTimeArchiveFailureKind.DUPLICATE_PATH)
        if (!seenCasePaths.add(normalized.lowercase(Locale.ROOT))) {
            throw TvTimeArchiveFailure(TvTimeArchiveFailureKind.CASE_COLLISION)
        }
        val size = entry.size
        val compressed = entry.compressedSize
        if (size < 0 || compressed < 0) {
            throw TvTimeArchiveFailure(TvTimeArchiveFailureKind.MALFORMED_ARCHIVE)
        }
        if (size >= 0 && size > TvTimeImportLimits.MAX_ENTRY_UNCOMPRESSED_BYTES) {
            throw TvTimeArchiveFailure(TvTimeArchiveFailureKind.OVERSIZED_ENTRY)
        }
        if (
            size >= 0 && compressed > 0 &&
            size > compressed * TvTimeImportLimits.MAX_COMPRESSION_RATIO
        ) {
            throw TvTimeArchiveFailure(TvTimeArchiveFailureKind.SUSPICIOUS_COMPRESSION)
        }
        if (isNestedArchive(name)) throw TvTimeArchiveFailure(TvTimeArchiveFailureKind.NESTED_ARCHIVE)
        entryKind(name)
        try {
            zip.getInputStream(entry).use { stream ->
                val signature = ByteArray(8)
                val read = stream.read(signature)
                if (isNestedArchiveSignature(signature, read)) {
                    throw TvTimeArchiveFailure(TvTimeArchiveFailureKind.NESTED_ARCHIVE)
                }
            }
        } catch (failure: ZipException) {
            throw TvTimeArchiveFailure(failure.toArchiveFailureKind())
        }
    }

    private fun entryKind(name: String): TvTimeArchiveEntryKind =
        when (name.substringAfterLast('.', "").lowercase(Locale.ROOT)) {
            "json" -> TvTimeArchiveEntryKind.JSON
            "html", "htm" -> TvTimeArchiveEntryKind.HTML
            else -> throw TvTimeArchiveFailure(TvTimeArchiveFailureKind.UNSUPPORTED_ENTRY)
        }

    private fun isNestedArchive(name: String): Boolean {
        val lower = name.lowercase(Locale.ROOT)
        return listOf(".zip", ".jar", ".7z", ".rar", ".tar", ".gz", ".tgz").any(lower::endsWith)
    }

    private fun cleanupStaleCopies() {
        context.cacheDir.listFiles()
            ?.filter { it.name.startsWith("tv-time-import-") }
            ?.forEach(File::delete)
    }

    private fun openZip(file: File): ZipFile = try {
        ZipFile(file)
    } catch (failure: ZipException) {
        throw TvTimeArchiveFailure(failure.toArchiveFailureKind())
    }
}

private fun ZipException.toArchiveFailureKind(): TvTimeArchiveFailureKind =
    if (message?.contains("encrypt", ignoreCase = true) == true) {
        TvTimeArchiveFailureKind.ENCRYPTED_ENTRY
    } else {
        TvTimeArchiveFailureKind.MALFORMED_ARCHIVE
    }

private fun isNestedArchiveSignature(bytes: ByteArray, count: Int): Boolean {
    if (count >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4b.toByte() &&
        bytes[2] in setOf(0x03.toByte(), 0x05.toByte(), 0x07.toByte()) &&
        bytes[3] in setOf(0x04.toByte(), 0x06.toByte(), 0x08.toByte())
    ) {
        return true
    }
    if (count >= 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()) return true
    if (count >= 6 &&
        bytes.sliceArray(0..5).contentEquals(byteArrayOf(0x37, 0x7a, 0xbc.toByte(), 0xaf.toByte(), 0x27, 0x1c))
    ) {
        return true
    }
    return count >= 7 && bytes.sliceArray(0..6).contentEquals(
        byteArrayOf(0x52, 0x61, 0x72, 0x21, 0x1a, 0x07, 0x00)
    )
}

private class ZipArchive(
    private val zip: ZipFile,
    private val rawEntries: List<ZipEntry>,
    override val entries: List<TvTimeArchiveEntry>
) : TvTimeArchive {
    private var totalRead = 0L

    override fun open(entry: TvTimeArchiveEntry): InputStream {
        val raw = rawEntries.getOrNull(entry.index)
            ?: throw TvTimeArchiveFailure(TvTimeArchiveFailureKind.UNSUPPORTED_LAYOUT)
        val source = try {
            zip.getInputStream(raw)
        } catch (failure: ZipException) {
            throw TvTimeArchiveFailure(failure.toArchiveFailureKind())
        }
        return object : InputStream() {
            private var entryRead = 0L

            override fun read(): Int {
                val value = source.read()
                if (value >= 0) count(1)
                return value
            }

            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                val read = source.read(buffer, offset, length)
                if (read > 0) count(read.toLong())
                return read
            }

            override fun close() = source.close()

            private fun count(read: Long) {
                entryRead += read
                totalRead += read
                if (entryRead > TvTimeImportLimits.MAX_ENTRY_UNCOMPRESSED_BYTES) {
                    throw TvTimeArchiveFailure(TvTimeArchiveFailureKind.OVERSIZED_ENTRY)
                }
                if (totalRead > TvTimeImportLimits.MAX_TOTAL_UNCOMPRESSED_BYTES) {
                    throw TvTimeArchiveFailure(TvTimeArchiveFailureKind.OVERSIZED_TOTAL)
                }
            }
        }
    }

    override fun close() {
        zip.close()
    }
}
