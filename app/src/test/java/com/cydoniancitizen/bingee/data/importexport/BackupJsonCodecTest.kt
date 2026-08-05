package com.cydoniancitizen.bingee.data.importexport

import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupJsonCodecTest {
    @Test
    fun encodesStableContractAndPortableOnlyFields() {
        val json = BackupJsonCodec.encode(fullDocument()).toString(Charsets.UTF_8)

        assertTrue(json.contains("\"formatId\": \"bingee-backup\""))
        assertTrue(json.contains("\"schemaVersion\": 2"))
        assertTrue(json.contains("\"exportedAt\": \"2026-08-04T10:00:00Z\""))
        assertTrue(json.contains("\"mediaType\": \"MOVIE\""))
        assertTrue(json.contains("\"releaseDate\": \"2026-01-02\""))
        assertFalse(json.contains("localMediaId"))
        assertFalse(json.contains("local_media_id"))
        assertFalse(json.contains("token"))
        assertFalse(json.contains("authorization"))
        assertFalse(json.contains("workManager"))
        assertFalse(json.contains("freshness"))
        assertFalse(json.contains("network"))
        assertFalse(json.contains("lastCheckedAt"))
        assertTrue(json.indexOf("\"media\"") < json.indexOf("\"seasons\""))
    }

    @Test
    fun roundTripsUtf8AndDates() {
        val original = fullDocument()
        val result = BackupJsonCodec.parse(BackupJsonCodec.encode(original))

        assertTrue(result is BackupParseResult.Success)
        assertEquals(original, (result as BackupParseResult.Success).document)
    }

    @Test
    fun committedV1FixtureRemainsAccepted() {
        val fixture = listOf(
            Path.of("docs", "backup", "fixtures", "valid-full.json"),
            Path.of("..", "docs", "backup", "fixtures", "valid-full.json")
        ).first { Files.isRegularFile(it) }
        val parsed = BackupJsonCodec.parse(Files.readAllBytes(fixture))

        assertTrue(parsed is BackupParseResult.Success)
        val document = (parsed as BackupParseResult.Success).document
        assertEquals(BACKUP_SCHEMA_VERSION_V1, document.schemaVersion)
        assertTrue(BackupValidator.validate(document) is BackupValidationResult.Success)
    }

    @Test
    fun rejectsMalformedWrongFormatMissingAndNewerVersion() {
        assertEquals(
            BackupFailureKind.MALFORMED_JSON,
            (BackupJsonCodec.parse("{".toByteArray()) as BackupParseResult.Failure).failure.kind
        )
        val valid = BackupJsonCodec.encode(fullDocument()).toString(Charsets.UTF_8)
        assertEquals(
            BackupFailureKind.WRONG_FORMAT,
            (
                BackupJsonCodec.parse(
                    valid.replace("bingee-backup", "other").toByteArray()
                ) as BackupParseResult.Failure
                ).failure.kind
        )
        assertEquals(
            BackupFailureKind.MISSING_VERSION,
            (
                BackupJsonCodec.parse(
                    valid.replace("\"schemaVersion\": 2,\n", "").toByteArray()
                ) as BackupParseResult.Failure
                ).failure.kind
        )
        assertEquals(
            BackupFailureKind.UNSUPPORTED_VERSION,
            (
                BackupJsonCodec.parse(
                    valid.replace("\"schemaVersion\": 2", "\"schemaVersion\": 3").toByteArray()
                ) as BackupParseResult.Failure
                ).failure.kind
        )
    }

    @Test
    fun rejectsInvalidUtf8AndOversizedInput() {
        assertEquals(
            BackupFailureKind.INVALID_UTF8,
            (BackupJsonCodec.parse(byteArrayOf(0xC3.toByte(), 0x28)) as BackupParseResult.Failure).failure.kind
        )
        assertEquals(
            BackupFailureKind.TOO_LARGE,
            (BackupJsonCodec.parse(ByteArray(MAX_BACKUP_BYTES + 1)) as BackupParseResult.Failure).failure.kind
        )
    }

    private fun fullDocument() = BackupDocument(
        formatId = BACKUP_FORMAT_ID,
        schemaVersion = BACKUP_SCHEMA_VERSION,
        exportedAt = Instant.parse("2026-08-04T10:00:00Z"),
        data = BackupData(
            media = listOf(
                BackupMedia(
                    primaryRef = BackupRef(MediaSource.TMDB, "1"),
                    externalRefs = listOf(BackupRef(MediaSource.TMDB, "1")),
                    mediaType = MediaType.MOVIE,
                    title = "Luce 東京",
                    originalTitle = "Light",
                    overview = null,
                    posterUrl = null,
                    releaseDate = LocalDate.parse("2026-01-02")
                )
            ),
            seasons = emptyList(),
            episodes = emptyList(),
            library = listOf(
                BackupLibraryEntry(BackupRef(MediaSource.TMDB, "1"), Instant.parse("2026-01-03T00:00:00Z"))
            ),
            movieProgress = listOf(
                BackupMovieProgress(BackupRef(MediaSource.TMDB, "1"), Instant.parse("2026-01-04T00:00:00Z"))
            ),
            episodeProgress = emptyList(),
            ratings = listOf(
                BackupRating(
                    BackupRef(MediaSource.TMDB, "1"),
                    8,
                    Instant.parse("2026-01-05T00:00:00Z"),
                    Instant.parse("2026-01-05T00:00:00Z")
                )
            ),
            preferences = BackupPreferences(1, true, false, true)
        )
    )
}
