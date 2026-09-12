package com.cydoniancitizen.bingee.data.importexport

import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import java.time.Instant
import java.time.LocalDate

internal const val BACKUP_FORMAT_ID = "bingee-backup"
internal const val BACKUP_SCHEMA_VERSION_V1 = 1
internal const val BACKUP_SCHEMA_VERSION = 2
internal const val BACKUP_MIME_TYPE = "application/json"
internal const val MAX_BACKUP_BYTES = 50 * 1024 * 1024

internal object BackupLimits {
    const val MAX_MEDIA = 50_000
    const val MAX_SEASONS = 100_000
    const val MAX_EPISODES = 500_000
    const val MAX_STRING = 8_192
    const val MAX_URL = 2_048
    const val MAX_GENRES_PER_MEDIA = 100
}

internal data class BackupRef(val source: MediaSource, val externalId: String)

internal fun BackupRef.key(): String = "${source.name}:$externalId"

/** Media identity: TMDB numbers movies and series independently, so the type is part of the key. */
internal fun BackupRef.key(type: MediaType): String = "${type.name}:${key()}"

internal data class BackupGenre(val name: String, val source: MediaSource?, val genreId: Long?)

internal data class BackupMedia(
    val primaryRef: BackupRef,
    val externalRefs: List<BackupRef>,
    val mediaType: MediaType,
    val title: String,
    val originalTitle: String?,
    val overview: String?,
    val posterUrl: String?,
    val releaseDate: LocalDate?,
    val isFavorite: Boolean = false,
    val favoriteAddedAt: Instant? = null,
    val genres: List<BackupGenre> = emptyList(),
    /** Movie runtime, so offline statistics survive a restore that drops the Details cache. */
    val runtimeMinutes: Int? = null
)

internal data class BackupSeason(
    val mediaRef: BackupRef,
    val externalRef: BackupRef,
    val seasonNumber: Int,
    val name: String?,
    val overview: String?,
    val posterUrl: String?,
    val airDate: LocalDate?,
    val episodeCount: Int
)

internal data class BackupEpisode(
    val seasonRef: BackupRef,
    val externalRef: BackupRef,
    val episodeNumber: Int,
    val title: String,
    val overview: String?,
    val airDate: LocalDate?,
    val runtimeMinutes: Int?,
    val stillUrl: String?
)

/** [mediaType] tells a movie from a series with the same TMDB ID; v1 files omit it and are never ambiguous. */
internal data class BackupLibraryEntry(val mediaRef: BackupRef, val addedAt: Instant, val mediaType: MediaType? = null)

internal data class BackupMovieProgress(
    val mediaRef: BackupRef,
    val watchedAt: Instant,
    val watchedDate: LocalDate? = null
)

internal data class BackupSeriesProgress(
    val mediaRef: BackupRef,
    val completedAt: Instant,
    val watchedDate: LocalDate? = null
)

internal data class BackupAbandonedSeries(val mediaRef: BackupRef)

internal data class BackupEpisodeProgress(val episodeRef: BackupRef, val watchedAt: Instant)

internal data class BackupRating(
    val mediaRef: BackupRef,
    val rating: Int,
    val ratedAt: Instant,
    val updatedAt: Instant,
    val mediaType: MediaType? = null
)

internal data class BackupPreferences(
    val notificationLeadDays: Int,
    val notifyMovieReleases: Boolean,
    val notifySeasonPremieres: Boolean,
    val notifyEpisodeAirings: Boolean
)

internal data class BackupData(
    val media: List<BackupMedia>,
    val seasons: List<BackupSeason>,
    val episodes: List<BackupEpisode>,
    val library: List<BackupLibraryEntry>,
    val movieProgress: List<BackupMovieProgress>,
    val episodeProgress: List<BackupEpisodeProgress>,
    val ratings: List<BackupRating>,
    val preferences: BackupPreferences,
    val seriesProgress: List<BackupSeriesProgress> = emptyList(),
    val abandonedSeries: List<BackupAbandonedSeries> = emptyList()
)

internal data class BackupDocument(
    val formatId: String,
    val schemaVersion: Int,
    val exportedAt: Instant,
    val data: BackupData
)

internal enum class BackupFailureKind {
    UNREADABLE,
    TOO_LARGE,
    INVALID_UTF8,
    MALFORMED_JSON,
    WRONG_FORMAT,
    MISSING_VERSION,
    UNSUPPORTED_VERSION,
    INVALID_STRUCTURE,
    VALIDATION,
    DUPLICATE_IDENTITY,
    MISSING_REFERENCE,
    CONFLICTING_REFERENCE,
    WRITE_FAILED,
    TRANSACTION_FAILED,
    SCHEDULING_WARNING
}

internal data class BackupParseFailure(val kind: BackupFailureKind) : Exception()

internal sealed interface BackupParseResult {
    data class Success(val document: BackupDocument) : BackupParseResult
    data class Failure(val failure: BackupParseFailure) : BackupParseResult
}
