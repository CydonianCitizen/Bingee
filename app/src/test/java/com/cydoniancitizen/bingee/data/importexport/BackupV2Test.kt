package com.cydoniancitizen.bingee.data.importexport

import com.cydoniancitizen.bingee.core.model.AnimeCompletionOrigin
import com.cydoniancitizen.bingee.core.model.AnimeFormat
import com.cydoniancitizen.bingee.core.model.AnimeStatus
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupV2Test {
    private val now = Instant.parse("2026-08-05T10:00:00Z")
    private val tmdb = BackupRef(MediaSource.TMDB, "1")
    private val jikan = BackupRef(MediaSource.JIKAN, "1")

    @Test
    fun v2RoundTripPreservesAnimeAndProviderCollision() {
        val document = BackupDocument(BACKUP_FORMAT_ID, BACKUP_SCHEMA_VERSION, now, mixedData())

        val parsed = BackupJsonCodec.parse(BackupJsonCodec.encode(document)) as BackupParseResult.Success

        assertEquals(document, parsed.document)
        assertTrue(BackupValidator.validate(parsed.document) is BackupValidationResult.Success)
        assertEquals(2, parsed.document.data.media.map { it.primaryRef }.distinct().size)
        assertEquals(AnimeCompletionOrigin.EXPLICIT, parsed.document.data.animeProgress.single().completionOrigin)
    }

    @Test
    fun v1RemainsAcceptedAndCreatesNoAnimeRecords() {
        val v1 = BackupDocument(
            BACKUP_FORMAT_ID,
            BACKUP_SCHEMA_VERSION_V1,
            now,
            mixedData().copy(
                media = mixedData().media.filter { it.mediaType == MediaType.MOVIE },
                library = emptyList(),
                ratings = emptyList(),
                animeDetails = emptyList(),
                animeRelations = emptyList(),
                animeProgress = emptyList()
            )
        )

        val parsed = BackupJsonCodec.parse(BackupJsonCodec.encode(v1)) as BackupParseResult.Success

        assertEquals(BACKUP_SCHEMA_VERSION_V1, parsed.document.schemaVersion)
        assertTrue(parsed.document.data.animeDetails.isEmpty())
        assertTrue(BackupValidator.validate(parsed.document) is BackupValidationResult.Success)
    }

    @Test
    fun duplicateAnimeProgressAndCrossProviderRelationAreRejectedBeforeRestore() {
        val duplicate = BackupDocument(
            BACKUP_FORMAT_ID,
            BACKUP_SCHEMA_VERSION,
            now,
            mixedData().let { it.copy(animeProgress = it.animeProgress + it.animeProgress.single()) }
        )
        assertEquals(
            BackupFailureKind.DUPLICATE_IDENTITY,
            (BackupValidator.validate(duplicate) as BackupValidationResult.Failure).failure.kind
        )

        val crossProvider = BackupDocument(
            BACKUP_FORMAT_ID,
            BACKUP_SCHEMA_VERSION,
            now,
            mixedData().let {
                it.copy(animeRelations = it.animeRelations.map { relation -> relation.copy(relatedRef = tmdb) })
            }
        )
        assertEquals(
            BackupFailureKind.CONFLICTING_REFERENCE,
            (BackupValidator.validate(crossProvider) as BackupValidationResult.Failure).failure.kind
        )
    }

    private fun mixedData(): BackupData {
        val movie = BackupMedia(tmdb, listOf(tmdb), MediaType.MOVIE, "Movie", null, null, null, null)
        val anime = BackupMedia(jikan, listOf(jikan), MediaType.ANIME, "Anime", "アニメ", null, null, null)
        return BackupData(
            media = listOf(movie, anime),
            seasons = emptyList(),
            episodes = emptyList(),
            library = listOf(BackupLibraryEntry(jikan, now)),
            movieProgress = emptyList(),
            episodeProgress = emptyList(),
            ratings = listOf(BackupRating(jikan, 9, now, now)),
            preferences = BackupPreferences(1, true, true, true),
            animeDetails = listOf(
                BackupAnimeDetails(
                    jikan,
                    AnimeFormat.TV,
                    AnimeStatus.FINISHED,
                    "Anime",
                    "アニメ",
                    "Cached synopsis",
                    12,
                    "24 min",
                    LocalDate.parse("2025-01-01"),
                    LocalDate.parse("2025-03-01"),
                    "winter",
                    2025,
                    8.4,
                    "https://image/poster.jpg"
                )
            ),
            animeRelations = listOf(
                BackupAnimeRelation(
                    jikan,
                    "Sequel",
                    BackupRef(MediaSource.JIKAN, "2"),
                    "Anime 2",
                    AnimeFormat.TV
                )
            ),
            animeProgress = listOf(
                BackupAnimeProgress(jikan, 12, now, AnimeCompletionOrigin.EXPLICIT, now)
            )
        )
    }
}
