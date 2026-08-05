@file:Suppress("DEPRECATION", "ktlint:standard:max-line-length")

package com.cydoniancitizen.bingee.data.imports.tvtime

import android.net.Uri
import com.cydoniancitizen.bingee.core.model.MediaType
import com.cydoniancitizen.bingee.data.imports.model.ImportSourceLocation
import com.cydoniancitizen.bingee.data.imports.model.ImportWarning
import com.cydoniancitizen.bingee.data.imports.model.ImportWarningCode
import com.cydoniancitizen.bingee.data.imports.model.ImportedEpisodeHint
import com.cydoniancitizen.bingee.data.imports.model.ImportedIdentityNamespace
import com.cydoniancitizen.bingee.data.imports.model.ImportedMediaHint
import com.cydoniancitizen.bingee.data.imports.model.ImportedSourceDocument
import com.cydoniancitizen.bingee.data.imports.model.ImportedSourceIdentity
import com.cydoniancitizen.bingee.data.imports.model.ImportedSourceSummary
import com.cydoniancitizen.bingee.data.imports.model.ImportedTimestamp
import com.cydoniancitizen.bingee.data.imports.model.ImportedUnsupportedFields
import com.cydoniancitizen.bingee.data.imports.model.ImportedWatchRecord
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.MalformedJsonException
import java.io.EOFException
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStreamReader
import java.math.BigDecimal
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.UUID
import java.util.regex.Pattern
import java.util.zip.ZipException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal enum class TvTimeParseFailureKind {
    ARCHIVE,
    MISSING_ROLE,
    DUPLICATE_ROLE,
    AMBIGUOUS_ROLE,
    UNKNOWN_ROLE,
    EMPTY_ARRAY,
    MALFORMED_JSON,
    INVALID_UTF8,
    DUPLICATE_JSON_KEY,
    INVALID_STRUCTURE,
    INVALID_RECORD,
    DUPLICATE_IDENTITY,
    CONFLICTING_IDENTITY,
    TOO_LARGE
}

internal data class TvTimeParseFailure(
    val kind: TvTimeParseFailureKind,
    val archiveFailure: TvTimeArchiveFailureKind? = null
) : Exception()

internal sealed interface TvTimeParseResult {
    data class Success(val document: ImportedSourceDocument) : TvTimeParseResult
    data class Failure(val failure: TvTimeParseFailure) : TvTimeParseResult
}

@Singleton
internal class TvTimeSourceParser @Inject constructor(private val zipGateway: TvTimeZipGateway) {
    suspend fun parse(uri: Uri): TvTimeParseResult = try {
        when (val result = zipGateway.withArchive(uri) { archive -> parseArchiveDocument(archive) }) {
            is TvTimeArchiveResult.Success -> TvTimeParseResult.Success(result.value)
            is TvTimeArchiveResult.Failure -> TvTimeParseResult.Failure(
                TvTimeParseFailure(TvTimeParseFailureKind.ARCHIVE, result.failure.kind)
            )
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: TvTimeParseFailure) {
        TvTimeParseResult.Failure(failure)
    } catch (_: Exception) {
        TvTimeParseResult.Failure(TvTimeParseFailure(TvTimeParseFailureKind.INVALID_STRUCTURE))
    }

    internal suspend fun parseArchiveForTest(archive: TvTimeArchive): TvTimeParseResult = try {
        TvTimeParseResult.Success(parseArchiveDocument(archive))
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: TvTimeParseFailure) {
        TvTimeParseResult.Failure(failure)
    } catch (_: Exception) {
        TvTimeParseResult.Failure(TvTimeParseFailure(TvTimeParseFailureKind.INVALID_STRUCTURE))
    }

    private suspend fun parseArchiveDocument(archive: TvTimeArchive): ImportedSourceDocument = try {
        parseArchiveDocumentUnsafe(archive)
    } catch (failure: ZipException) {
        throw failure
    } catch (_: CharacterCodingException) {
        fail(TvTimeParseFailureKind.INVALID_UTF8)
    } catch (_: MalformedJsonException) {
        fail(TvTimeParseFailureKind.MALFORMED_JSON)
    } catch (_: EOFException) {
        fail(TvTimeParseFailureKind.MALFORMED_JSON)
    } catch (_: IOException) {
        fail(TvTimeParseFailureKind.MALFORMED_JSON)
    }

    private suspend fun parseArchiveDocumentUnsafe(archive: TvTimeArchive): ImportedSourceDocument {
        val coroutineContext = currentCoroutineContext()
        val jsonEntries = archive.entries.filter { it.kind == TvTimeArchiveEntryKind.JSON }
        if (jsonEntries.isEmpty()) fail(TvTimeParseFailureKind.MISSING_ROLE)

        val warnings = WarningCollector()
        val identityOwners = linkedMapOf<String, String>()
        val seenRoles = mutableSetOf<TvTimeRole>()
        val movies = mutableListOf<ImportedMediaHint>()
        val series = mutableListOf<ImportedMediaHint>()
        val episodes = mutableListOf<ImportedEpisodeHint>()
        val listLinks = mutableListOf<ListLink>()
        var listCount = 0
        var invalidRecords = 0
        var seasonCount = 0
        var favoriteCount = 0
        var rewatchCount = 0
        var watchedCount = 0
        var statusCount = 0
        var technicalFlagCount = 0
        var recordsWithImdb = 0
        var recordsWithTvdb = 0
        var watchedMovies = 0
        var watchedEpisodes = 0
        var specials = 0

        jsonEntries.forEach { entry ->
            coroutineContext.ensureActive()
            archive.open(entry).use { input ->
                val guardedInput = RecordGuardInputStream(input, coroutineContext)
                val reader = JsonReader(
                    InputStreamReader(
                        guardedInput,
                        StandardCharsets.UTF_8.newDecoder()
                            .onMalformedInput(CodingErrorAction.REPORT)
                            .onUnmappableCharacter(CodingErrorAction.REPORT)
                    )
                )
                reader.isLenient = false
                if (reader.peek() != JsonToken.BEGIN_ARRAY) fail(TvTimeParseFailureKind.UNKNOWN_ROLE)
                reader.beginArray()
                if (!reader.hasNext()) fail(TvTimeParseFailureKind.EMPTY_ARRAY)
                val first = readRecord(reader, guardedInput, coroutineContext)
                val role = classify(first)
                if (!seenRoles.add(role)) fail(TvTimeParseFailureKind.DUPLICATE_ROLE)
                when (role) {
                    TvTimeRole.LIST -> {
                        listCount++
                        val list = parseList(first, entry.index, warnings)
                        list.items.forEachIndexed { index, item ->
                            val location = ImportSourceLocation(entry.index, index, "$.items[$index]")
                            val type = item.type.lowercase()
                            if (type != "movie" && type != "series") {
                                invalidRecords++
                                warnings.add(ImportWarning(ImportWarningCode.INVALID_RECORD, location, "type"))
                            } else {
                                listLinks += ListLink(type, item.tvdbId, item.uuid, location)
                            }
                        }
                        if (reader.hasNext()) fail(TvTimeParseFailureKind.INVALID_STRUCTURE)
                    }

                    TvTimeRole.MOVIE -> {
                        var index = 0
                        processMovie(first, entry.index, index, warnings, identityOwners).also { result ->
                            if (result.warning != null) warnings.add(result.warning)
                            if (result.value != null) {
                                movies += result.value
                                if (result.value.watch?.watched == true) watchedMovies++
                                if (result.value.identities.any {
                                        it.namespace == ImportedIdentityNamespace.IMDB
                                    }
                                ) {
                                    recordsWithImdb++
                                }
                                if (result.value.identities.any {
                                        it.namespace == ImportedIdentityNamespace.TVDB
                                    }
                                ) {
                                    recordsWithTvdb++
                                }
                                if (result.value.warnings.any {
                                        it.code == ImportWarningCode.UNSUPPORTED_FIELD
                                    }
                                ) {
                                    favoriteCount++
                                }
                                result.value.watch?.rewatchCount?.let { rewatchCount++ }
                            } else {
                                invalidRecords++
                            }
                        }
                        index++
                        while (reader.hasNext()) {
                            if (index >= TvTimeImportLimits.MAX_MOVIE_RECORDS) fail(TvTimeParseFailureKind.TOO_LARGE)
                            val value = readRecord(reader, guardedInput, coroutineContext, TvTimeRole.MOVIE)
                            processMovie(value, entry.index, index, warnings, identityOwners).also { result ->
                                result.warning?.let(warnings::add)
                                result.value?.let { movie ->
                                    movies += movie
                                    if (movie.watch?.watched == true) watchedMovies++
                                    if (movie.identities.any {
                                            it.namespace == ImportedIdentityNamespace.IMDB
                                        }
                                    ) {
                                        recordsWithImdb++
                                    }
                                    if (movie.identities.any {
                                            it.namespace == ImportedIdentityNamespace.TVDB
                                        }
                                    ) {
                                        recordsWithTvdb++
                                    }
                                    if (movie.warnings.any {
                                            it.code == ImportWarningCode.UNSUPPORTED_FIELD
                                        }
                                    ) {
                                        favoriteCount++
                                    }
                                    movie.watch?.rewatchCount?.let { rewatchCount++ }
                                } ?: run { invalidRecords++ }
                            }
                            index++
                        }
                    }

                    TvTimeRole.SERIES -> {
                        var index = 0
                        fun add(value: JsonElement, recordIndex: Int) {
                            val result = processSeries(value, entry.index, recordIndex, warnings, identityOwners)
                            result.warning?.let(warnings::add)
                            result.value?.let { parsed ->
                                if (seasonCount + parsed.seasonCount > TvTimeImportLimits.MAX_SEASON_RECORDS ||
                                    episodes.size + parsed.episodes.size > TvTimeImportLimits.MAX_EPISODE_RECORDS
                                ) {
                                    fail(TvTimeParseFailureKind.TOO_LARGE)
                                }
                                series += parsed.hint
                                seasonCount += parsed.seasonCount
                                invalidRecords += parsed.invalidEpisodeCount
                                if (parsed.hint.identities.any {
                                        it.namespace == ImportedIdentityNamespace.IMDB
                                    }
                                ) {
                                    recordsWithImdb++
                                }
                                if (parsed.hint.identities.any {
                                        it.namespace == ImportedIdentityNamespace.TVDB
                                    }
                                ) {
                                    recordsWithTvdb++
                                }
                                if (parsed.hint.warnings.any {
                                        it.code == ImportWarningCode.UNSUPPORTED_FIELD
                                    }
                                ) {
                                    favoriteCount++
                                }
                                if (parsed.statusPresent) statusCount++
                                if (parsed.technicalFlagPresent) technicalFlagCount++
                                parsed.hint.warnings.count {
                                    it.code == ImportWarningCode.UNSUPPORTED_FIELD &&
                                        it.fieldName == "rewatch_count"
                                }
                                    .takeIf { it > 0 }?.let { rewatchCount += it }
                                parsed.episodes.forEach { episode ->
                                    episodes += episode
                                    if (episode.watch.watched) watchedEpisodes++
                                    if (episode.special || episode.specialsSeason) specials++
                                    if (episode.identities.any {
                                            it.namespace == ImportedIdentityNamespace.IMDB
                                        }
                                    ) {
                                        recordsWithImdb++
                                    }
                                    if (episode.identities.any {
                                            it.namespace == ImportedIdentityNamespace.TVDB
                                        }
                                    ) {
                                        recordsWithTvdb++
                                    }
                                    episode.watch.rewatchCount?.let { rewatchCount++ }
                                    episode.watch.watchedCount?.let { watchedCount++ }
                                }
                            } ?: run { invalidRecords++ }
                        }
                        add(first, index++)
                        while (reader.hasNext()) {
                            if (index >= TvTimeImportLimits.MAX_SERIES_RECORDS) fail(TvTimeParseFailureKind.TOO_LARGE)
                            add(readRecord(reader, guardedInput, coroutineContext, TvTimeRole.SERIES), index++)
                        }
                    }
                }
                reader.endArray()
                if (reader.peek() != JsonToken.END_DOCUMENT) fail(TvTimeParseFailureKind.INVALID_STRUCTURE)
                reader.close()
            }
        }

        if (listCount !=
            1
        ) {
            fail(if (listCount == 0) TvTimeParseFailureKind.MISSING_ROLE else TvTimeParseFailureKind.DUPLICATE_ROLE)
        }
        if (!seenRoles.contains(TvTimeRole.MOVIE)) fail(TvTimeParseFailureKind.MISSING_ROLE)
        if (!seenRoles.contains(TvTimeRole.SERIES)) fail(TvTimeParseFailureKind.MISSING_ROLE)
        if (seasonCount > TvTimeImportLimits.MAX_SEASON_RECORDS ||
            episodes.size > TvTimeImportLimits.MAX_EPISODE_RECORDS
        ) {
            fail(TvTimeParseFailureKind.TOO_LARGE)
        }

        listLinks.forEach { link ->
            val found = when (link.type) {
                "movie" -> link.uuid != null && movies.any {
                    it.identities.any { identity ->
                        identity.namespace == ImportedIdentityNamespace.TV_TIME && identity.value == link.uuid
                    }
                }
                else -> link.tvdbId != null && series.any {
                    it.identities.any { identity ->
                        identity.namespace == ImportedIdentityNamespace.TVDB && identity.value == link.tvdbId.toString()
                    }
                }
            }
            if (!found) warnings.add(ImportWarning(ImportWarningCode.ORPHAN_LIST_LINK, link.location))
        }

        val unsupportedNames = buildSet {
            if (listCount > 0) add("custom_lists")
            if (favoriteCount > 0) add("is_favorite")
            if (rewatchCount > 0 || watchedCount > 0) add("rewatch_count")
            if (statusCount > 0) add("status")
            if (technicalFlagCount > 0) add("_noEpisodeData")
        }
        val unsupported = ImportedUnsupportedFields(
            favoriteRecords = favoriteCount,
            customLists = listCount,
            rewatchRecords = rewatchCount,
            watchedCountRecords = watchedCount,
            sourceStatusRecords = statusCount,
            technicalFlagRecords = technicalFlagCount,
            names = unsupportedNames
        )
        return ImportedSourceDocument(
            profileId = TV_TIME_PROFILE_ID,
            movies = movies,
            series = series,
            episodes = episodes,
            summary = ImportedSourceSummary(
                movieRecordCount = movies.size,
                seriesCount = series.size,
                seasonCount = seasonCount,
                episodeCount = episodes.size,
                watchedMovieCount = watchedMovies,
                watchedEpisodeCount = watchedEpisodes,
                specialsCount = specials,
                recordsWithImdbIds = recordsWithImdb,
                recordsWithTvdbIds = recordsWithTvdb,
                warningCount = warnings.values.sumOf(ImportWarning::occurrenceCount),
                invalidRecordCount = invalidRecords,
                unsupported = unsupported
            ),
            warnings = warnings.values
        )
    }

    private fun parseList(value: JsonElement, entryIndex: Int, warnings: WarningCollector): TvTimeListDto {
        val objectValue = value.asObjectOrInvalid()
        warnUnknown(objectValue, LIST_FIELDS, ImportSourceLocation(entryIndex, 0, "$[0]"), warnings)
        val items = requiredArray(objectValue, "items", "$.items")
        if (items.size() > TvTimeImportLimits.MAX_LIST_ITEMS) fail(TvTimeParseFailureKind.TOO_LARGE)
        return TvTimeListDto(
            id = requiredString(objectValue, "id", "$.id"),
            name = requiredString(objectValue, "name", "$.name"),
            description = requiredString(objectValue, "description", "$.description"),
            isPublic = requiredBoolean(objectValue, "is_public", "$.is_public"),
            createdAt = requiredString(objectValue, "created_at", "$.created_at").also(::parseCreatedTimestamp),
            items = items.mapIndexed { index, element -> parseListItem(element, entryIndex, index, warnings) }
        )
    }

    private fun parseListItem(
        value: JsonElement,
        entryIndex: Int,
        index: Int,
        warnings: WarningCollector
    ): TvTimeListItemDto {
        val location = ImportSourceLocation(entryIndex, index, "$.items[$index]")
        val objectValue = value.asObjectOrInvalid()
        warnUnknown(objectValue, LIST_ITEM_FIELDS, location, warnings)
        return TvTimeListItemDto(
            customOrder = requiredInt(objectValue, "custom_order", "${location.path}.custom_order"),
            name = requiredString(objectValue, "name", "${location.path}.name"),
            type = requiredString(objectValue, "type", "${location.path}.type"),
            tvdbId = optionalLong(objectValue, "tvdb_id", "${location.path}.tvdb_id"),
            uuid = optionalString(objectValue, "uuid", "${location.path}.uuid")
        )
    }

    private fun processMovie(
        value: JsonElement,
        entryIndex: Int,
        index: Int,
        warnings: WarningCollector,
        identityOwners: MutableMap<String, String>
    ): ParsedValue<ImportedMediaHint> {
        val location = ImportSourceLocation(entryIndex, index, "$[$index]")
        val originalOwners = identityOwners.toMap()
        return try {
            val objectValue = value.asObjectOrInvalid()
            warnUnknown(objectValue, MOVIE_FIELDS, location, warnings)
            val ids = parseIds(requiredObject(objectValue, "id", "${location.path}.id"), location, warnings)
            val imdb = ids.imdb
            val tvdb = ids.tvdb
            if (imdb == null && tvdb == null) throw InvalidRecordException("id")
            val uuid = requiredUuid(objectValue, "uuid", "${location.path}.uuid")
            val title = requiredText(objectValue, "title", "${location.path}.title")
            val year = requiredInt(objectValue, "year", "${location.path}.year")
                .takeIf { it in 1000..9999 } ?: throw InvalidRecordException("year")
            val created = optionalCreatedTimestamp(objectValue, location)
            val watched = requiredBoolean(objectValue, "is_watched", "${location.path}.is_watched")
            val watchedAt = optionalTimestamp(objectValue, "watched_at", watched, location)
            val rewatch = requiredNonNegativeInt(objectValue, "rewatch_count", "${location.path}.rewatch_count")
            val favorite = requiredBoolean(objectValue, "is_favorite", "${location.path}.is_favorite")
            val identities = buildList {
                imdb?.let { add(ImportedSourceIdentity(ImportedIdentityNamespace.IMDB, it)) }
                tvdb?.let { add(ImportedSourceIdentity(ImportedIdentityNamespace.TVDB, it.toString())) }
                add(ImportedSourceIdentity(ImportedIdentityNamespace.TV_TIME, uuid))
            }
            ensureUniqueIdentities(identityOwners, identities, "movie:$index")
            val recordWarnings = buildList {
                add(ImportWarning(ImportWarningCode.UNSUPPORTED_FIELD, location, "is_favorite"))
                add(ImportWarning(ImportWarningCode.UNSUPPORTED_FIELD, location, "rewatch_count"))
                if (created == null) add(ImportWarning(ImportWarningCode.APPROXIMATE_TIMESTAMP, location, "created_at"))
            }
            ParsedValue(
                ImportedMediaHint(
                    recordId = "movie:$index",
                    mediaType = MediaType.MOVIE,
                    title = title,
                    normalizedTitle = normalizeImportedTitle(title),
                    year = year,
                    createdAt = created,
                    identities = identities,
                    watch = ImportedWatchRecord(watched, watchedAt, rewatch, null, recordWarnings),
                    sourceLocation = location,
                    warnings = recordWarnings
                ),
                null
            )
        } catch (_: InvalidRecordException) {
            identityOwners.clear()
            identityOwners.putAll(originalOwners)
            ParsedValue(null, ImportWarning(ImportWarningCode.INVALID_RECORD, location))
        }
    }

    private fun processSeries(
        value: JsonElement,
        entryIndex: Int,
        index: Int,
        warnings: WarningCollector,
        identityOwners: MutableMap<String, String>
    ): ParsedSeriesResult {
        val location = ImportSourceLocation(entryIndex, index, "$[$index]")
        val originalOwners = identityOwners.toMap()
        return try {
            val objectValue = value.asObjectOrInvalid()
            warnUnknown(objectValue, SERIES_FIELDS, location, warnings)
            val ids = parseIds(requiredObject(objectValue, "id", "${location.path}.id"), location, warnings)
            val tvdb = ids.tvdb?.takeIf { it > 0 } ?: throw InvalidRecordException("tvdb")
            val imdb = ids.imdb
            val uuid = requiredUuid(objectValue, "uuid", "${location.path}.uuid")
            val title = requiredText(objectValue, "title", "${location.path}.title")
            val created = optionalCreatedTimestamp(objectValue, location)
            val favorite = requiredBoolean(objectValue, "is_favorite", "${location.path}.is_favorite")
            requiredText(objectValue, "status", "${location.path}.status")
            requiredBoolean(objectValue, "_noEpisodeData", "${location.path}._noEpisodeData")
            val seasons = requiredArray(objectValue, "seasons", "${location.path}.seasons")
            val identities = buildList {
                add(ImportedSourceIdentity(ImportedIdentityNamespace.TVDB, tvdb.toString()))
                imdb?.let { add(ImportedSourceIdentity(ImportedIdentityNamespace.IMDB, it)) }
                add(ImportedSourceIdentity(ImportedIdentityNamespace.TV_TIME, uuid))
            }
            ensureUniqueIdentities(identityOwners, identities, "series:$index")
            val recordWarnings = buildList {
                add(ImportWarning(ImportWarningCode.UNSUPPORTED_FIELD, location, "is_favorite"))
                add(ImportWarning(ImportWarningCode.UNSUPPORTED_FIELD, location, "status"))
                add(ImportWarning(ImportWarningCode.UNSUPPORTED_FIELD, location, "_noEpisodeData"))
                if (created == null) add(ImportWarning(ImportWarningCode.APPROXIMATE_TIMESTAMP, location, "created_at"))
            }
            val parsedEpisodes = mutableListOf<ImportedEpisodeHint>()
            var invalidEpisodeCount = 0
            var seasonsSeen = 0
            val seasonNumbers = mutableSetOf<Int>()
            seasons.forEachIndexed { seasonIndex, seasonValue ->
                if (seasonsSeen >= TvTimeImportLimits.MAX_SEASON_RECORDS) fail(TvTimeParseFailureKind.TOO_LARGE)
                val seasonLocation = ImportSourceLocation(entryIndex, index, "${location.path}.seasons[$seasonIndex]")
                val seasonObject = seasonValue.asObjectOrInvalid()
                warnUnknown(seasonObject, SEASON_FIELDS, seasonLocation, warnings)
                val seasonNumber = requiredInt(seasonObject, "number", "${seasonLocation.path}.number")
                if (seasonNumber < 0) throw InvalidRecordException("season number")
                if (!seasonNumbers.add(seasonNumber)) fail(TvTimeParseFailureKind.DUPLICATE_IDENTITY)
                val specialsSeason = requiredBoolean(seasonObject, "is_specials", "${seasonLocation.path}.is_specials")
                val episodeArray = requiredArray(seasonObject, "episodes", "${seasonLocation.path}.episodes")
                val episodeNumbers = mutableSetOf<Int>()
                if (seasonNumber >= 1000) {
                    warnings.add(ImportWarning(ImportWarningCode.HIGH_SEASON_NUMBER, seasonLocation, "number"))
                }
                episodeArray.forEachIndexed { episodeIndex, episodeValue ->
                    if (parsedEpisodes.size >=
                        TvTimeImportLimits.MAX_EPISODE_RECORDS
                    ) {
                        fail(TvTimeParseFailureKind.TOO_LARGE)
                    }
                    val episodeLocation = ImportSourceLocation(
                        entryIndex,
                        index,
                        "${seasonLocation.path}.episodes[$episodeIndex]"
                    )
                    val parsedEpisode = parseEpisode(
                        value = episodeValue,
                        location = episodeLocation,
                        parentRecordId = "series:$index",
                        parentIdentities = identities,
                        seasonNumber = seasonNumber,
                        specialsSeason = specialsSeason,
                        identityOwners = identityOwners,
                        warnings = warnings
                    )
                    if (parsedEpisode == null) {
                        invalidEpisodeCount++
                    } else {
                        if (!episodeNumbers.add(parsedEpisode.episodeNumber)) {
                            fail(TvTimeParseFailureKind.DUPLICATE_IDENTITY)
                        }
                        parsedEpisodes += parsedEpisode
                    }
                }
                seasonsSeen++
            }
            ParsedSeriesResult(
                value = ParsedSeries(
                    hint = ImportedMediaHint(
                        recordId = "series:$index",
                        mediaType = MediaType.SERIES,
                        title = title,
                        normalizedTitle = normalizeImportedTitle(title),
                        year = null,
                        createdAt = created,
                        identities = identities,
                        watch = null,
                        sourceLocation = location,
                        warnings = recordWarnings
                    ),
                    episodes = parsedEpisodes,
                    seasonCount = seasonsSeen,
                    invalidEpisodeCount = invalidEpisodeCount,
                    statusPresent = true,
                    technicalFlagPresent = true
                ),
                warning = null
            )
        } catch (_: InvalidRecordException) {
            identityOwners.clear()
            identityOwners.putAll(originalOwners)
            ParsedSeriesResult(value = null, warning = ImportWarning(ImportWarningCode.INVALID_RECORD, location))
        }
    }

    private fun parseEpisode(
        value: JsonElement,
        location: ImportSourceLocation,
        parentRecordId: String,
        parentIdentities: List<ImportedSourceIdentity>,
        seasonNumber: Int,
        specialsSeason: Boolean,
        identityOwners: MutableMap<String, String>,
        warnings: WarningCollector
    ): ImportedEpisodeHint? = try {
        val objectValue = value.asObjectOrInvalid()
        warnUnknown(objectValue, EPISODE_FIELDS, location, warnings)
        val ids = parseIds(requiredObject(objectValue, "id", "${location.path}.id"), location, warnings)
        val imdb = ids.imdb
        val tvdb = ids.tvdb
        val name = requiredText(objectValue, "name", "${location.path}.name")
        val number = requiredInt(objectValue, "number", "${location.path}.number")
        if (number <= 0) throw InvalidRecordException("episode number")
        val special = requiredBoolean(objectValue, "special", "${location.path}.special")
        val watched = requiredBoolean(objectValue, "is_watched", "${location.path}.is_watched")
        val watchedAt = optionalTimestamp(objectValue, "watched_at", watched, location)
        val rewatch = requiredNonNegativeInt(objectValue, "rewatch_count", "${location.path}.rewatch_count")
        val watchedCount = requiredNonNegativeInt(objectValue, "watched_count", "${location.path}.watched_count")
        val identities = buildList {
            imdb?.let { add(ImportedSourceIdentity(ImportedIdentityNamespace.IMDB, it)) }
            tvdb?.let { add(ImportedSourceIdentity(ImportedIdentityNamespace.TVDB, it.toString())) }
        }
        val recordId = "$parentRecordId/season:$seasonNumber/episode:$number"
        if (identities.isNotEmpty()) ensureUniqueIdentities(identityOwners, identities, recordId)
        val recordWarnings = buildList {
            if (rewatch > 0) add(ImportWarning(ImportWarningCode.UNSUPPORTED_FIELD, location, "rewatch_count"))
            if (watchedCount > 0) add(ImportWarning(ImportWarningCode.UNSUPPORTED_FIELD, location, "watched_count"))
        }
        ImportedEpisodeHint(
            recordId = recordId,
            parentRecordId = parentRecordId,
            parentIdentities = parentIdentities,
            seasonNumber = seasonNumber,
            episodeNumber = number,
            title = name,
            normalizedTitle = normalizeImportedTitle(name),
            special = special,
            specialsSeason = specialsSeason,
            identities = identities,
            watch = ImportedWatchRecord(watched, watchedAt, rewatch, watchedCount, recordWarnings),
            sourceLocation = location,
            warnings = recordWarnings
        )
    } catch (_: InvalidRecordException) {
        warnings.add(ImportWarning(ImportWarningCode.INVALID_RECORD, location))
        null
    }

    private fun parseIds(
        value: JsonObject,
        location: ImportSourceLocation,
        warnings: WarningCollector
    ): TvTimeSourceIdsDto {
        warnUnknown(value, IDS_FIELDS, location, warnings)
        val imdbValue = nullableString(value, "imdb", "${location.path}.id.imdb")
        val imdb = imdbValue?.takeIf { IMDB_PATTERN.matcher(it).matches() }
            ?: if (imdbValue == null) null else throw InvalidRecordException("imdb")
        val tvdbValue = nullableLong(value, "tvdb", "${location.path}.id.tvdb")
        val tvdb = tvdbValue?.takeIf { it > 0 }
            ?: if (tvdbValue == null) null else throw InvalidRecordException("tvdb")
        return TvTimeSourceIdsDto(imdb = imdb, tvdb = tvdb)
    }

    private fun optionalTimestamp(
        objectValue: JsonObject,
        field: String,
        watched: Boolean,
        location: ImportSourceLocation
    ): ImportedTimestamp? {
        val value = if (!objectValue.has(field) || objectValue.get(field).isJsonNull) {
            null
        } else {
            requiredString(objectValue, field, "${location.path}.$field")
        }
        if (!watched && value != null) throw InvalidRecordException("$field consistency")
        if (watched && value == null) throw InvalidRecordException("$field consistency")
        return value?.let(::parseWatchedTimestamp)
    }

    private fun parseCreatedTimestamp(value: String): ImportedTimestamp {
        val fraction = value.substringAfter('.', "").removeSuffix("Z")
        if (!CREATED_AT_PATTERN.matcher(value).matches()) throw InvalidRecordException("created_at")
        val instant = try {
            Instant.parse(value)
        } catch (_: DateTimeParseException) {
            throw InvalidRecordException("created_at")
        }
        return ImportedTimestamp(value, instant, fraction.length)
    }

    private fun optionalCreatedTimestamp(value: JsonObject, location: ImportSourceLocation): ImportedTimestamp? {
        if (!value.has("created_at") || value.get("created_at").isJsonNull) return null
        return parseCreatedTimestamp(requiredString(value, "created_at", "${location.path}.created_at"))
    }

    private fun parseWatchedTimestamp(value: String): ImportedTimestamp {
        if (!WATCHED_AT_PATTERN.matcher(value).matches()) throw InvalidRecordException("watched_at")
        val instant = try {
            Instant.parse(value)
        } catch (_: DateTimeParseException) {
            throw InvalidRecordException("watched_at")
        }
        return ImportedTimestamp(value, instant, 0)
    }

    private fun ensureUniqueIdentities(
        owners: MutableMap<String, String>,
        identities: List<ImportedSourceIdentity>,
        owner: String
    ) {
        if (identities.map(ImportedSourceIdentity::key).toSet().size != identities.size) {
            fail(TvTimeParseFailureKind.DUPLICATE_IDENTITY)
        }
        identities.forEach { identity ->
            val key = identity.key()
            val previous = owners.putIfAbsent(key, owner)
            if (previous != null && previous != owner) {
                fail(TvTimeParseFailureKind.CONFLICTING_IDENTITY)
            }
        }
    }

    private fun classify(value: JsonElement): TvTimeRole {
        val objectValue = value.asObjectOrInvalid()
        val list = objectValue.has("items") && objectValue.has("is_public") && objectValue.has("description")
        val movie = objectValue.has("year") && objectValue.has("watched_at") && objectValue.has("is_watched")
        val series = objectValue.has("seasons") && objectValue.has("status") && objectValue.has("_noEpisodeData")
        return when (list) {
            true -> if (!movie && !series) TvTimeRole.LIST else fail(TvTimeParseFailureKind.AMBIGUOUS_ROLE)
            false -> when {
                movie && !series -> TvTimeRole.MOVIE
                series && !movie -> TvTimeRole.SERIES
                else -> fail(
                    if (movie ||
                        series
                    ) {
                        TvTimeParseFailureKind.AMBIGUOUS_ROLE
                    } else {
                        TvTimeParseFailureKind.UNKNOWN_ROLE
                    }
                )
            }
        }
    }

    private fun warnUnknown(
        value: JsonObject,
        known: Set<String>,
        location: ImportSourceLocation,
        warnings: WarningCollector
    ) {
        value.keySet().filterNot(known::contains).forEach { field ->
            warnings.add(ImportWarning(ImportWarningCode.UNKNOWN_FIELD, location, field.take(128)))
        }
    }

    private fun readRecord(
        reader: JsonReader,
        input: RecordGuardInputStream,
        coroutineContext: CoroutineContext,
        expectedRole: TvTimeRole? = null
    ): JsonElement {
        coroutineContext.ensureActive()
        input.beginRecord()
        val value = readElement(reader, 0)
        if (expectedRole != null && classify(value) != expectedRole) {
            fail(TvTimeParseFailureKind.INVALID_STRUCTURE)
        }
        return value
    }

    private fun readElement(reader: JsonReader, depth: Int): JsonElement {
        if (depth > TvTimeImportLimits.MAX_JSON_DEPTH) fail(TvTimeParseFailureKind.TOO_LARGE)
        return when (reader.peek()) {
            JsonToken.BEGIN_OBJECT -> {
                reader.beginObject()
                val objectValue = JsonObject()
                val seen = HashSet<String>()
                while (reader.hasNext()) {
                    val name = reader.nextName()
                    if (!seen.add(name)) fail(TvTimeParseFailureKind.DUPLICATE_JSON_KEY)
                    objectValue.add(name, readElement(reader, depth + 1))
                }
                reader.endObject()
                objectValue
            }
            JsonToken.END_OBJECT, JsonToken.END_ARRAY, JsonToken.END_DOCUMENT, JsonToken.NAME ->
                fail(TvTimeParseFailureKind.INVALID_STRUCTURE)
            JsonToken.BEGIN_ARRAY -> {
                reader.beginArray()
                val array = JsonArray()
                while (reader.hasNext()) array.add(readElement(reader, depth + 1))
                reader.endArray()
                array
            }
            JsonToken.STRING -> JsonPrimitive(
                reader.nextString().also {
                    if (it.length > TvTimeImportLimits.MAX_STRING_LENGTH) fail(TvTimeParseFailureKind.TOO_LARGE)
                }
            )
            JsonToken.NUMBER -> {
                val raw = reader.nextString()
                if (raw.length > 64) fail(TvTimeParseFailureKind.TOO_LARGE)
                JsonPrimitive(
                    try {
                        BigDecimal(raw)
                    } catch (_: NumberFormatException) {
                        fail(TvTimeParseFailureKind.INVALID_STRUCTURE)
                    }
                )
            }
            JsonToken.BOOLEAN -> JsonPrimitive(reader.nextBoolean())
            JsonToken.NULL -> {
                reader.nextNull()
                JsonNull.INSTANCE
            }
        }
    }

    private fun JsonElement.asObjectOrInvalid(): JsonObject =
        if (isJsonObject) asJsonObject else throw InvalidRecordException("object")

    private fun requiredObject(value: JsonObject, field: String, path: String): JsonObject =
        value.get(field)?.takeIf(JsonElement::isJsonObject)?.asJsonObject ?: throw InvalidRecordException(path)

    private fun requiredArray(value: JsonObject, field: String, path: String): JsonArray =
        value.get(field)?.takeIf(JsonElement::isJsonArray)?.asJsonArray ?: throw InvalidRecordException(path)

    private fun requiredString(value: JsonObject, field: String, path: String): String =
        value.get(field)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
            ?.takeIf { it.length <= TvTimeImportLimits.MAX_STRING_LENGTH }
            ?: throw InvalidRecordException(path)

    private fun requiredText(value: JsonObject, field: String, path: String): String =
        requiredString(value, field, path).trim().takeIf(String::isNotEmpty) ?: throw InvalidRecordException(path)

    private fun nullableString(value: JsonObject, field: String, path: String): String? {
        if (!value.has(field) || value.get(field).isJsonNull) return null
        return requiredString(value, field, path)
    }

    private fun optionalString(value: JsonObject, field: String, path: String): String? =
        if (!value.has(field) || value.get(field).isJsonNull) null else requiredString(value, field, path)

    private fun requiredBoolean(value: JsonObject, field: String, path: String): Boolean =
        value.get(field)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.asBoolean
            ?: throw InvalidRecordException(path)

    private fun requiredInt(value: JsonObject, field: String, path: String): Int =
        value.get(field)?.let { number(it, path) }?.toIntExactOrNull() ?: throw InvalidRecordException(path)

    private fun requiredNonNegativeInt(value: JsonObject, field: String, path: String): Int =
        requiredInt(value, field, path).takeIf { it >= 0 } ?: throw InvalidRecordException(path)

    private fun optionalLong(value: JsonObject, field: String, path: String): Long? =
        if (!value.has(field) || value.get(field).isJsonNull) null else nullableLong(value, field, path)

    private fun nullableLong(value: JsonObject, field: String, path: String): Long? {
        if (!value.has(field) || value.get(field).isJsonNull) return null
        return number(value.get(field), path).toLongExactOrNull() ?: throw InvalidRecordException(path)
    }

    private fun number(value: JsonElement, path: String): BigDecimal =
        value.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asBigDecimal
            ?: throw InvalidRecordException(path)

    private fun requiredUuid(value: JsonObject, field: String, path: String): String {
        val text = requiredString(value, field, path)
        if (!UUID_PATTERN.matcher(text).matches()) throw InvalidRecordException(path)
        return text.lowercase()
    }

    private fun BigDecimal.toIntExactOrNull(): Int? = try {
        intValueExact()
    } catch (_: ArithmeticException) {
        null
    }

    private fun BigDecimal.toLongExactOrNull(): Long? = try {
        longValueExact()
    } catch (_: ArithmeticException) {
        null
    }

    private fun fail(kind: TvTimeParseFailureKind): Nothing = throw TvTimeParseFailure(kind)

    private data class ParsedValue<T>(val value: T?, val warning: ImportWarning?)

    private data class ParsedSeriesResult(val value: ParsedSeries?, val warning: ImportWarning?)

    private data class ParsedSeries(
        val hint: ImportedMediaHint,
        val episodes: List<ImportedEpisodeHint>,
        val seasonCount: Int,
        val invalidEpisodeCount: Int,
        val statusPresent: Boolean,
        val technicalFlagPresent: Boolean
    )

    private data class ListLink(
        val type: String,
        val tvdbId: Long?,
        val uuid: String?,
        val location: ImportSourceLocation
    )

    private enum class TvTimeRole { LIST, MOVIE, SERIES }

    private class InvalidRecordException(message: String) : Exception(message)

    private class WarningCollector {
        val values = mutableListOf<ImportWarning>()

        fun add(warning: ImportWarning) {
            if (warning.code == ImportWarningCode.UNKNOWN_FIELD) {
                val safePath = warning.location?.path?.replace(RECORD_INDEX_PATTERN, "[*]")
                val existing = values.indexOfFirst {
                    it.code == ImportWarningCode.UNKNOWN_FIELD &&
                        it.fieldName == warning.fieldName &&
                        it.location?.path?.replace(RECORD_INDEX_PATTERN, "[*]") == safePath
                }
                if (existing >= 0) {
                    values[existing] = values[existing].copy(
                        occurrenceCount = values[existing].occurrenceCount + warning.occurrenceCount
                    )
                    return
                }
            }
            if (values.size < TvTimeImportLimits.MAX_UNKNOWN_FIELD_WARNINGS) values += warning
        }
    }

    private class RecordGuardInputStream(input: java.io.InputStream, private val coroutineContext: CoroutineContext) :
        FilterInputStream(input) {
        private var recordBytes = 0L

        fun beginRecord() {
            recordBytes = 0L
            coroutineContext.ensureActive()
        }

        override fun read(): Int {
            coroutineContext.ensureActive()
            return super.read().also { if (it >= 0) count(1) }
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            coroutineContext.ensureActive()
            return super.read(buffer, offset, length).also { if (it > 0) count(it.toLong()) }
        }

        private fun count(value: Long) {
            recordBytes += value
            if (recordBytes > TvTimeImportLimits.MAX_JSON_RECORD_BYTES) {
                throw TvTimeParseFailure(TvTimeParseFailureKind.TOO_LARGE)
            }
        }
    }

    private companion object {
        val UUID_PATTERN = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"
        )
        val IMDB_PATTERN = Pattern.compile("tt[0-9]+")
        val RECORD_INDEX_PATTERN = Regex("\\[\\d+]")
        val CREATED_AT_PATTERN = Pattern.compile(
            "[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(\\.[0-9]{4,6})?Z"
        )
        val WATCHED_AT_PATTERN = Pattern.compile("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z")
        val LIST_FIELDS = setOf("id", "name", "description", "is_public", "created_at", "items")
        val LIST_ITEM_FIELDS = setOf("custom_order", "name", "type", "tvdb_id", "uuid")
        val IDS_FIELDS = setOf("imdb", "tvdb")
        val MOVIE_FIELDS =
            setOf("created_at", "id", "is_favorite", "is_watched", "rewatch_count", "title", "uuid", "watched_at", "year")
        val SERIES_FIELDS =
            setOf("_noEpisodeData", "created_at", "id", "is_favorite", "seasons", "status", "title", "uuid")
        val SEASON_FIELDS = setOf("episodes", "is_specials", "number")
        val EPISODE_FIELDS =
            setOf("id", "is_watched", "name", "number", "rewatch_count", "special", "watched_at", "watched_count")
    }
}

internal fun normalizeImportedTitle(value: String): String =
    java.text.Normalizer.normalize(value.trim(), java.text.Normalizer.Form.NFKC).lowercase(java.util.Locale.ROOT)
