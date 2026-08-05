package com.cydoniancitizen.bingee.data.imports.tvtime

import android.net.Uri
import com.cydoniancitizen.bingee.data.imports.model.ImportWarningCode
import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TvTimeSourceValidationTest {
    @Test
    fun requiresExactlyOneOfEveryJsonRole() = runTest {
        assertFailure(archive(LIST, MOVIE), TvTimeParseFailureKind.MISSING_ROLE)
        assertFailure(archive(LIST, MOVIE, MOVIE, SERIES), TvTimeParseFailureKind.DUPLICATE_ROLE)
        assertFailure(archive(LIST, MOVIE, SERIES, "[{\"unexpected\":true}]"), TvTimeParseFailureKind.UNKNOWN_ROLE)
    }

    @Test
    fun rejectsEmptyAmbiguousAndRoleChangingArrays() = runTest {
        assertFailure(archive(LIST, "[]", SERIES), TvTimeParseFailureKind.EMPTY_ARRAY)
        val ambiguous =
            "[{\"items\":[],\"is_public\":false,\"description\":\"\",\"year\":2020," +
                "\"watched_at\":null,\"is_watched\":false}]"
        assertFailure(archive(ambiguous, MOVIE, SERIES), TvTimeParseFailureKind.AMBIGUOUS_ROLE)
        val changed = MOVIE.dropLast(1) + "," + SERIES.removePrefix("[")
        assertFailure(archive(LIST, changed, SERIES), TvTimeParseFailureKind.INVALID_STRUCTURE)
    }

    @Test
    fun rejectsMalformedJsonInvalidUtf8AndDuplicateKeys() = runTest {
        assertFailure(archive(LIST, MOVIE.dropLast(2), SERIES), TvTimeParseFailureKind.MALFORMED_JSON)
        val invalidUtf8 = Archive(
            listOf(
                LIST.toByteArray(),
                byteArrayOf('['.code.toByte(), 0xc3.toByte(), 0x28.toByte(), ']'.code.toByte()),
                SERIES.toByteArray()
            )
        )
        assertFailure(invalidUtf8, TvTimeParseFailureKind.INVALID_UTF8)
        assertFailure(
            archive(LIST, MOVIE.replace("\"title\":\"Movie\"", "\"title\":\"Movie\",\"title\":\"Again\""), SERIES),
            TvTimeParseFailureKind.DUPLICATE_JSON_KEY
        )
    }

    @Test
    fun reportsSafeSemanticMovieErrorsWithoutDroppingArchive() = runTest {
        val invalidRecords = listOf(
            MOVIE.replace("\"year\":2020", "\"year\":999"),
            MOVIE.replace("\"watched_at\":\"2024-02-03T04:05:06Z\"", "\"watched_at\":null"),
            MOVIE.replace("\"is_watched\":true", "\"is_watched\":false"),
            MOVIE.replace(UUID_1, "not-a-uuid")
        )
        invalidRecords.forEach { movie ->
            val document = success(archive(LIST, movie, SERIES))
            assertEquals(0, document.movies.size)
            assertEquals(1, document.summary.invalidRecordCount)
        }
    }

    @Test
    fun strictTimestampsRejectOffsetsMissingZuluAndInvalidDates() = runTest {
        val invalidValues = listOf(
            "2024-02-03T05:05:06+01:00",
            "2024-02-03T04:05:06",
            "2024-02-31T04:05:06Z"
        )
        invalidValues.forEach { value ->
            val document = success(
                archive(LIST, MOVIE.replace("2024-02-03T04:05:06Z", value), SERIES)
            )
            assertEquals(1, document.summary.invalidRecordCount)
        }
    }

    @Test
    fun detectsDuplicateSeasonAndEpisodeNumbersWithoutIds() = runTest {
        val duplicateSeason = SERIES.replace(SEASON, "$SEASON,$SEASON")
        assertFailure(archive(LIST, MOVIE, duplicateSeason), TvTimeParseFailureKind.DUPLICATE_IDENTITY)

        val noIdsEpisode = EPISODE.replace("\"tvdb\":303", "\"tvdb\":null")
        val duplicateEpisode = SERIES.replace(EPISODE, "$noIdsEpisode,$noIdsEpisode")
        assertFailure(archive(LIST, MOVIE, duplicateEpisode), TvTimeParseFailureKind.DUPLICATE_IDENTITY)
    }

    @Test
    fun duplicateIdentityInInvalidSeriesDoesNotPoisonLaterSafeRecord() = runTest {
        val invalid = SERIES_OBJECT.replace("\"number\":1", "\"number\":-1")
        val validWithNewUuid = SERIES_OBJECT.replace(UUID_2, UUID_3)
        val document = success(archive(LIST, MOVIE, "[$invalid,$validWithNewUuid]"))
        assertEquals(1, document.series.size)
        assertEquals(1, document.summary.invalidRecordCount)
    }

    @Test
    fun preservesSeasonZeroHighNumbersUnicodeAndUnsupportedAggregates() = runTest {
        val high = "{\"episodes\":[],\"is_specials\":false,\"number\":2042}"
        val expanded = SERIES.replace(SEASON, "$SEASON,$high")
            .replace("\"Series\"", "\"Séries, Δ\"")
        val document = success(archive(LIST, MOVIE, expanded))
        assertEquals(2, document.summary.seasonCount)
        assertTrue(document.warnings.any { it.code == ImportWarningCode.HIGH_SEASON_NUMBER })
        assertEquals(1, document.summary.unsupported.sourceStatusRecords)
        assertEquals(1, document.summary.unsupported.technicalFlagRecords)
        assertEquals(0, document.summary.ratingsImported)
        assertFalse(document.series.single().normalizedTitle.isBlank())
    }

    @Test
    fun listOrphanAndUnknownFieldsProduceNamesOnlyWarnings() = runTest {
        val list = LIST.replace(UUID_1, UUID_4)
        val first = MOVIE.removeSurrounding("[", "]")
            .replace("\"year\":2020", "\"year\":2020,\"future_field\":{\"private\":\"not retained\"}")
        val second = first.replace(UUID_1, UUID_3)
            .replace("tt1234567", "tt7654321")
            .replace("\"tvdb\":101", "\"tvdb\":102")
            .replace("\"Movie\"", "\"Another\"")
        val movie = "[$first,$second]"
        val document = success(archive(list, movie, SERIES))
        assertTrue(document.warnings.any { it.code == ImportWarningCode.ORPHAN_LIST_LINK })
        val unknown = document.warnings.single { it.fieldName == "future_field" }
        assertEquals(ImportWarningCode.UNKNOWN_FIELD, unknown.code)
        assertEquals(2, unknown.occurrenceCount)
        assertFalse(unknown.toString().contains("not retained"))
    }

    private suspend fun success(archive: TvTimeArchive) =
        (parser().parseArchiveForTest(archive) as TvTimeParseResult.Success).document

    private suspend fun assertFailure(archive: TvTimeArchive, expected: TvTimeParseFailureKind) {
        val result = parser().parseArchiveForTest(archive)
        assertTrue("Expected $expected but was $result", result is TvTimeParseResult.Failure)
        assertEquals(expected, (result as TvTimeParseResult.Failure).failure.kind)
    }

    private fun parser() = TvTimeSourceParser(NoOpGateway)

    private fun archive(vararg values: String): TvTimeArchive = Archive(values.map(String::toByteArray))

    private class Archive(private val values: List<ByteArray>) : TvTimeArchive {
        override val entries = values.mapIndexed { index, value ->
            TvTimeArchiveEntry(index, TvTimeArchiveEntryKind.JSON, value.size.toLong(), value.size.toLong())
        }

        override fun open(entry: TvTimeArchiveEntry): InputStream = ByteArrayInputStream(values[entry.index])

        override fun close() = Unit
    }

    private data object NoOpGateway : TvTimeZipGateway {
        override suspend fun <T> withArchive(uri: Uri, block: suspend (TvTimeArchive) -> T): TvTimeArchiveResult<T> =
            error("not used")
    }

    private companion object {
        const val UUID_1 = "11111111-1111-4111-8111-111111111111"
        const val UUID_2 = "22222222-2222-4222-8222-222222222222"
        const val UUID_3 = "33333333-3333-4333-8333-333333333333"
        const val UUID_4 = "44444444-4444-4444-8444-444444444444"
        const val LIST =
            "[{\"id\":\"list\",\"name\":\"List\",\"description\":\"\",\"is_public\":false," +
                "\"created_at\":\"2024-01-01T00:00:00Z\",\"items\":[{\"custom_order\":0," +
                "\"name\":\"Movie\",\"type\":\"movie\",\"tvdb_id\":null,\"uuid\":\"$UUID_1\"}]}]"
        const val MOVIE =
            "[{\"created_at\":\"2024-01-01T00:00:00.1234Z\",\"id\":{\"imdb\":\"tt1234567\"," +
                "\"tvdb\":101},\"is_favorite\":false,\"is_watched\":true,\"rewatch_count\":0," +
                "\"title\":\"Movie\",\"uuid\":\"$UUID_1\",\"watched_at\":\"2024-02-03T04:05:06Z\"," +
                "\"year\":2020}]"
        const val EPISODE =
            "{\"id\":{\"imdb\":null,\"tvdb\":303},\"is_watched\":true,\"name\":\"Pilot\"," +
                "\"number\":1,\"rewatch_count\":0,\"special\":false," +
                "\"watched_at\":\"2024-02-03T04:05:06Z\",\"watched_count\":1}"
        const val SEASON = "{\"episodes\":[$EPISODE],\"is_specials\":false,\"number\":1}"
        const val SERIES_OBJECT =
            "{\"_noEpisodeData\":false,\"created_at\":\"2024-01-01T00:00:00Z\"," +
                "\"id\":{\"imdb\":null,\"tvdb\":202},\"is_favorite\":false,\"seasons\":[$SEASON]," +
                "\"status\":\"ended\",\"title\":\"Series\",\"uuid\":\"$UUID_2\"}"
        const val SERIES = "[$SERIES_OBJECT]"
    }
}
