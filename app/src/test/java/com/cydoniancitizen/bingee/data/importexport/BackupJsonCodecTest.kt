package com.cydoniancitizen.bingee.data.importexport

import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.data.settings.AppLanguage
import com.cydoniancitizen.bingee.data.settings.AppTheme
import com.cydoniancitizen.bingee.data.settings.ProfileDisplayModes
import com.cydoniancitizen.bingee.data.settings.ProfileViewMode
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
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
    fun emptySeasonEvidenceRoundTripsAndOlderBackupsDefaultToUnknown() {
        val document = emptySeasonDocument()
        val parsed = BackupJsonCodec.parse(BackupJsonCodec.encode(document)) as BackupParseResult.Success
        assertEquals(document, parsed.document)
        assertTrue(validate(parsed.document) is BackupValidationResult.Success)
        val schema = JsonParser.parseString(resource("backup/bingee-backup-v3.schema.json").toString(Charsets.UTF_8))
        val payload = JsonParser.parseString(BackupJsonCodec.encode(document).toString(Charsets.UTF_8)).asJsonObject
        assertTrue(validateSchema(schema, payload, schema.asJsonObject, "$").isEmpty())
        val season = payload.getAsJsonObject("data").getAsJsonArray("seasons")[0].asJsonObject
        season.remove("isKnownEmpty")
        listOf(1, 2).forEach { version ->
            payload.addProperty("schemaVersion", version)
            val legacy = BackupJsonCodec.parse(payload.toString().toByteArray()) as BackupParseResult.Success
            assertFalse(legacy.document.data.seasons.single().isKnownEmpty)
            assertTrue(validate(legacy.document) is BackupValidationResult.Success)
        }
        listOf("null", "1", "\"true\"").forEach { invalid ->
            season.add("isKnownEmpty", JsonParser.parseString(invalid))
            assertTrue(BackupJsonCodec.parse(payload.toString().toByteArray()) is BackupParseResult.Failure)
        }
    }

    @Test
    fun contradictoryEmptySeasonEvidenceIsRejectedBeforeRestore() {
        val document = emptySeasonDocument()
        val empty = document.data.seasons.single()
        val declaredEpisode = document.copy(data = document.data.copy(seasons = listOf(empty.copy(episodeCount = 1))))
        assertTrue(validate(declaredEpisode) is BackupValidationResult.Failure)
        val storedEpisode = BackupEpisode(
            empty.externalRef,
            BackupRef(MediaSource.TMDB, "30"),
            1,
            "Episode",
            null,
            null,
            null,
            null
        )
        assertTrue(
            validate(document.copy(data = document.data.copy(episodes = listOf(storedEpisode))))
                is BackupValidationResult.Failure
        )
    }

    private fun emptySeasonDocument(): BackupDocument = fullDocument().let { document ->
        val series = document.data.media.single().copy(mediaType = MediaType.SERIES)
        document.copy(
            data = document.data.copy(
                media = listOf(series),
                seasons = listOf(
                    BackupSeason(
                        series.primaryRef, BackupRef(MediaSource.TMDB, "20"), 1,
                        null, null, null, null, 0, isKnownEmpty = true
                    )
                ),
                movieProgress = emptyList()
            )
        )
    }

    @Test
    fun genreIdentityNamesAndOrderRoundTripIncludingLegacyNames() {
        val genres = listOf(
            BackupGenre("Dramma", MediaSource.TMDB, 18),
            BackupGenre("Commedia", MediaSource.TMDB, Long.MAX_VALUE),
            BackupGenre("Legacy", null, null)
        )
        val document = withGenres(genres)
        val parsed = BackupJsonCodec.parse(BackupJsonCodec.encode(document)) as BackupParseResult.Success
        assertEquals(document, parsed.document)
        assertTrue(validate(parsed.document) is BackupValidationResult.Success)
        val schema = JsonParser.parseString(resource("backup/bingee-backup-v3.schema.json").toString(Charsets.UTF_8))
        val payload = JsonParser.parseString(BackupJsonCodec.encode(document).toString(Charsets.UTF_8))
        assertTrue(validateSchema(schema, payload, schema.asJsonObject, "$").isEmpty())
    }

    @Test
    fun invalidGenreIdentityOrNameRejectsWholeBackup() {
        listOf(
            BackupGenre(" ", MediaSource.TMDB, 18),
            BackupGenre("Drama", null, 18),
            BackupGenre("Drama", MediaSource.TMDB, null),
            BackupGenre("Drama", MediaSource.TMDB, 0),
            BackupGenre("Drama", MediaSource.TMDB, -1),
            BackupGenre("x".repeat(BackupLimits.MAX_STRING + 1), null, null)
        ).forEach { genre ->
            assertTrue(validate(withGenres(listOf(genre))) is BackupValidationResult.Failure)
        }
        assertTrue(
            validate(withGenres(List(101) { BackupGenre("Drama", MediaSource.TMDB, 18) }))
                is BackupValidationResult.Failure
        )
    }

    @Test
    fun v2RequiresGenresAndExactLongIdentityWhileV1MayOmitGenres() {
        val payload = JsonParser.parseString(BackupJsonCodec.encode(withGenres(emptyList())).toString(Charsets.UTF_8))
            .asJsonObject
        val media = payload.getAsJsonObject("data").getAsJsonArray("media")[0].asJsonObject
        media.remove("genres")
        payload.addProperty("schemaVersion", BACKUP_SCHEMA_VERSION_V2)
        assertTrue(BackupJsonCodec.parse(payload.toString().toByteArray()) is BackupParseResult.Failure)
        payload.addProperty("schemaVersion", 1)
        val legacy = BackupJsonCodec.parse(payload.toString().toByteArray()) as BackupParseResult.Success
        assertTrue(legacy.document.data.media.single().genres.isEmpty())
        assertTrue(validate(legacy.document) is BackupValidationResult.Success)

        val json = BackupJsonCodec.encode(withGenres(listOf(BackupGenre("Drama", MediaSource.TMDB, 18))))
            .toString(Charsets.UTF_8)
        listOf("1.5", "9223372036854775808", "\"18\"").forEach { id ->
            assertTrue(
                BackupJsonCodec.parse(json.replace("\"genreId\": 18", "\"genreId\": $id").toByteArray())
                    is BackupParseResult.Failure
            )
        }
    }

    private fun withGenres(genres: List<BackupGenre>): BackupDocument = fullDocument().let { document ->
        document.copy(data = document.data.copy(media = document.data.media.map { it.copy(genres = genres) }))
    }

    @Test
    fun encodesStableContractAndPortableOnlyFields() {
        val json = BackupJsonCodec.encode(fullDocument()).toString(Charsets.UTF_8)

        assertTrue(json.contains("\"formatId\": \"bingee-backup\""))
        assertTrue(json.contains("\"schemaVersion\": 3"))
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
    fun productionPayloadMatchesCanonicalV3SchemaAndParser() {
        val encoded = BackupJsonCodec.encode(fullDocument())
        val payload = JsonParser.parseString(encoded.toString(Charsets.UTF_8))
        val schema = JsonParser.parseString(resource("backup/bingee-backup-v3.schema.json").toString(Charsets.UTF_8))
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
    fun v3RoundTripsPortablePreferencesWhileV1AndV2UseTheirDefaults() {
        val preferences = BackupPreferences(
            notificationLeadDays = 7,
            notifyMovieReleases = false,
            notifySeasonPremieres = true,
            notifyEpisodeAirings = false,
            theme = AppTheme.DARK,
            language = AppLanguage.ITALIAN,
            hideEpisodeSpoilers = true,
            profileDisplayModes = ProfileDisplayModes(
                watchedMovies = ProfileViewMode.GRID,
                watchedTvSeries = ProfileViewMode.LIST,
                watchLaterMovies = ProfileViewMode.GRID,
                watchLaterTvSeries = ProfileViewMode.GRID,
                favoritesMovies = ProfileViewMode.LIST,
                favoritesTvSeries = ProfileViewMode.GRID
            )
        )
        val document = fullDocument().copy(data = fullDocument().data.copy(preferences = preferences))
        val current = BackupJsonCodec.parse(BackupJsonCodec.encode(document)) as BackupParseResult.Success
        assertEquals(preferences, current.document.data.preferences)
        assertTrue(validate(current.document) is BackupValidationResult.Success)

        listOf(BACKUP_SCHEMA_VERSION_V1, BACKUP_SCHEMA_VERSION_V2).forEach { version ->
            val legacy = document.copy(schemaVersion = version)
            val bytes = BackupJsonCodec.encode(legacy)
            val parsed = BackupJsonCodec.parse(bytes) as BackupParseResult.Success
            assertEquals(version, parsed.document.schemaVersion)
            assertEquals(
                BackupPreferences(7, false, true, false),
                parsed.document.data.preferences
            )
            assertTrue(validate(parsed.document) is BackupValidationResult.Success)
        }

        val missingV3Field = JsonParser.parseString(BackupJsonCodec.encode(document).toString(Charsets.UTF_8))
            .asJsonObject
        missingV3Field.getAsJsonObject("data").getAsJsonObject("preferences").remove("theme")
        assertTrue(BackupJsonCodec.parse(missingV3Field.toString().toByteArray()) is BackupParseResult.Failure)
    }

    @Test
    fun roundTripsAtSeasonAndEpisodeCountLimits() {
        val base = fullDocument()
        val seriesRef = BackupRef(MediaSource.TMDB, "11")
        val series = BackupMedia(
            seriesRef,
            listOf(seriesRef),
            MediaType.SERIES,
            "Series",
            null,
            null,
            null,
            null
        )
        val seasons = List(BackupLimits.MAX_SEASONS) { index ->
            BackupSeason(
                seriesRef,
                BackupRef(MediaSource.TMDB, (index + 100).toString()),
                index,
                null,
                null,
                null,
                null,
                0
            )
        }
        val seasonDocument = base.copy(
            data = base.data.copy(
                media = listOf(series),
                seasons = seasons,
                episodes = emptyList(),
                library = listOf(BackupLibraryEntry(seriesRef, Instant.EPOCH, MediaType.SERIES)),
                movieProgress = emptyList(),
                seriesProgress = emptyList(),
                episodeProgress = emptyList(),
                ratings = emptyList()
            )
        )
        val seasonsResult = BackupJsonCodec.parse(BackupJsonCodec.encode(seasonDocument))
        assertTrue(seasonsResult is BackupParseResult.Success)
        assertEquals(seasonDocument, (seasonsResult as BackupParseResult.Success).document)

        val seasonRef = BackupRef(MediaSource.TMDB, "20")
        val episodes = List(BackupLimits.MAX_EPISODES) { index ->
            BackupEpisode(
                seasonRef,
                BackupRef(MediaSource.TMDB, (index + 1_000).toString()),
                index + 1,
                "Episode",
                null,
                null,
                null,
                null
            )
        }
        val episodeDocument = seasonDocument.copy(
            data = seasonDocument.data.copy(
                seasons = listOf(
                    BackupSeason(seriesRef, seasonRef, 1, null, null, null, null, episodes.size)
                ),
                episodes = episodes
            )
        )
        val episodesResult = BackupJsonCodec.parse(BackupJsonCodec.encode(episodeDocument))
        assertTrue(episodesResult is BackupParseResult.Success)
        assertEquals(episodeDocument, (episodesResult as BackupParseResult.Success).document)

        val overLimit = episodeDocument.copy(
            data = episodeDocument.data.copy(episodes = List(BackupLimits.MAX_EPISODES + 1) { episodes.first() })
        )
        try {
            BackupJsonCodec.encode(overLimit)
            throw AssertionError("Expected episode-count export rejection")
        } catch (failure: BackupExportFailure) {
            assertEquals(BackupFailureKind.EXPORT_TOO_LARGE, failure.kind)
        }
    }

    @Test
    fun legacyV1AndV2StillAcceptEpisodeCountsAboveTheV3Ceiling() {
        val count = BackupLimits.MAX_EPISODES + 1
        for (version in BACKUP_SCHEMA_VERSION_V1..BACKUP_SCHEMA_VERSION_V2) {
            val legacyBytes = buildString(count * 160 + 1_024) {
                append("{\"formatId\":\"$BACKUP_FORMAT_ID\",\"schemaVersion\":$version,")
                append("\"exportedAt\":\"2020-01-01T00:00:00Z\",\"data\":{\"media\":[")
                append("{\"primaryRef\":{\"source\":\"TMDB\",\"externalId\":\"10\"},")
                append("\"externalRefs\":[{\"source\":\"TMDB\",\"externalId\":\"10\"}],")
                append("\"mediaType\":\"SERIES\",\"title\":\"Series\",\"genres\":[]}],")
                append("\"seasons\":[{\"mediaRef\":{\"source\":\"TMDB\",\"externalId\":\"10\"},")
                append("\"externalRef\":{\"source\":\"TMDB\",\"externalId\":\"20\"},")
                append("\"seasonNumber\":1,\"episodeCount\":$count}],\"episodes\":[")
                repeat(count) { index ->
                    if (index > 0) append(',')
                    append("{\"seasonRef\":{\"source\":\"TMDB\",\"externalId\":\"20\"},")
                    append("\"externalRef\":{\"source\":\"TMDB\",\"externalId\":\"${index + 1_000}\"},")
                    append("\"episodeNumber\":${index + 1},\"title\":\"Episode\"}")
                }
                append("],\"library\":[],\"movieProgress\":[],")
                append("\"episodeProgress\":[],\"ratings\":[],\"preferences\":{")
                append("\"notificationLeadDays\":1,\"notifyMovieReleases\":true,")
                append("\"notifySeasonPremieres\":true,\"notifyEpisodeAirings\":true}}}")
            }.toByteArray()

            val result = BackupJsonCodec.parse(legacyBytes)
            assertTrue("Backup v$version should retain its legacy episode cap", result is BackupParseResult.Success)
            val document = (result as BackupParseResult.Success).document
            assertEquals(count, document.data.episodes.size)
            assertTrue(
                "Backup v$version must remain restorable above the v3 count ceiling",
                BackupValidator.validate(document, validationDate) is BackupValidationResult.Success
            )
        }
    }

    @Test
    fun v3ParserRejectsEpisodeCountsAboveItsConfiguredCeiling() {
        val count = BackupLimits.MAX_EPISODES + 1
        val bytes = buildString(count * 5 + 256) {
            append("{\"formatId\":\"$BACKUP_FORMAT_ID\",\"schemaVersion\":$BACKUP_SCHEMA_VERSION,")
            append("\"exportedAt\":\"2020-01-01T00:00:00Z\",\"data\":{\"media\":[],")
            append("\"seasons\":[],\"episodes\":[")
            repeat(count) { index ->
                if (index > 0) append(',')
                append("null")
            }
            append("""]}}""")
        }.toByteArray()

        val result = BackupJsonCodec.parse(bytes)
        assertTrue(result is BackupParseResult.Failure)
        assertEquals(BackupFailureKind.TOO_LARGE, (result as BackupParseResult.Failure).failure.kind)
    }

    @Test
    fun exportRejectsMetadataOutsideRestoreStringLimits() {
        val document = fullDocument()
        val tooLongTitle = document.copy(
            data = document.data.copy(
                media = document.data.media.map { it.copy(title = "x".repeat(BackupLimits.MAX_STRING + 1)) }
            )
        )
        val tooLongUrl = document.copy(
            data = document.data.copy(
                media = document.data.media.map { media ->
                    media.copy(posterUrl = "https://example.com/" + "x".repeat(BackupLimits.MAX_URL))
                }
            )
        )

        listOf(tooLongTitle, tooLongUrl).forEach { invalid ->
            try {
                BackupJsonCodec.encode(invalid)
                throw AssertionError("Expected export rejection for metadata outside restore limits")
            } catch (failure: BackupExportFailure) {
                assertEquals(BackupFailureKind.EXPORT_TOO_LARGE, failure.kind)
            }
        }
    }

    @Test
    fun byteLimitRoundTripsNearTheCapAndRejectsOversizedExports() {
        val base = fullDocument()
        val seriesRef = BackupRef(MediaSource.TMDB, "11")
        val seasonRef = BackupRef(MediaSource.TMDB, "20")
        val series = BackupMedia(seriesRef, listOf(seriesRef), MediaType.SERIES, "Series", null, null, null, null)
        val longOverview = "x".repeat(BackupLimits.MAX_STRING)
        fun documentWithEpisodeCount(count: Int) = base.copy(
            data = base.data.copy(
                media = listOf(series),
                seasons = listOf(BackupSeason(seriesRef, seasonRef, 1, null, null, null, null, count)),
                episodes = List(count) { index ->
                    BackupEpisode(
                        seasonRef,
                        BackupRef(MediaSource.TMDB, (index + 1_000).toString()),
                        index + 1,
                        "Episode",
                        longOverview,
                        null,
                        null,
                        null
                    )
                },
                library = listOf(BackupLibraryEntry(seriesRef, Instant.EPOCH, MediaType.SERIES)),
                movieProgress = emptyList(),
                seriesProgress = emptyList(),
                episodeProgress = emptyList(),
                ratings = emptyList()
            )
        )

        val nearLimit = BackupJsonCodec.encode(documentWithEpisodeCount(6_000))
        assertTrue(nearLimit.size > MAX_BACKUP_BYTES - 3 * 1024 * 1024)
        assertTrue(nearLimit.size <= MAX_BACKUP_BYTES)
        val parsed = BackupJsonCodec.parse(nearLimit)
        assertTrue(parsed is BackupParseResult.Success)
        assertEquals(6_000, (parsed as BackupParseResult.Success).document.data.episodes.size)

        try {
            BackupJsonCodec.encode(documentWithEpisodeCount(6_400))
            throw AssertionError("Expected oversized export rejection")
        } catch (failure: BackupExportFailure) {
            assertEquals(BackupFailureKind.EXPORT_TOO_LARGE, failure.kind)
        }
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
    fun movieRuntimeAndTypedReferencesRoundTripWithinTheSchema() {
        val document = fullDocument().let { base ->
            base.copy(
                data = base.data.copy(
                    media = base.data.media.map { it.copy(runtimeMinutes = 116) },
                    library = base.data.library.map { it.copy(mediaType = MediaType.MOVIE) },
                    ratings = base.data.ratings.map { it.copy(mediaType = MediaType.MOVIE) }
                )
            )
        }

        val encoded = BackupJsonCodec.encode(document)
        val parsed = BackupJsonCodec.parse(encoded) as BackupParseResult.Success
        assertEquals(document, parsed.document)
        val schema = JsonParser.parseString(resource("backup/bingee-backup-v3.schema.json").toString(Charsets.UTF_8))
        val payload = JsonParser.parseString(encoded.toString(Charsets.UTF_8))
        assertTrue(validateSchema(schema, payload, schema.asJsonObject, "$").isEmpty())
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
        val parsed = BackupJsonCodec.parse(resource("backup/valid-full.json"))

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
        val missingVersionResult = BackupJsonCodec.parse(valid.replace("\"schemaVersion\": 3,\n", "").toByteArray())
        assertTrue(missingVersionResult is BackupParseResult.Failure)

        assertEquals(
            BackupFailureKind.UNSUPPORTED_VERSION,
            (
                BackupJsonCodec.parse(
                    valid.replace("\"schemaVersion\": 3", "\"schemaVersion\": 5").toByteArray()
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
        .replace(Regex("\"$key\": [0-9]+"), "\"$key\": $value")
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

    private fun resource(name: String): ByteArray =
        checkNotNull(javaClass.classLoader?.getResourceAsStream(name)) { "Missing test resource: $name" }
            .use { it.readBytes() }

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
