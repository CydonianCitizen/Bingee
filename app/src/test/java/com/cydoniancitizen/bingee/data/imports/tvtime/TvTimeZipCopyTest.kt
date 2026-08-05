package com.cydoniancitizen.bingee.data.imports.tvtime

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TvTimeZipCopyTest {
    @Test
    fun copiesBoundedInput() = runBlocking {
        val bytes = "synthetic archive".toByteArray()
        val output = ByteArrayOutputStream()

        copyBoundedTvTimeInput(ByteArrayInputStream(bytes), output)

        assertArrayEquals(bytes, output.toByteArray())
    }

    @Test
    fun rejectsCompressedInputOverLimitWithoutRetainingIt() {
        val failure = assertThrows(TvTimeArchiveFailure::class.java) {
            runBlocking {
                copyBoundedTvTimeInput(
                    CountingInputStream(TvTimeImportLimits.MAX_COMPRESSED_INPUT_BYTES + 1),
                    DiscardingOutputStream
                )
            }
        }

        assertEquals(TvTimeArchiveFailureKind.OVERSIZED_INPUT, failure.kind)
    }

    @Test
    fun rethrowsCancellationBeforeReading() {
        val cancelled = Job().apply { cancel() }
        assertThrows(CancellationException::class.java) {
            runBlocking {
                withContext(cancelled) {
                    copyBoundedTvTimeInput(CountingInputStream(Long.MAX_VALUE), DiscardingOutputStream)
                }
            }
        }
    }

    private class CountingInputStream(private var remaining: Long) : InputStream() {
        override fun read(): Int = if (remaining-- > 0) 0 else -1

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (remaining <= 0) return -1
            val count = minOf(remaining, length.toLong()).toInt()
            remaining -= count
            return count
        }
    }

    private data object DiscardingOutputStream : OutputStream() {
        override fun write(value: Int) = Unit

        override fun write(buffer: ByteArray, offset: Int, length: Int) = Unit
    }
}
