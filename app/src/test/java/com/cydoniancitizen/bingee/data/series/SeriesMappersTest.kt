package com.cydoniancitizen.bingee.data.series

import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.Season
import com.cydoniancitizen.bingee.data.CacheFreshnessPolicy
import com.cydoniancitizen.bingee.data.library.local.SeasonWithEpisodesRelation
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertNull
import org.junit.Test

class SeriesMappersTest {
    @Test
    fun missingEpisodeFetchTimestampKeepsFreshnessAbsent() {
        val now = Instant.parse("2026-08-03T12:00:00Z")
        val seriesRef = ExternalMediaRef(MediaSource.TMDB, "100")
        val season = Season(seriesRef, ExternalMediaRef(MediaSource.TMDB, "11"), 1)
        val row = SeasonWithEpisodesRelation(season.toEntity(now), emptyList())

        val cached = row.toDomain(
            seriesRef,
            LocalDate.of(2026, 8, 3),
            CacheFreshnessPolicy(Clock.fixed(now, ZoneOffset.UTC))
        )

        assertNull(cached.episodesFetchedAt)
        assertNull(cached.episodeCacheFreshness)
    }
}
