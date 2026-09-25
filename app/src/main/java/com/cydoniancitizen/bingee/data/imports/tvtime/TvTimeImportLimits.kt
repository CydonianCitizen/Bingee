package com.cydoniancitizen.bingee.data.imports.tvtime

import com.cydoniancitizen.bingee.data.imports.PortableRecordLimits

internal const val TV_TIME_PROFILE_ID = "TVTIME-SAMPLE-001"

internal object TvTimeImportLimits {
    const val MAX_COMPRESSED_INPUT_BYTES = 200L * 1024 * 1024
    const val MAX_TOTAL_UNCOMPRESSED_BYTES = 1L * 1024 * 1024 * 1024
    const val MAX_ENTRY_UNCOMPRESSED_BYTES = 500L * 1024 * 1024
    const val MAX_ENTRY_COUNT = 100_000
    const val MAX_COMPRESSION_RATIO = 100L
    const val MAX_JSON_DEPTH = 64
    const val MAX_JSON_RECORD_BYTES = 32L * 1024 * 1024
    const val MAX_MOVIE_RECORDS = 50_000
    const val MAX_SERIES_RECORDS = 50_000
    const val MAX_MEDIA_RECORDS = PortableRecordLimits.MAX_MEDIA
    const val MAX_SEASON_RECORDS = PortableRecordLimits.MAX_SEASONS
    const val MAX_EPISODE_RECORDS = PortableRecordLimits.MAX_EPISODES
    const val MAX_LIST_ITEMS = 100_000
    const val MAX_STRING_LENGTH = 8_192
    const val MAX_UNKNOWN_FIELD_WARNINGS = 2_000
    const val MAX_CANDIDATES = 20
}

internal enum class TvTimeArchiveFailureKind {
    UNREADABLE_ZIP,
    OVERSIZED_INPUT,
    OVERSIZED_ENTRY,
    OVERSIZED_TOTAL,
    SUSPICIOUS_COMPRESSION,
    PATH_TRAVERSAL,
    ABSOLUTE_PATH,
    DRIVE_PATH,
    UNC_PATH,
    NULL_BYTE_PATH,
    DUPLICATE_PATH,
    CASE_COLLISION,
    ENCRYPTED_ENTRY,
    NESTED_ARCHIVE,
    UNSUPPORTED_ENTRY,
    TOO_MANY_ENTRIES,
    MALFORMED_ARCHIVE,
    UNSUPPORTED_LAYOUT
}

internal data class TvTimeArchiveFailure(val kind: TvTimeArchiveFailureKind) : Exception()
