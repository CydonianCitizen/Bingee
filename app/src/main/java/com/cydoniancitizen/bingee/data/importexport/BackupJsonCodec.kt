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
        writer.beginObject().name("mediaRef")
        writeRef(writer, progress.mediaRef)
        writer.name("watchedAt").value(progress.watchedAt.toString()).endObject()
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
            .name("notificationLeadDays").value(preferences.notificationLeadDays)
            .name("notifyMovieReleases").value(preferences.notifyMovieReleases)
            .name("notifySeasonPremieres").value(preferences.notifySeasonPremieres)
            .name("notifyEpisodeAirings").value(preferences.notifyEpisodeAirings)
            .endObject()
    }

    private fun readDocument(root: JsonObject): BackupParseResult.Success {
        val formatId = requiredString(root, "formatId")
        if (formatId != BACKUP_FORMAT_ID) problem(BackupFailureKind.WRONG_FORMAT)
        if (!root.has("schemaVersion")) problem(BackupFailureKind.MISSING_VERSION)
        val schemaVersion = requiredInt(root, "schemaVersion")
        if (schemaVersion != BACKUP_SCHEMA_VERSION) problem(BackupFailureKind.UNSUPPORTED_VERSION)
        val exportedAt = requiredInstant(root, "exportedAt")
        val data = requiredObject(root, "data")
        return BackupParseResult.Success(
            BackupDocument(BACKUP_FORMAT_ID, schemaVersion, exportedAt, readData(data))
        )
    }

    private fun readData(data: JsonObject) = BackupData(
        media = requiredArray(data, "media", BackupLimits.MAX_MEDIA).map(::readMedia),
        seasons = requiredArray(data, "seasons", BackupLimits.MAX_SEASONS).map(::readSeason),
        episodes = requiredArray(data, "episodes", BackupLimits.MAX_EPISODES).map(::readEpisode),
        library = requiredArray(data, "library", BackupLimits.MAX_MEDIA).map(::readLibrary),
        movieProgress = requiredArray(data, "movieProgress", BackupLimits.MAX_MEDIA).map(::readMovieProgress),
        episodeProgress = requiredArray(data, "episodeProgress", BackupLimits.MAX_EPISODES).map(::readEpisodeProgress),
        ratings = requiredArray(data, "ratings", BackupLimits.MAX_MEDIA).map(::readRating),
        preferences = readPreferences(requiredObject(data, "preferences"))
    )

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
            releaseDate = nullableDate(objectValue, "releaseDate")
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
            readRef(required(objectValue, "mediaRef")),
            requiredInstant(objectValue, "watchedAt")
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

    private fun required(objectValue: JsonObject, name: String): JsonElement =
        objectValue.get(name) ?: problem(BackupFailureKind.INVALID_STRUCTURE)

    private fun requiredString(objectValue: JsonObject, name: String): String {
        val value = required(objectValue, name)
        if (!value.isJsonPrimitive || !value.asJsonPrimitive.isString) problem(BackupFailureKind.INVALID_STRUCTURE)
        return value.asString
    }

    private fun nullableString(objectValue: JsonObject, name: String): String? {
        if (!objectValue.has(name) || objectValue.get(name).isJsonNull) return null
        return requiredString(objectValue, name)
    }

    private fun requiredInt(objectValue: JsonObject, name: String): Int {
        val value = required(objectValue, name)
        if (!value.isJsonPrimitive || !value.asJsonPrimitive.isNumber ||
            !value.asString.matches(Regex("-?(0|[1-9][0-9]*)"))
        ) {
            problem(BackupFailureKind.INVALID_STRUCTURE)
        }
        return value.asInt
    }

    private fun nullableInt(objectValue: JsonObject, name: String): Int? =
        if (!objectValue.has(name) || objectValue.get(name).isJsonNull) null else requiredInt(objectValue, name)

    private fun requiredBoolean(objectValue: JsonObject, name: String): Boolean {
        val value = required(objectValue, name)
        if (!value.isJsonPrimitive || !value.asJsonPrimitive.isBoolean) problem(BackupFailureKind.INVALID_STRUCTURE)
        return value.asBoolean
    }

    private fun requiredInstant(objectValue: JsonObject, name: String): Instant {
        val value = requiredString(objectValue, name)
        if (!value.endsWith("Z")) problem(BackupFailureKind.INVALID_STRUCTURE)
        return try {
            Instant.parse(value)
        } catch (_: Exception) {
            problem(BackupFailureKind.INVALID_STRUCTURE)
        }
    }

    private fun nullableDate(objectValue: JsonObject, name: String): LocalDate? {
        val value = nullableString(objectValue, name) ?: return null
        return try {
            LocalDate.parse(value)
        } catch (_: Exception) {
            problem(BackupFailureKind.INVALID_STRUCTURE)
        }
    }

    private fun requiredObject(objectValue: JsonObject, name: String): JsonObject =
        required(objectValue, name).asObjectOrProblem()

    private fun requiredArray(objectValue: JsonObject, name: String, max: Int): JsonArray {
        val value = required(objectValue, name)
        if (!value.isJsonArray || value.asJsonArray.size() > max) problem(BackupFailureKind.TOO_LARGE)
        return value.asJsonArray
    }

    private fun JsonElement.asObjectOrProblem(): JsonObject =
        if (isJsonObject) asJsonObject else problem(BackupFailureKind.INVALID_STRUCTURE)

    private fun problem(kind: BackupFailureKind): Nothing = throw BackupParseFailure(kind)
}
