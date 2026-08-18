package com.cydoniancitizen.bingee.data.importexport

import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupValidatorTest {
    private val today = LocalDate.of(2026, 8, 18)

    @Test
    fun acceptsSeasonZeroAndCrossReferenceGraph() {
        val data = baseData().copy(
            media = listOf(series()),
            seasons = listOf(season()),
            episodes = listOf(episode()),
            episodeProgress = listOf(BackupEpisodeProgress(ref("20001"), instant))
        )
        assertTrue(validate(document(data)) is BackupValidationResult.Success)
    }

    @Test
    fun acceptsPositiveTmdbId() {
        assertTrue(
            validate(
                document(baseData().copy(media = listOf(movieWithId("550"))))
            ) is BackupValidationResult.Success
        )
    }

    @Test
    fun rejectsZeroTmdbId() {
        assertTmdbIdRejected("0")
    }

    @Test
    fun rejectsWhitespaceTmdbId() {
        assertTmdbIdRejected("   ")
    }

    @Test
    fun rejectsNegativeTmdbId() {
        assertTmdbIdRejected("-10")
    }

    @Test
    fun rejectsAlphanumericTmdbId() {
        assertTmdbIdRejected("abc")
    }

    @Test
    fun rejectsDecimalTmdbId() {
        assertTmdbIdRejected("12.5")
    }

    @Test
    fun rejectsOverflowTmdbId() {
        assertTmdbIdRejected("999999999999999999999999999999")
    }

    @Test
    fun rejectsDuplicateMediaAndMissingReferences() {
        val duplicate = baseData().copy(media = listOf(movie(), movie()))
        assertEquals(
            BackupFailureKind.DUPLICATE_IDENTITY,
            failure(duplicate)
        )
        val missing = baseData().copy(
            library = listOf(BackupLibraryEntry(ref("99999"), instant))
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
                    ratings = listOf(BackupRating(ref("550"), 11, instant, instant))
                )
            )
        )
        assertEquals(
            BackupFailureKind.CONFLICTING_REFERENCE,
            failure(
                baseData().copy(
                    media = listOf(series()),
                    movieProgress = listOf(BackupMovieProgress(ref("1399"), instant))
                )
            )
        )
    }

    @Test
    fun previewContainsIncomingAndCurrentCountsWithoutSensitiveState() {
        val plan = (
            validate(
                document(baseData().copy(media = listOf(movie())))
            ) as BackupValidationResult.Success
            ).plan
        val preview = BackupValidator.preview(plan, currentLibraryCount = 7)
        assertEquals(1, preview.mediaCount)
        assertEquals(1, preview.movieCount)
        assertEquals(7, preview.currentLibraryCount)
    }

    private fun failure(data: BackupData): BackupFailureKind =
        ((validate(document(data)) as BackupValidationResult.Failure).failure.kind)

    private fun validate(document: BackupDocument) = BackupValidator.validate(document, today)

    private fun assertTmdbIdRejected(id: String) {
        assertEquals(BackupFailureKind.VALIDATION, failure(baseData().copy(media = listOf(movieWithId(id)))))
    }

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
    private fun movie() = movieWithId("550")
    private fun movieWithId(id: String) =
        BackupMedia(ref(id), listOf(ref(id)), MediaType.MOVIE, "Movie", null, null, null, null)
    private fun series() =
        BackupMedia(ref("1399"), listOf(ref("1399")), MediaType.SERIES, "Series", null, null, null, null)
    private fun season() = BackupSeason(ref("1399"), ref("10001"), 0, "Specials", null, null, null, 1)
    private fun episode() = BackupEpisode(ref("10001"), ref("20001"), 1, "Episode", null, null, null, null)
    private val instant = Instant.parse("2026-08-04T10:00:00Z")
}
