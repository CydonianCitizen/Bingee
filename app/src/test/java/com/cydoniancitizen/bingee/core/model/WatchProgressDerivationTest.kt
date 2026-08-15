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
        episodeCacheFreshness = CacheFreshness.FRESH
    )

    private fun ref(id: String) = ExternalMediaRef(MediaSource.TMDB, id)
}
