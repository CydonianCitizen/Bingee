package com.cydoniancitizen.bingee.data.imports.tvtime

import android.net.Uri
import com.cydoniancitizen.bingee.data.imports.model.ImportWarningCode
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TvTimeSourceParserTest {
    @Test
    fun parsesSyntheticProfileAndKeepsUnsupportedSummary() = runTest {
        val gateway = FakeZipGateway(movieJson())
        val result = parser(gateway).parseArchiveForTest(gateway.archive)
        assertTrue(result is TvTimeParseResult.Success)
        val document = (result as TvTimeParseResult.Success).document
        assertEquals(1, document.summary.movieRecordCount)
        assertEquals(1, document.summary.seriesCount)
        assertEquals(1, document.summary.episodeCount)
        assertEquals(1, document.summary.watchedMovieCount)
        assertEquals(1, document.summary.watchedEpisodeCount)
        assertEquals("example movie", document.movies.single().normalizedTitle)
        assertEquals(4, document.movies.single().createdAt?.fractionalDigits)
        assertTrue(document.warnings.any { it.code == ImportWarningCode.UNKNOWN_FIELD })
        assertEquals(2, document.summary.unsupported.favoriteRecords)
    }

    @Test
    fun rejectsDuplicateJsonKeys() = runTest {
        val gateway =
            FakeZipGateway(
                movieJson().replace(
                    "\"title\":\" Example Movie \"",
                    "\"title\":\" Example Movie \",\"title\":\"Duplicate\""
                )
            )
        val result = parser(gateway).parseArchiveForTest(gateway.archive)
        assertEquals(
            TvTimeParseFailureKind.DUPLICATE_JSON_KEY,
            (result as TvTimeParseResult.Failure).failure.kind
        )
    }

    @Test
    fun watchedRecordWithoutTimestampIsReportedAsInvalid() = runTest {
        val gateway =
            FakeZipGateway(movieJson().replace("\"watched_at\":\"2024-02-03T04:05:06Z\"", "\"watched_at\":null"))
        val result = parser(gateway).parseArchiveForTest(gateway.archive)
        assertTrue(result is TvTimeParseResult.Success)
        val document = (result as TvTimeParseResult.Success).document
        assertEquals(1, document.summary.invalidRecordCount)
        assertEquals(0, document.movies.size)
    }

    @Test
    fun missingCreationTimestampIsRetainedAsAnApproximationWarning() = runTest {
        val gateway = FakeZipGateway(movieJson().replace("\"created_at\":\"2024-01-01T00:00:00.1234Z\",", ""))
        val result = parser(gateway).parseArchiveForTest(gateway.archive)
        val document = (result as TvTimeParseResult.Success).document
        assertEquals(null, document.movies.single().createdAt)
        assertTrue(document.movies.single().warnings.any { it.code == ImportWarningCode.APPROXIMATE_TIMESTAMP })
    }

    private fun parser(gateway: FakeZipGateway): TvTimeSourceParser = TvTimeSourceParser(gateway)

    private class FakeZipGateway(movieJson: String) : TvTimeZipGateway {
        private val values = listOf(
            "[{\"id\":\"list-1\",\"name\":\"Synthetic list\",\"description\":\"Synthetic\",\"is_public\":false,\"created_at\":\"2024-01-01T00:00:00Z\",\"items\":[{\"custom_order\":0,\"name\":\"Example Movie\",\"type\":\"movie\",\"tvdb_id\":101,\"uuid\":\"11111111-1111-1111-1111-111111111111\"}]}]",
            movieJson,
            "[{\"_noEpisodeData\":false,\"created_at\":\"2024-01-01T00:00:00Z\",\"id\":{\"imdb\":null,\"tvdb\":202},\"is_favorite\":false,\"seasons\":[{\"episodes\":[{\"id\":{\"imdb\":null,\"tvdb\":303},\"is_watched\":true,\"name\":\"Pilot\",\"number\":1,\"rewatch_count\":0,\"special\":false,\"watched_at\":\"2024-02-03T04:05:06Z\",\"watched_count\":1}],\"is_specials\":false,\"number\":1}],\"status\":\"ended\",\"title\":\"Example Series\",\"uuid\":\"22222222-2222-2222-2222-222222222222\"}]",
            "<html>synthetic auxiliary report</html>"
        )
        val archive: TvTimeArchive = object : TvTimeArchive {
            override val entries = values.mapIndexed { index, value ->
                TvTimeArchiveEntry(
                    index = index,
                    kind = if (index == 3) TvTimeArchiveEntryKind.HTML else TvTimeArchiveEntryKind.JSON,
                    compressedSize = value.length.toLong(),
                    uncompressedSize = value.length.toLong()
                )
            }

            override fun open(entry: TvTimeArchiveEntry): InputStream =
                ByteArrayInputStream(values[entry.index].toByteArray(StandardCharsets.UTF_8))

            override fun close() = Unit
        }

        override suspend fun <T> withArchive(uri: Uri, block: suspend (TvTimeArchive) -> T): TvTimeArchiveResult<T> =
            TvTimeArchiveResult.Success(block(archive))
    }

    private companion object {
        fun movieJson(): String =
            "[{\"created_at\":\"2024-01-01T00:00:00.1234Z\",\"id\":{\"imdb\":\"tt1234567\",\"tvdb\":101},\"is_favorite\":true,\"is_watched\":true,\"rewatch_count\":2,\"title\":\" Example Movie \",\"uuid\":\"11111111-1111-1111-1111-111111111111\",\"watched_at\":\"2024-02-03T04:05:06Z\",\"year\":2020,\"new_field\":\"ignored\"}]"
    }
}
