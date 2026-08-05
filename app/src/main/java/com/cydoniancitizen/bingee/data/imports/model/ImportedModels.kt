package com.cydoniancitizen.bingee.data.imports.model

import com.cydoniancitizen.bingee.core.model.MediaType
import java.time.Instant

/** Narrow, provider-neutral model for the supported external-history profile. */
internal enum class ImportedIdentityNamespace {
    TVDB,
    IMDB,
    TV_TIME
}

internal data class ImportedSourceIdentity(val namespace: ImportedIdentityNamespace, val value: String) {
    init {
        require(value.isNotBlank())
    }

    fun key(): String = "${namespace.name}:$value"
}

internal data class ImportSourceLocation(val entryIndex: Int, val recordIndex: Int, val path: String) {
    init {
        require(entryIndex >= 0)
        require(recordIndex >= 0)
        require(path.startsWith("$"))
    }
}

internal data class ImportedTimestamp(
    val original: String,
    val instant: Instant,
    val fractionalDigits: Int,
    val approximate: Boolean = false
)

internal enum class ImportWarningCode {
    UNKNOWN_FIELD,
    HIGH_SEASON_NUMBER,
    ORPHAN_LIST_LINK,
    DUPLICATE_IDENTITY,
    CONFLICTING_IDENTITY,
    INVALID_RECORD,
    UNSUPPORTED_FIELD,
    APPROXIMATE_TIMESTAMP,
    UNRESOLVED_SOURCE_ID
}

internal data class ImportWarning(
    val code: ImportWarningCode,
    val location: ImportSourceLocation?,
    val fieldName: String? = null,
    val occurrenceCount: Int = 1
)

internal data class ImportedWatchRecord(
    val watched: Boolean,
    val watchedAt: ImportedTimestamp?,
    val rewatchCount: Int?,
    val watchedCount: Int?,
    val warnings: List<ImportWarning> = emptyList()
)

internal data class ImportedMediaHint(
    val recordId: String,
    val mediaType: MediaType,
    val title: String,
    val normalizedTitle: String,
    val year: Int?,
    val createdAt: ImportedTimestamp?,
    val identities: List<ImportedSourceIdentity>,
    val watch: ImportedWatchRecord?,
    val sourceLocation: ImportSourceLocation,
    val warnings: List<ImportWarning>
)

internal data class ImportedEpisodeHint(
    val recordId: String,
    val parentRecordId: String,
    val parentIdentities: List<ImportedSourceIdentity>,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val title: String,
    val normalizedTitle: String,
    val special: Boolean,
    val specialsSeason: Boolean,
    val identities: List<ImportedSourceIdentity>,
    val watch: ImportedWatchRecord,
    val sourceLocation: ImportSourceLocation,
    val warnings: List<ImportWarning>
)

internal data class ImportedUnsupportedFields(
    val favoriteRecords: Int = 0,
    val customLists: Int = 0,
    val rewatchRecords: Int = 0,
    val watchedCountRecords: Int = 0,
    val sourceStatusRecords: Int = 0,
    val technicalFlagRecords: Int = 0,
    val names: Set<String> = emptySet()
)

internal data class ImportedSourceSummary(
    val movieRecordCount: Int,
    val seriesCount: Int,
    val seasonCount: Int,
    val episodeCount: Int,
    val watchedMovieCount: Int,
    val watchedEpisodeCount: Int,
    val specialsCount: Int,
    val recordsWithImdbIds: Int,
    val recordsWithTvdbIds: Int,
    val warningCount: Int,
    val invalidRecordCount: Int,
    val unsupported: ImportedUnsupportedFields,
    val ratingsImported: Int = 0
)

internal data class ImportedSourceDocument(
    val profileId: String,
    val movies: List<ImportedMediaHint>,
    val series: List<ImportedMediaHint>,
    val episodes: List<ImportedEpisodeHint>,
    val summary: ImportedSourceSummary,
    val warnings: List<ImportWarning>
)
