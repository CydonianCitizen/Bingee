package com.cydoniancitizen.bingee.data.importexport

import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupValidatorTest {
    @Test
    fun acceptsSeasonZeroAndCrossReferenceGraph() {
        val data = baseData().copy(
            media = listOf(series()),
            seasons = listOf(season()),
            episodes = listOf(episode()),
            episodeProgress = listOf(BackupEpisodeProgress(ref("episode"), instant))
        )
        assertTrue(BackupValidator.validate(document(data)) is BackupValidationResult.Success)
    }

    @Test
    fun rejectsDuplicateMediaAndMissingReferences() {
        val duplicate = baseData().copy(media = listOf(movie(), movie()))
        assertEquals(
            BackupFailureKind.DUPLICATE_IDENTITY,
            failure(duplicate)
        )
        val missing = baseData().copy(
            library = listOf(BackupLibraryEntry(ref("missing"), instant))
        )
        assertEquals(BackupFailureKind.MISSING_REFERENCE, failure(missing))
    }

    @Test
    fun rejectsInvalidRatingAndMovieProgressOnSeries() {
        assertEquals(
            BackupFailureKind.VALIDATION,
            failure(
                baseData().copy(
                    media = listOf(movie()),
                    ratings = listOf(BackupRating(ref("movie"), 11, instant, instant))
                )
            )
        )
        assertEquals(
            BackupFailureKind.CONFLICTING_REFERENCE,
            failure(
                baseData().copy(
                    media = listOf(series()),
                    movieProgress = listOf(BackupMovieProgress(ref("series"), instant))
                )
            )
        )
    }

    @Test
    fun previewContainsIncomingAndCurrentCountsWithoutSensitiveState() {
        val plan = (
            BackupValidator.validate(
                document(baseData().copy(media = listOf(movie())))
            ) as BackupValidationResult.Success
            ).plan
        val preview = BackupValidator.preview(plan, currentLibraryCount = 7)
        assertEquals(1, preview.mediaCount)
        assertEquals(1, preview.movieCount)
        assertEquals(7, preview.currentLibraryCount)
    }

    @Test
    fun rejectsLinkGroupWithMissingMember() {
        val data = baseData().copy(
            media = listOf(movie()),
            mediaLinkGroups = listOf(
                BackupMediaLinkGroup(
                    groupId = "g1",
                    members = listOf(
                        BackupMediaIdentity(MediaSource.TMDB, MediaType.MOVIE, "movie"),
                        BackupMediaIdentity(MediaSource.JIKAN, MediaType.ANIME, "999")
                    ),
                    preferredPresentation = BackupMediaIdentity(MediaSource.TMDB, MediaType.MOVIE, "movie"),
                    createdAt = instant,
                    updatedAt = instant
                )
            )
        )
        val doc = BackupDocument(BACKUP_FORMAT_ID, BACKUP_SCHEMA_VERSION, instant, data)
        val result = BackupValidator.validate(doc)
        assertTrue(result is BackupValidationResult.Failure)
        assertEquals(BackupFailureKind.MISSING_REFERENCE, (result as BackupValidationResult.Failure).failure.kind)
    }

    @Test
    fun rejectsLinkGroupWithInvalidPreferredMember() {
        val anime =
            BackupMedia(
                BackupRef(MediaSource.JIKAN, "200"),
                listOf(BackupRef(MediaSource.JIKAN, "200")),
                MediaType.ANIME,
                "Anime",
                null,
                null,
                null,
                null
            )
        val data = baseData().copy(
            media = listOf(movie(), anime),
            mediaLinkGroups = listOf(
                BackupMediaLinkGroup(
                    groupId = "g1",
                    members = listOf(
                        BackupMediaIdentity(MediaSource.TMDB, MediaType.MOVIE, "movie"),
                        BackupMediaIdentity(MediaSource.JIKAN, MediaType.ANIME, "200")
                    ),
                    preferredPresentation = BackupMediaIdentity(MediaSource.TMDB, MediaType.SERIES, "series"),
                    createdAt = instant,
                    updatedAt = instant
                )
            )
        )
        val doc = BackupDocument(BACKUP_FORMAT_ID, BACKUP_SCHEMA_VERSION, instant, data)
        val result = BackupValidator.validate(doc)
        assertTrue(result is BackupValidationResult.Failure)
        assertEquals(BackupFailureKind.VALIDATION, (result as BackupValidationResult.Failure).failure.kind)
    }

    @Test
    fun acceptsValidV3LinkedDocument() {
        val anime =
            BackupMedia(
                BackupRef(MediaSource.JIKAN, "200"),
                listOf(BackupRef(MediaSource.JIKAN, "200")),
                MediaType.ANIME,
                "Anime",
                null,
                null,
                null,
                null
            )
        val data = baseData().copy(
            media = listOf(movie(), anime),
            mediaLinkGroups = listOf(
                BackupMediaLinkGroup(
                    groupId = "g1",
                    members = listOf(
                        BackupMediaIdentity(MediaSource.TMDB, MediaType.MOVIE, "movie"),
                        BackupMediaIdentity(MediaSource.JIKAN, MediaType.ANIME, "200")
                    ),
                    preferredPresentation = BackupMediaIdentity(MediaSource.TMDB, MediaType.MOVIE, "movie"),
                    createdAt = instant,
                    updatedAt = instant
                )
            )
        )
        val doc = BackupDocument(BACKUP_FORMAT_ID, BACKUP_SCHEMA_VERSION, instant, data)
        assertTrue(BackupValidator.validate(doc) is BackupValidationResult.Success)
    }

    private fun failure(data: BackupData): BackupFailureKind =
        ((BackupValidator.validate(document(data)) as BackupValidationResult.Failure).failure.kind)

    private fun document(data: BackupData) = BackupDocument(BACKUP_FORMAT_ID, 1, instant, data)

    private fun baseData() = BackupData(
        emptyList(),
        emptyList(),
        emptyList(),
        emptyList(),
        emptyList(),
        emptyList(),
        emptyList(),
        BackupPreferences(1, true, true, true)
    )
    private fun ref(id: String) = BackupRef(MediaSource.TMDB, id)
    private fun movie() =
        BackupMedia(ref("movie"), listOf(ref("movie")), MediaType.MOVIE, "Movie", null, null, null, null)
    private fun series() =
        BackupMedia(ref("series"), listOf(ref("series")), MediaType.SERIES, "Series", null, null, null, null)
    private fun season() = BackupSeason(ref("series"), ref("season"), 0, "Specials", null, null, null, 1)
    private fun episode() = BackupEpisode(ref("season"), ref("episode"), 1, "Episode", null, null, null, null)
    private val instant = Instant.parse("2026-08-04T10:00:00Z")
}
