package com.cydoniancitizen.bingee.core.model

import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchProgressDerivationTest {
    private val today = LocalDate.of(2026, 8, 3)
    private val watchedAt = Instant.parse("2026-08-03T10:00:00Z")

    @Test
    fun currentAndUnknownDatesAreTrackableWhileFutureDateIsUnavailable() {
        assertEquals(EpisodeWatchState.Unwatched, state(episode(1, today), null))
        assertEquals(EpisodeWatchState.Unwatched, state(episode(2, null), null))
        assertEquals(EpisodeWatchState.Unavailable, state(episode(3, today.plusDays(1)), watchedAt))
        assertEquals(EpisodeWatchState.Watched(watchedAt), state(episode(4, today), watchedAt))
    }

    @Test
    fun seasonProgressIsDeterministicAndSafeForZeroPartialAndCompleteInputs() {
        assertEquals(SeasonProgress.EMPTY, deriveSeasonProgress(emptyList()))
        val partial = listOf(
            tracked(1, EpisodeWatchState.Watched(watchedAt)),
            tracked(2, EpisodeWatchState.Unwatched),
            tracked(3, EpisodeWatchState.Unavailable)
        )
        assertEquals(SeasonProgress(1, 2, false), deriveSeasonProgress(partial))
        assertEquals(0.5f, deriveSeasonProgress(partial).fraction)
        assertEquals(
            SeasonProgress(2, 2, true),
            deriveSeasonProgress(
                partial.mapIndexed { index, row ->
                    if (index == 1) row.copy(watchState = EpisodeWatchState.Watched(watchedAt)) else row
                }
            )
        )
        assertEquals(0f, SeasonProgress.EMPTY.fraction)
    }

    @Test
    fun seriesCompletionRequiresCompleteEpisodeCacheForEveryRegularSeason() {
        val first = cachedSeason(1, SeasonProgress(2, 2, true))
        val second = cachedSeason(2, SeasonProgress(3, 3, true))

        assertTrue(deriveSeriesProgress(listOf(first)).isComplete)
        assertTrue(deriveSeriesProgress(listOf(first, second)).isComplete)
        assertFalse(
            deriveSeriesProgress(listOf(first, cachedSeason(2, SeasonProgress.EMPTY, fetchedAt = null))).isComplete
        )
        assertFalse(
            deriveSeriesProgress(
                listOf(cachedSeason(1, SeasonProgress(2, 2, true), episodeCount = 3, cachedEpisodes = 2))
            ).isComplete
        )
    }

    @Test
    fun canonicalSeriesCompletionRequiresPositiveEqualCountsAndCoverage() {
        assertFalse(isSeriesComplete(watchedEpisodes = 0, trackableEpisodes = 0, hasSufficientCoverage = true))
        assertFalse(isSeriesComplete(watchedEpisodes = 1, trackableEpisodes = 2, hasSufficientCoverage = true))
        assertFalse(isSeriesComplete(watchedEpisodes = 2, trackableEpisodes = 2, hasSufficientCoverage = false))
        assertTrue(isSeriesComplete(watchedEpisodes = 2, trackableEpisodes = 2, hasSufficientCoverage = true))
    }

    @Test
    fun restoredSeasonsProveCoverageThroughStoredRowsWithoutAFetchTimestamp() {
        val restored = cachedSeason(1, SeasonProgress(2, 2, true), fetchedAt = null)
        val restoredEmpty = cachedSeason(2, SeasonProgress.EMPTY, fetchedAt = null)
        val fetchedEmpty = cachedSeason(2, SeasonProgress.EMPTY)

        assertTrue(restored.hasSufficientEpisodeCoverage())
        assertTrue(deriveSeriesProgress(listOf(restored)).isComplete)
        // A declared zero may be unknown: only a season fetch proves the season is really empty.
        assertFalse(restoredEmpty.hasSufficientEpisodeCoverage())
        assertFalse(deriveSeriesProgress(listOf(restored, restoredEmpty)).isComplete)
        assertTrue(deriveSeriesProgress(listOf(restored, fetchedEmpty)).isComplete)
        val provenEmpty = fetchedEmpty.copy(episodesFetchedAt = null, episodeCacheFreshness = null)
        assertTrue(provenEmpty.hasSufficientEpisodeCoverage())
        assertTrue(deriveSeriesProgress(listOf(restored, provenEmpty)).isComplete)
        assertFalse(deriveSeriesProgress(listOf(provenEmpty)).isComplete)
        assertFalse(
            provenEmpty.copy(episodes = listOf(tracked(1, EpisodeWatchState.Unwatched)))
                .hasSufficientEpisodeCoverage()
        )
        // Partial rows or an available unwatched episode keep the series open.
        val partial =
            cachedSeason(1, SeasonProgress(2, 2, true), episodeCount = 3, cachedEpisodes = 2, fetchedAt = null)
        assertFalse(deriveSeriesProgress(listOf(partial)).isComplete)
        val unwatchedAvailable = cachedSeason(1, SeasonProgress(1, 2, false), fetchedAt = null)
        assertFalse(deriveSeriesProgress(listOf(unwatchedAvailable)).isComplete)
    }

    @Test
    fun specialsAndFutureEpisodesKeepExistingCompletionSemantics() {
        val specials = cachedSeason(0, SeasonProgress.EMPTY, fetchedAt = null)
        val regular = cachedSeason(1, SeasonProgress(1, 1, true), episodeCount = 2)

        val progress = deriveSeriesProgress(listOf(specials, regular))

        assertEquals(SeriesProgress(1, 1, 1, 1, true), progress)
        assertTrue(progress.isComplete)
        assertFalse(deriveSeriesProgress(listOf(specials)).isComplete)
        assertEquals(0f, deriveSeriesProgress(listOf(specials)).fraction)
    }

    private fun state(episode: Episode, at: Instant?) = deriveEpisodeWatchState(episode, at, today)

    private fun tracked(number: Int, state: EpisodeWatchState) = TrackedEpisode(episode(number, today), state)

    private fun episode(number: Int, date: LocalDate?) = Episode(
        seriesRef = ref("100"),
        seasonRef = ref("200"),
        externalRef = ref((300 + number).toString()),
        seasonNumber = 1,
        episodeNumber = number,
        title = "Episode $number",
        airDate = date
    )

    private fun cachedSeason(
        number: Int,
        progress: SeasonProgress,
        episodeCount: Int = progress.trackableEpisodes,
        cachedEpisodes: Int = episodeCount,
        fetchedAt: Instant? = watchedAt
    ) = CachedSeason(
        season = Season(ref("100"), ref((200 + number).toString()), number, episodeCount = episodeCount),
        metadataUpdatedAt = watchedAt,
        episodesFetchedAt = fetchedAt,
        episodes = List(cachedEpisodes) { tracked(it + 1, EpisodeWatchState.Unwatched) },
        progress = progress,
        episodeCacheFreshness = CacheFreshness.FRESH,
        isKnownEmpty = episodeCount == 0 && cachedEpisodes == 0 && fetchedAt != null
    )

    private fun ref(id: String) = ExternalMediaRef(MediaSource.TMDB, id)
}
