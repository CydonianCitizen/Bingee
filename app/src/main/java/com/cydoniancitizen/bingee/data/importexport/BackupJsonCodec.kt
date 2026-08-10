package com.cydoniancitizen.bingee.data.importexport

import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStreamWriter
import java.io.StringReader
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.CancellationException

internal object BackupJsonCodec {
    fun encode(document: BackupDocument): ByteArray {
        val output = ByteArrayOutputStream()
        JsonWriter(OutputStreamWriter(output, StandardCharsets.UTF_8)).use { writer ->
            writer.setIndent("  ")
            writer.serializeNulls = true
            writeDocument(writer, document)
        }
        return output.toByteArray()
    }

    fun parse(input: InputStream, maxBytes: Int = MAX_BACKUP_BYTES): BackupParseResult = try {
        val bytes = readBounded(input, maxBytes)
        parse(bytes)
    } catch (failure: BackupParseFailure) {
        BackupParseResult.Failure(failure)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        BackupParseResult.Failure(BackupParseFailure(BackupFailureKind.UNREADABLE))
    }

    fun parse(bytes: ByteArray): BackupParseResult = try {
        if (bytes.size > MAX_BACKUP_BYTES) throw BackupParseFailure(BackupFailureKind.TOO_LARGE)
        val text = decodeUtf8(bytes)
        val reader = JsonReader(StringReader(text))
        val root = JsonParser.parseReader(reader)
        if (!root.isJsonObject) problem(BackupFailureKind.INVALID_STRUCTURE)
        readDocument(root.asJsonObject)
    } catch (failure: BackupParseFailure) {
        BackupParseResult.Failure(failure)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: com.google.gson.JsonParseException) {
        BackupParseResult.Failure(BackupParseFailure(BackupFailureKind.MALFORMED_JSON))
    } catch (_: CharacterCodingException) {
        BackupParseResult.Failure(BackupParseFailure(BackupFailureKind.INVALID_UTF8))
    } catch (_: Exception) {
        BackupParseResult.Failure(BackupParseFailure(BackupFailureKind.INVALID_STRUCTURE))
    }

    private fun readBounded(input: InputStream, maxBytes: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > maxBytes) throw BackupParseFailure(BackupFailureKind.TOO_LARGE)
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun decodeUtf8(bytes: ByteArray): String = StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes))
        .toString()

    private fun writeDocument(writer: JsonWriter, document: BackupDocument) {
        writer.beginObject()
        writer.name("formatId").value(document.formatId)
        writer.name("schemaVersion").value(document.schemaVersion)
        writer.name("exportedAt").value(document.exportedAt.toString())
        writer.name("data")
        writeData(writer, document.data)
        writer.endObject()
    }

    private fun writeData(writer: JsonWriter, data: BackupData) {
        writer.beginObject()
        writer.name("media").beginArray()
        data.media.forEach { writeMedia(writer, it) }
        writer.endArray()
        writer.name("seasons").beginArray()
        data.seasons.forEach { writeSeason(writer, it) }
        writer.endArray()
        writer.name("episodes").beginArray()
        data.episodes.forEach { writeEpisode(writer, it) }
        writer.endArray()
        writer.name("library").beginArray()
        data.library.forEach { writeLibrary(writer, it) }
        writer.endArray()
        writer.name("movieProgress").beginArray()
        data.movieProgress.forEach { writeMovieProgress(writer, it) }
        writer.endArray()
        writer.name("seriesProgress").beginArray()
        data.seriesProgress.forEach { writeSeriesProgress(writer, it) }
        writer.endArray()
        writer.name("episodeProgress").beginArray()
        data.episodeProgress.forEach { writeEpisodeProgress(writer, it) }
        writer.endArray()
        writer.name("ratings").beginArray()
        data.ratings.forEach { writeRating(writer, it) }
        writer.endArray()

        writer.name("preferences")
        writePreferences(writer, data.preferences)
        writer.endObject()
    }

    private fun writeRef(writer: JsonWriter, ref: BackupRef) {
        writer.beginObject().name("source").value(ref.source.name).name("externalId").value(ref.externalId).endObject()
    }

    private fun writeNullable(writer: JsonWriter, name: String, value: String?) {
        writer.name(name)
        if (value == null) writer.nullValue() else writer.value(value)
    }

    private fun writeMedia(writer: JsonWriter, media: BackupMedia) {
        writer.beginObject()
        writer.name("primaryRef")
        writeRef(writer, media.primaryRef)
        writer.name("externalRefs").beginArray()
        media.externalRefs.forEach { writeRef(writer, it) }
        writer.endArray()
        writer.name("mediaType").value(media.mediaType.name)
        writer.name("title").value(media.title)
        writeNullable(writer, "originalTitle", media.originalTitle)
        writeNullable(writer, "overview", media.overview)
        writeNullable(writer, "posterUrl", media.posterUrl)
        writeNullable(writer, "releaseDate", media.releaseDate?.toString())
        writer.name("isFavorite").value(media.isFavorite)
        writer.endObject()
    }

    private fun writeSeason(writer: JsonWriter, season: BackupSeason) {
        writer.beginObject()
        writer.name("mediaRef")
        writeRef(writer, season.mediaRef)
        writer.name("externalRef")
        writeRef(writer, season.externalRef)
        writer.name("seasonNumber").value(season.seasonNumber)
        writeNullable(writer, "name", season.name)
        writeNullable(writer, "overview", season.overview)
        writeNullable(writer, "posterUrl", season.posterUrl)
        writeNullable(writer, "airDate", season.airDate?.toString())
        writer.name("episodeCount").value(season.episodeCount)
        writer.endObject()
    }

    private fun writeEpisode(writer: JsonWriter, episode: BackupEpisode) {
        writer.beginObject()
        writer.name("seasonRef")
        writeRef(writer, episode.seasonRef)
        writer.name("externalRef")
        writeRef(writer, episode.externalRef)
        writer.name("episodeNumber").value(episode.episodeNumber)
        writer.name("title").value(episode.title)
        writeNullable(writer, "overview", episode.overview)
        writeNullable(writer, "airDate", episode.airDate?.toString())
        if (episode.runtimeMinutes == null) {
            writer.name("runtimeMinutes").nullValue()
        } else {
            writer.name("runtimeMinutes").value(episode.runtimeMinutes)
        }
        writeNullable(writer, "stillUrl", episode.stillUrl)
        writer.endObject()
    }

    private fun writeLibrary(writer: JsonWriter, entry: BackupLibraryEntry) {
        writer.beginObject().name("mediaRef")
        writeRef(writer, entry.mediaRef)
        writer.name("addedAt").value(entry.addedAt.toString()).endObject()
    }

    private fun writeMovieProgress(writer: JsonWriter, progress: BackupMovieProgress) {
        writer.beginObject()
        writer.name("mediaRef")
        writeRef(writer, progress.mediaRef)
        writer.name("watchedAt").value(progress.watchedAt.toString())
        writeNullable(writer, "watchedDate", progress.watchedDate?.toString())
        writer.endObject()
    }

    private fun writeSeriesProgress(writer: JsonWriter, progress: BackupSeriesProgress) {
        writer.beginObject()
        writer.name("mediaRef")
        writeRef(writer, progress.mediaRef)
        writer.name("completedAt").value(progress.completedAt.toString())
        writeNullable(writer, "watchedDate", progress.watchedDate?.toString())
        writer.endObject()
    }

    private fun writeEpisodeProgress(writer: JsonWriter, progress: BackupEpisodeProgress) {
        writer.beginObject().name("episodeRef")
        writeRef(writer, progress.episodeRef)
        writer.name("watchedAt").value(progress.watchedAt.toString()).endObject()
    }

    private fun writeRating(writer: JsonWriter, rating: BackupRating) {
        writer.beginObject().name("mediaRef")
        writeRef(writer, rating.mediaRef)
        writer.name("rating").value(rating.rating)
        writer.name("ratedAt").value(rating.ratedAt.toString())
        writer.name("updatedAt").value(rating.updatedAt.toString()).endObject()
    }

    private fun writePreferences(writer: JsonWriter, preferences: BackupPreferences) {
        writer.beginObject()
        writer.name("notificationLeadDays").value(preferences.notificationLeadDays)
        writer.name("notifyMovieReleases").value(preferences.notifyMovieReleases)
        writer.name("notifySeasonPremieres").value(preferences.notifySeasonPremieres)
        writer.name("notifyEpisodeAirings").value(preferences.notifyEpisodeAirings)
        writer.endObject()
    }

    private fun readDocument(root: JsonObject): BackupParseResult {
        val formatId = requiredString(root, "formatId")
        if (formatId !=
            BACKUP_FORMAT_ID
        ) {
            return BackupParseResult.Failure(BackupParseFailure(BackupFailureKind.WRONG_FORMAT))
        }
        val schemaVersion = requiredInt(root, "schemaVersion")
        if (schemaVersion != BACKUP_SCHEMA_VERSION) {
            return BackupParseResult.Failure(BackupParseFailure(BackupFailureKind.UNSUPPORTED_VERSION))
        }
        val exportedAt = requiredInstant(root, "exportedAt")
        val dataObj = required(root, "data").asObjectOrProblem()
        val data = readData(dataObj)
        return BackupParseResult.Success(BackupDocument(formatId, schemaVersion, exportedAt, data))
    }

    private fun readData(dataObj: JsonObject): BackupData {
        val media = readArray(dataObj, "media", BackupLimits.MAX_MEDIA, ::readMedia)
        val seasons = readArray(dataObj, "seasons", BackupLimits.MAX_SEASONS, ::readSeason)
        val episodes = readArray(dataObj, "episodes", BackupLimits.MAX_EPISODES, ::readEpisode)
        val library = readArray(dataObj, "library", BackupLimits.MAX_MEDIA, ::readLibrary)
        val movieProgress = readArray(dataObj, "movieProgress", BackupLimits.MAX_MEDIA, ::readMovieProgress)
        val episodeProgress = readArray(dataObj, "episodeProgress", BackupLimits.MAX_EPISODES, ::readEpisodeProgress)
        val ratings = readArray(dataObj, "ratings", BackupLimits.MAX_MEDIA, ::readRating)
        val preferences = readPreferences(required(dataObj, "preferences").asObjectOrProblem())

        val seriesProgress = readOptionalArray(dataObj, "seriesProgress", BackupLimits.MAX_MEDIA, ::readSeriesProgress)

        return BackupData(
            media = media,
            seasons = seasons,
            episodes = episodes,
            library = library,
            movieProgress = movieProgress,
            episodeProgress = episodeProgress,
            ratings = ratings,
            preferences = preferences,
            seriesProgress = seriesProgress
        )
    }

    private fun readRef(value: JsonElement): BackupRef {
        val objectValue = value.asObjectOrProblem()
        val source = try {
            MediaSource.valueOf(requiredString(objectValue, "source"))
        } catch (
            _: Exception
        ) {
            problem(BackupFailureKind.INVALID_STRUCTURE)
        }
        val externalId = requiredString(objectValue, "externalId")
        return BackupRef(source, externalId)
    }

    private fun readMedia(value: JsonElement): BackupMedia {
        val objectValue = value.asObjectOrProblem()
        return BackupMedia(
            primaryRef = readRef(required(objectValue, "primaryRef")),
            externalRefs = requiredArray(objectValue, "externalRefs", BackupLimits.MAX_MEDIA).map(::readRef),
            mediaType = readMediaType(requiredString(objectValue, "mediaType")),
            title = requiredString(objectValue, "title"),
            originalTitle = nullableString(objectValue, "originalTitle"),
            overview = nullableString(objectValue, "overview"),
            posterUrl = nullableString(objectValue, "posterUrl"),
            releaseDate = nullableDate(objectValue, "releaseDate"),
            isFavorite = booleanOrDefault(objectValue, "isFavorite", false)
        )
    }

    private fun readSeason(value: JsonElement): BackupSeason {
        val objectValue = value.asObjectOrProblem()
        return BackupSeason(
            mediaRef = readRef(required(objectValue, "mediaRef")),
            externalRef = readRef(required(objectValue, "externalRef")),
            seasonNumber = requiredInt(objectValue, "seasonNumber"),
            name = nullableString(objectValue, "name"),
            overview = nullableString(objectValue, "overview"),
            posterUrl = nullableString(objectValue, "posterUrl"),
            airDate = nullableDate(objectValue, "airDate"),
            episodeCount = requiredInt(objectValue, "episodeCount")
        )
    }

    private fun readEpisode(value: JsonElement): BackupEpisode {
        val objectValue = value.asObjectOrProblem()
        return BackupEpisode(
            seasonRef = readRef(required(objectValue, "seasonRef")),
            externalRef = readRef(required(objectValue, "externalRef")),
            episodeNumber = requiredInt(objectValue, "episodeNumber"),
            title = requiredString(objectValue, "title"),
            overview = nullableString(objectValue, "overview"),
            airDate = nullableDate(objectValue, "airDate"),
            runtimeMinutes = nullableInt(objectValue, "runtimeMinutes"),
            stillUrl = nullableString(objectValue, "stillUrl")
        )
    }

    private fun readLibrary(value: JsonElement): BackupLibraryEntry {
        val objectValue = value.asObjectOrProblem()
        return BackupLibraryEntry(readRef(required(objectValue, "mediaRef")), requiredInstant(objectValue, "addedAt"))
    }

    private fun readMovieProgress(value: JsonElement): BackupMovieProgress {
        val objectValue = value.asObjectOrProblem()
        return BackupMovieProgress(
            mediaRef = readRef(required(objectValue, "mediaRef")),
            watchedAt = requiredInstant(objectValue, "watchedAt"),
            watchedDate = nullableDate(objectValue, "watchedDate")
        )
    }

    private fun readSeriesProgress(value: JsonElement): BackupSeriesProgress {
        val objectValue = value.asObjectOrProblem()
        return BackupSeriesProgress(
            mediaRef = readRef(required(objectValue, "mediaRef")),
            completedAt = requiredInstant(objectValue, "completedAt"),
            watchedDate = nullableDate(objectValue, "watchedDate")
        )
    }

    private fun readEpisodeProgress(value: JsonElement): BackupEpisodeProgress {
        val objectValue = value.asObjectOrProblem()
        return BackupEpisodeProgress(
            readRef(required(objectValue, "episodeRef")),
            requiredInstant(objectValue, "watchedAt")
        )
    }

    private fun readRating(value: JsonElement): BackupRating {
        val objectValue = value.asObjectOrProblem()
        return BackupRating(
            readRef(required(objectValue, "mediaRef")),
            requiredInt(objectValue, "rating"),
            requiredInstant(objectValue, "ratedAt"),
            requiredInstant(objectValue, "updatedAt")
        )
    }

    private fun readPreferences(value: JsonObject) = BackupPreferences(
        requiredInt(value, "notificationLeadDays"),
        requiredBoolean(value, "notifyMovieReleases"),
        requiredBoolean(value, "notifySeasonPremieres"),
        requiredBoolean(value, "notifyEpisodeAirings")
    )

    private fun readMediaType(value: String): MediaType = try {
        MediaType.valueOf(value)
    } catch (_: Exception) {
        problem(BackupFailureKind.INVALID_STRUCTURE)
    }

    private fun <T> readArray(obj: JsonObject, key: String, maxCount: Int, parseItem: (JsonElement) -> T): List<T> {
        val jsonArray = requiredArray(obj, key, maxCount)
        return jsonArray.map(parseItem)
    }

    private fun <T> readOptionalArray(
        obj: JsonObject,
        key: String,
        maxCount: Int,
        parseItem: (JsonElement) -> T
    ): List<T> {
        val elem = obj.get(key)
        if (elem == null) return emptyList()
        if (!elem.isJsonArray) problem(BackupFailureKind.INVALID_STRUCTURE)
        val array = elem.asJsonArray
        if (array.size() > maxCount) problem(BackupFailureKind.TOO_LARGE)
        return array.map(parseItem)
    }

    private fun requiredArray(obj: JsonObject, key: String, maxCount: Int): JsonArray {
        val elem = required(obj, key)
        if (!elem.isJsonArray) problem(BackupFailureKind.INVALID_STRUCTURE)
        val array = elem.asJsonArray
        if (array.size() > maxCount) problem(BackupFailureKind.TOO_LARGE)
        return array
    }

    private fun required(obj: JsonObject, key: String): JsonElement {
        val value = obj.get(key)
        if (value == null || value.isJsonNull) problem(BackupFailureKind.INVALID_STRUCTURE)
        return value
    }

    private fun requiredString(obj: JsonObject, key: String): String {
        val elem = required(obj, key)
        if (!elem.isJsonPrimitive || !elem.asJsonPrimitive.isString) problem(BackupFailureKind.INVALID_STRUCTURE)
        val value = elem.asString
        if (value.length > BackupLimits.MAX_STRING) problem(BackupFailureKind.TOO_LARGE)
        return value
    }

    private fun requiredInt(obj: JsonObject, key: String): Int {
        val elem = required(obj, key)
        if (!elem.isJsonPrimitive || !elem.asJsonPrimitive.isNumber) problem(BackupFailureKind.INVALID_STRUCTURE)
        return try {
            elem.asInt
        } catch (_: Exception) {
            problem(BackupFailureKind.INVALID_STRUCTURE)
        }
    }

    private fun requiredBoolean(obj: JsonObject, key: String): Boolean {
        val elem = required(obj, key)
        if (!elem.isJsonPrimitive || !elem.asJsonPrimitive.isBoolean) problem(BackupFailureKind.INVALID_STRUCTURE)
        return elem.asBoolean
    }

    private fun booleanOrDefault(obj: JsonObject, key: String, default: Boolean): Boolean {
        val elem = obj.get(key)
        if (elem == null) return default
        if (!elem.isJsonPrimitive || !elem.asJsonPrimitive.isBoolean) problem(BackupFailureKind.INVALID_STRUCTURE)
        return elem.asBoolean
    }

    private fun requiredInstant(obj: JsonObject, key: String): Instant = try {
        Instant.parse(requiredString(obj, key))
    } catch (_: Exception) {
        problem(BackupFailureKind.INVALID_STRUCTURE)
    }

    private fun nullableString(obj: JsonObject, key: String): String? {
        val elem = obj.get(key)
        if (elem == null || elem.isJsonNull) return null
        if (!elem.isJsonPrimitive || !elem.asJsonPrimitive.isString) problem(BackupFailureKind.INVALID_STRUCTURE)
        val value = elem.asString
        if (value.length > BackupLimits.MAX_STRING) problem(BackupFailureKind.TOO_LARGE)
        return value
    }

    private fun nullableInt(obj: JsonObject, key: String): Int? {
        val elem = obj.get(key)
        if (elem == null || elem.isJsonNull) return null
        if (!elem.isJsonPrimitive || !elem.asJsonPrimitive.isNumber) problem(BackupFailureKind.INVALID_STRUCTURE)
        return try {
            elem.asInt
        } catch (_: Exception) {
            problem(BackupFailureKind.INVALID_STRUCTURE)
        }
    }

    private fun nullableDouble(obj: JsonObject, key: String): Double? {
        val elem = obj.get(key)
        if (elem == null || elem.isJsonNull) return null
        if (!elem.isJsonPrimitive || !elem.asJsonPrimitive.isNumber) problem(BackupFailureKind.INVALID_STRUCTURE)
        return try {
            elem.asDouble
        } catch (_: Exception) {
            problem(BackupFailureKind.INVALID_STRUCTURE)
        }
    }

    private fun nullableDate(obj: JsonObject, key: String): LocalDate? = nullableString(obj, key)?.let { raw ->
        try {
            LocalDate.parse(raw)
        } catch (_: Exception) {
            problem(BackupFailureKind.INVALID_STRUCTURE)
        }
    }

    private fun nullableInstant(obj: JsonObject, key: String): Instant? = nullableString(obj, key)?.let { raw ->
        try {
            Instant.parse(raw)
        } catch (_: Exception) {
            problem(BackupFailureKind.INVALID_STRUCTURE)
        }
    }

    private fun JsonElement.asObjectOrProblem(): JsonObject {
        if (!isJsonObject) problem(BackupFailureKind.INVALID_STRUCTURE)
        return asJsonObject
    }

    private fun problem(kind: BackupFailureKind): Nothing = throw BackupParseFailure(kind)
}
