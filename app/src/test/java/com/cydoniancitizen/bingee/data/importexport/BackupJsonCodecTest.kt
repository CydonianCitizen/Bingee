package com.cydoniancitizen.bingee.data.importexport

import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupJsonCodecTest {
    private val validationDate = LocalDate.of(2026, 8, 18)

    @Test
    fun encodesStableContractAndPortableOnlyFields() {
        val json = BackupJsonCodec.encode(fullDocument()).toString(Charsets.UTF_8)

        assertTrue(json.contains("\"formatId\": \"bingee-backup\""))
        assertTrue(json.contains("\"schemaVersion\": 1"))
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
        assertFalse(json.contains("mediaLinkGroups"))
        assertFalse(json.contains("mediaLinkAudit"))
        assertTrue(json.indexOf("\"media\"") < json.indexOf("\"seasons\""))
    }

    @Test
    fun productionPayloadMatchesCanonicalV1SchemaAndParser() {
        val encoded = BackupJsonCodec.encode(fullDocument())
        val payload = JsonParser.parseString(encoded.toString(Charsets.UTF_8))
        val schema = JsonParser.parseString(Files.readString(schemaPath()))
        val errors = validateSchema(schema, payload, schema.asJsonObject, "$")

        assertTrue(errors.joinToString("\n"), errors.isEmpty())
        val parsed = BackupJsonCodec.parse(payload.toString().toByteArray(Charsets.UTF_8))
        assertTrue(parsed is BackupParseResult.Success)
        assertTrue(
            validate((parsed as BackupParseResult.Success).document) is BackupValidationResult.Success
        )

        payload.asJsonObject.addProperty("futureField", true)
        payload.asJsonObject.getAsJsonObject("data").addProperty("futureDataField", "ignored")
        val additiveResult = BackupJsonCodec.parse(payload.toString().toByteArray(Charsets.UTF_8))
        assertTrue(additiveResult is BackupParseResult.Success)
    }

    @Test
    fun roundTripsUtf8AndDates() {
        val original = fullDocument()
        val result = BackupJsonCodec.parse(BackupJsonCodec.encode(original))

        assertTrue(result is BackupParseResult.Success)
        assertEquals(original, (result as BackupParseResult.Success).document)
    }

    @Test
    fun abandonedSeriesIsOptionalAndRoundTripsInBackupV1() {
        val ref = BackupRef(MediaSource.TMDB, "1399")
        val document = fullDocument().copy(
            data = fullDocument().data.copy(
                media = listOf(
                    BackupMedia(ref, listOf(ref), MediaType.SERIES, "Series", null, null, null, null)
                ),
                library = listOf(BackupLibraryEntry(ref, Instant.EPOCH)),
                movieProgress = emptyList(),
                ratings = emptyList(),
                abandonedSeries = listOf(BackupAbandonedSeries(ref))
            )
        )

        val parsed = BackupJsonCodec.parse(BackupJsonCodec.encode(document)) as BackupParseResult.Success

        assertEquals(listOf(BackupAbandonedSeries(ref)), parsed.document.data.abandonedSeries)
        assertTrue(validate(parsed.document) is BackupValidationResult.Success)
    }

    @Test
    fun favoriteChronologyRoundTripsExactlyAndStaysOptionalForLegacyBackups() {
        val ref = BackupRef(MediaSource.TMDB, "1")
        val favoriteAddedAt = Instant.parse("2026-02-03T04:05:06Z")
        val document = fullDocument().let { base ->
            base.copy(
                data = base.data.copy(
                    media = listOf(
                        BackupMedia(
                            primaryRef = ref,
                            externalRefs = listOf(ref),
                            mediaType = MediaType.MOVIE,
                            title = "Favorite",
                            originalTitle = null,
                            overview = null,
                            posterUrl = null,
                            releaseDate = null,
                            isFavorite = true,
                            favoriteAddedAt = favoriteAddedAt
                        )
                    )
                )
            )
        }

        val encoded = BackupJsonCodec.encode(document).toString(Charsets.UTF_8)
        assertTrue(encoded.contains("\"favoriteAddedAt\": \"2026-02-03T04:05:06Z\""))
        val parsed = BackupJsonCodec.parse(encoded.toByteArray(Charsets.UTF_8)) as BackupParseResult.Success
        assertEquals(favoriteAddedAt, parsed.document.data.media.single().favoriteAddedAt)
        assertTrue(validate(parsed.document) is BackupValidationResult.Success)

        // A backup written before v4 chronology existed carries the flag without the timestamp. It
        // must still restore as a favorite, with chronology left unknown rather than invented.
        val legacy = JsonParser.parseString(encoded).asJsonObject
        legacy.getAsJsonObject("data").getAsJsonArray("media").forEach { entry ->
            entry.asJsonObject.remove("favoriteAddedAt")
        }
        val legacyParsed = BackupJsonCodec.parse(
            legacy.toString().toByteArray(Charsets.UTF_8)
        ) as BackupParseResult.Success
        val legacyMedia = legacyParsed.document.data.media.single()

        assertTrue(legacyMedia.isFavorite)
        assertNull(legacyMedia.favoriteAddedAt)
        assertTrue(validate(legacyParsed.document) is BackupValidationResult.Success)
    }

    @Test
    fun ratingKeepsRatedAtAndUpdatedAtIndependent() {
        val ref = BackupRef(MediaSource.TMDB, "1")
        val ratedAt = Instant.parse("2026-01-05T00:00:00Z")
        val updatedAt = Instant.parse("2026-04-09T11:22:33Z")
        val document = fullDocument().let { base ->
            base.copy(data = base.data.copy(ratings = listOf(BackupRating(ref, 8, ratedAt, updatedAt))))
        }

        val parsed = BackupJsonCodec.parse(BackupJsonCodec.encode(document)) as BackupParseResult.Success
        val rating = parsed.document.data.ratings.single()

        assertEquals(ratedAt, rating.ratedAt)
        assertEquals(updatedAt, rating.updatedAt)
        assertTrue(validate(parsed.document) is BackupValidationResult.Success)
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
        assertTrue(validate(document) is BackupValidationResult.Success)
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
        val missingVersionResult = BackupJsonCodec.parse(valid.replace("\"schemaVersion\": 1,\n", "").toByteArray())
        assertTrue(missingVersionResult is BackupParseResult.Failure)

        assertEquals(
            BackupFailureKind.UNSUPPORTED_VERSION,
            (
                BackupJsonCodec.parse(
                    valid.replace("\"schemaVersion\": 1", "\"schemaVersion\": 5").toByteArray()
                ) as BackupParseResult.Failure
                ).failure.kind
        )
    }

    private fun validate(document: BackupDocument) = BackupValidator.validate(document, validationDate)

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

    @Test
    fun integerFieldsRequireExactIntValues() {
        assertEquals(1, parseSchemaVersion("1"))
        assertEquals(1, parseSchemaVersion("1e0"))
        assertEquals(0, parseNotificationLeadDays("0"))
        assertEquals(-1, parseNotificationLeadDays("-1"))
        listOf("1.5", "2147483648", "1e-1").forEach { value ->
            assertEquals(BackupFailureKind.INVALID_STRUCTURE, parseSchemaVersionFailure(value))
        }
    }

    private fun parseSchemaVersion(value: String): Int = (
        BackupJsonCodec.parse(
            replaceInteger("schemaVersion", value)
        ) as BackupParseResult.Success
        ).document.schemaVersion

    private fun parseNotificationLeadDays(value: String): Int = (
        BackupJsonCodec.parse(replaceInteger("notificationLeadDays", value)) as BackupParseResult.Success
        ).document.data.preferences.notificationLeadDays

    private fun parseSchemaVersionFailure(value: String): BackupFailureKind =
        (BackupJsonCodec.parse(replaceInteger("schemaVersion", value)) as BackupParseResult.Failure).failure.kind

    private fun replaceInteger(key: String, value: String): ByteArray = BackupJsonCodec.encode(fullDocument())
        .toString(Charsets.UTF_8)
        .replace("\"$key\": 1", "\"$key\": $value")
        .toByteArray(Charsets.UTF_8)

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

    private fun schemaPath(): Path = listOf(
        Path.of("docs", "backup", "bingee-backup-v1.schema.json"),
        Path.of("..", "docs", "backup", "bingee-backup-v1.schema.json")
    ).first { Files.isRegularFile(it) }

    // ponytail: validator covers schema keywords used here; add a library if contract grows beyond this subset.
    private fun validateSchema(schema: JsonElement, value: JsonElement, root: JsonObject, path: String): List<String> {
        val resolved = schema.asJsonObject.get("\$ref")?.let { ref ->
            ref.asString.removePrefix("#/").split('/').fold(root as JsonElement) { current, part ->
                current.asJsonObject.get(part)!!
            }
        } ?: schema
        val definition = resolved.asJsonObject
        val errors = mutableListOf<String>()
        val types = definition.get("type")?.let { type ->
            if (type.isJsonArray) type.asJsonArray.map { it.asString } else listOf(type.asString)
        }
        if (types != null && types.none { matchesType(value, it) }) {
            return listOf("$path: type")
        }
        definition.get("const")?.let { if (it != value) errors += "$path: const" }
        definition.get("enum")?.let { enums ->
            if (enums.asJsonArray.none { it == value }) errors += "$path: enum"
        }
        if (value.isJsonObject) {
            val jsonObject = value.asJsonObject
            definition.getAsJsonArray("required")?.forEach { required ->
                if (!jsonObject.has(required.asString)) errors += "$path.${required.asString}: required"
            }
            val properties = definition.getAsJsonObject("properties")
            properties?.entrySet()?.forEach { (name, propertySchema) ->
                if (jsonObject.has(name)) {
                    errors += validateSchema(propertySchema, jsonObject.get(name), root, "$path.$name")
                }
            }
            if (definition.get("additionalProperties")?.asBoolean == false && properties != null) {
                jsonObject.keySet().filterNot { it in properties.keySet() }.forEach { name ->
                    errors += "$path.$name: additional property"
                }
            }
        }
        if (value.isJsonArray) {
            val array = value.asJsonArray
            definition.get("minItems")?.let { if (array.size() < it.asInt) errors += "$path: minItems" }
            definition.get("maxItems")?.let { if (array.size() > it.asInt) errors += "$path: maxItems" }
            definition.get("items")?.let { itemSchema ->
                array.forEachIndexed { index, item ->
                    errors += validateSchema(itemSchema, item, root, "$path[$index]")
                }
            }
        }
        if (value.isJsonPrimitive && value.asJsonPrimitive.isString) {
            val string = value.asString
            definition.get("minLength")?.let { if (string.length < it.asInt) errors += "$path: minLength" }
            definition.get("maxLength")?.let { if (string.length > it.asInt) errors += "$path: maxLength" }
            definition.get("pattern")?.let {
                if (!Regex(it.asString).containsMatchIn(string)) errors += "$path: pattern"
            }
            when (definition.get("format")?.asString) {
                "date" -> runCatching { LocalDate.parse(string) }.onFailure { errors += "$path: date" }
                "date-time" -> runCatching { Instant.parse(string) }.onFailure { errors += "$path: date-time" }
            }
        }
        if (value.isJsonPrimitive && value.asJsonPrimitive.isNumber) {
            val number = value.asDouble
            definition.get("minimum")?.let { if (number < it.asDouble) errors += "$path: minimum" }
            definition.get("maximum")?.let { if (number > it.asDouble) errors += "$path: maximum" }
        }
        return errors
    }

    private fun matchesType(value: JsonElement, type: String): Boolean = when (type) {
        "object" -> value.isJsonObject
        "array" -> value.isJsonArray
        "string" -> value.isJsonPrimitive && value.asJsonPrimitive.isString
        "integer" -> value.isJsonPrimitive && value.asJsonPrimitive.isNumber && value.asDouble % 1 == 0.0
        "number" -> value.isJsonPrimitive && value.asJsonPrimitive.isNumber
        "boolean" -> value.isJsonPrimitive && value.asJsonPrimitive.isBoolean
        "null" -> value.isJsonNull
        else -> false
    }
}
