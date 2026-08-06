package com.cydoniancitizen.bingee.core.model

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaLinkModelsTest {

    @Test(expected = IllegalArgumentException::class)
    fun mediaLinkGroupIdBlankValueThrowsException() {
        MediaLinkGroupId("   ")
    }

    @Test(expected = IllegalArgumentException::class)
    fun linkedMediaIdentityBlankExternalIdThrowsException() {
        LinkedMediaIdentity(MediaSource.TMDB, MediaType.MOVIE, "")
    }

    @Test(expected = IllegalArgumentException::class)
    fun mediaLinkGroupSameIdentitiesThrowsException() {
        val identity = LinkedMediaIdentity(MediaSource.TMDB, MediaType.MOVIE, "101")
        MediaLinkGroup(
            groupId = MediaLinkGroupId("grp-1"),
            first = identity,
            second = identity,
            preferredPresentation = identity,
            createdAt = Instant.parse("2026-08-05T10:00:00Z"),
            updatedAt = Instant.parse("2026-08-05T10:00:00Z")
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun mediaLinkGroupInvalidPreferredPresentationThrowsException() {
        val first = LinkedMediaIdentity(MediaSource.TMDB, MediaType.MOVIE, "101")
        val second = LinkedMediaIdentity(MediaSource.JIKAN, MediaType.ANIME, "202")
        val third = LinkedMediaIdentity(MediaSource.TMDB, MediaType.SERIES, "303")
        MediaLinkGroup(
            groupId = MediaLinkGroupId("grp-1"),
            first = first,
            second = second,
            preferredPresentation = third,
            createdAt = Instant.parse("2026-08-05T10:00:00Z"),
            updatedAt = Instant.parse("2026-08-05T10:00:00Z")
        )
    }

    @Test
    fun validMediaLinkGroupConstructsSuccessfully() {
        val first = LinkedMediaIdentity(MediaSource.TMDB, MediaType.MOVIE, "101")
        val second = LinkedMediaIdentity(MediaSource.JIKAN, MediaType.ANIME, "202")
        val group = MediaLinkGroup(
            groupId = MediaLinkGroupId("grp-1"),
            first = first,
            second = second,
            preferredPresentation = second,
            createdAt = Instant.parse("2026-08-05T10:00:00Z"),
            updatedAt = Instant.parse("2026-08-05T10:00:00Z")
        )
        assertEquals("grp-1", group.groupId.value)
        assertEquals(second, group.preferredPresentation)
    }
}
