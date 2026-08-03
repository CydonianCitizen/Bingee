package com.cydoniancitizen.bingee.data.tmdb.series

import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.data.tmdb.details.TmdbSeasonSummaryDto
import java.time.Duration
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TmdbSeasonMappersTest {
    private val seriesRef = ExternalMediaRef(MediaSource.TMDB, "1399")

    @Test
    fun summariesKeepSpecialsNormalizeOptionalFieldsAndOrderSafely() {
        val rows = listOf(
            summary(id = 20, number = 2, name = "  Season 2  ", count = -4, date = "bad"),
            summary(id = 10, number = 0, name = " ", count = 0, date = null),
            summary(id = 30, number = 1, name = "Season 1", count = 8, date = "2020-01-02"),
            summary(id = 31, number = 1, name = "duplicate", count = 1, date = null),
            summary(id = null, number = 3, name = "invalid", count = 1, date = null)
        )

        val mapped = TmdbSeasonSummaryMapper.mapAll(seriesRef, rows)

        assertEquals(listOf(0, 1, 2), mapped.map { it.seasonNumber })
        assertNull(mapped.first().name)
        assertEquals("10", mapped.first().externalRef.externalId)
        assertEquals(MediaSource.TMDB, mapped.first().externalRef.source)
        assertEquals(LocalDate.of(2020, 1, 2), mapped[1].airDate)
        assertEquals(0, mapped[2].episodeCount)
        assertNull(mapped[2].airDate)
        assertTrue(mapped[1].posterUrl?.endsWith("/poster.jpg") == true)
    }

    @Test
    fun detailsSkipUnusableAndDuplicateEpisodesWhileKeepingUnknownAndFutureDates() {
        val dto = TmdbSeasonDetailsDto(
            id = 900,
            seasonNumber = 1,
            name = "Season 1",
            overview = " ",
            posterPath = null,
            airDate = "2026-01-01",
            episodes = listOf(
                episode(101, 1, 2, "Second", "2027-01-01", 0),
                episode(100, 1, 1, " First ", null, 55),
                episode(102, 1, 1, "duplicate number", "2026-01-01", 45),
                episode(101, 1, 3, "duplicate id", "2026-01-01", 45),
                episode(null, 1, 4, "no id", null, 45),
                episode(104, 2, 4, "wrong season", null, 45),
                episode(105, 1, 5, " ", "malformed", -1)
            )
        )

        val payload = TmdbSeasonDetailsMapper.map(seriesRef, 1, dto)!!

        assertEquals(listOf(1, 2), payload.episodes.map { it.episodeNumber })
        assertEquals("First", payload.episodes.first().title)
        assertEquals(Duration.ofMinutes(55), payload.episodes.first().runtime)
        assertNull(payload.episodes.first().airDate)
        assertNull(payload.episodes[1].runtime)
        assertEquals(LocalDate.of(2027, 1, 1), payload.episodes[1].airDate)
        assertEquals("100", payload.episodes.first().externalRef.externalId)
        assertEquals(seriesRef, payload.episodes.first().seriesRef)
        assertEquals("900", payload.episodes.first().seasonRef.externalId)
        assertEquals(7, payload.season.episodeCount)
    }

    @Test
    fun mismatchedSeasonOrBlankProviderIdentityFailsMapping() {
        val mismatch = TmdbSeasonDetailsDto(1, 2, "S2", null, null, null, emptyList())
        val blankIdentity = TmdbSeasonDetailsDto(0, 1, "S1", null, null, null, emptyList())

        assertNull(TmdbSeasonDetailsMapper.map(seriesRef, 1, mismatch))
        assertNull(TmdbSeasonDetailsMapper.map(seriesRef, 1, blankIdentity))
    }

    private fun summary(id: Long?, number: Int?, name: String?, count: Int?, date: String?) =
        TmdbSeasonSummaryDto(id, number, name, " ", "/poster.jpg", date, count)

    private fun episode(id: Long?, season: Int?, number: Int?, name: String?, date: String?, runtime: Int?) =
        TmdbEpisodeDto(id, season, number, name, " ", date, runtime, null)
}
