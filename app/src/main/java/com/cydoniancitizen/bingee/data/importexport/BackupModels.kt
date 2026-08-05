package com.cydoniancitizen.bingee.data.importexport

import com.cydoniancitizen.bingee.core.model.AnimeCompletionOrigin
import com.cydoniancitizen.bingee.core.model.AnimeFormat
import com.cydoniancitizen.bingee.core.model.AnimeStatus
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
}

internal data class BackupRef(val source: MediaSource, val externalId: String)

internal data class BackupMedia(
    val primaryRef: BackupRef,
    val externalRefs: List<BackupRef>,
    val mediaType: MediaType,
    val title: String,
    val originalTitle: String?,
    val overview: String?,
    val posterUrl: String?,
    val releaseDate: LocalDate?
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

internal data class BackupLibraryEntry(val mediaRef: BackupRef, val addedAt: Instant)

internal data class BackupMovieProgress(val mediaRef: BackupRef, val watchedAt: Instant)

internal data class BackupEpisodeProgress(val episodeRef: BackupRef, val watchedAt: Instant)

internal data class BackupRating(val mediaRef: BackupRef, val rating: Int, val ratedAt: Instant, val updatedAt: Instant)

internal data class BackupAnimeDetails(
    val mediaRef: BackupRef,
    val format: AnimeFormat,
    val status: AnimeStatus,
    val englishTitle: String?,
    val japaneseTitle: String?,
    val synopsis: String?,
    val episodeCount: Int?,
    val duration: String?,
    val startDate: LocalDate?,
    val endDate: LocalDate?,
    val season: String?,
    val year: Int?,
    val providerScore: Double?,
    val posterUrl: String?
)

internal data class BackupAnimeRelation(
    val mediaRef: BackupRef,
    val relationType: String,
    val relatedRef: BackupRef,
    val relatedTitle: String,
    val relatedFormat: AnimeFormat?
)

internal data class BackupAnimeProgress(
    val mediaRef: BackupRef,
    val watchedEpisodeCount: Int,
    val completedAt: Instant?,
    val completionOrigin: AnimeCompletionOrigin?,
    val updatedAt: Instant
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
    val animeDetails: List<BackupAnimeDetails> = emptyList(),
    val animeRelations: List<BackupAnimeRelation> = emptyList(),
    val animeProgress: List<BackupAnimeProgress> = emptyList()
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
