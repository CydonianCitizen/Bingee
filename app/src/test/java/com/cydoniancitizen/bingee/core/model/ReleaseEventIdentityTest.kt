package com.cydoniancitizen.bingee.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ReleaseEventIdentityTest {
    @Test
    fun identitySeparatesSubjectTypeProviderAndEventType() {
        val media = identity(MediaSource.TMDB, ReleaseSubjectType.MEDIA, ReleaseEventType.MOVIE_RELEASE)
        val season = identity(MediaSource.TMDB, ReleaseSubjectType.SEASON, ReleaseEventType.SEASON_PREMIERE)
        val episode = identity(MediaSource.TMDB, ReleaseSubjectType.EPISODE, ReleaseEventType.EPISODE_AIRING)
        val otherProvider = identity(MediaSource.IMDB, ReleaseSubjectType.MEDIA, ReleaseEventType.MOVIE_RELEASE)
        val otherEvent = identity(MediaSource.TMDB, ReleaseSubjectType.MEDIA, ReleaseEventType.SEASON_PREMIERE)

        assertEquals(5, setOf(media, season, episode, otherProvider, otherEvent).size)
        assertNotEquals(media.stableKey, season.stableKey)
        assertEquals("TMDB:EPISODE:42:EPISODE_AIRING", episode.stableKey)
        assertEquals(media, identity(MediaSource.TMDB, ReleaseSubjectType.MEDIA, ReleaseEventType.MOVIE_RELEASE))
    }

    @Test(expected = IllegalArgumentException::class)
    fun blankSubjectIdIsRejected() {
        ReleaseSubjectIdentity(
            MediaSource.TMDB,
            ReleaseSubjectType.MEDIA,
            " ",
            ReleaseEventType.MOVIE_RELEASE
        )
    }

    private fun identity(source: MediaSource, type: ReleaseSubjectType, event: ReleaseEventType) =
        ReleaseSubjectIdentity(source, type, "42", event)
}
