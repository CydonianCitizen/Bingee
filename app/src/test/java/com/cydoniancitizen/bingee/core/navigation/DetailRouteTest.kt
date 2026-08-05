package com.cydoniancitizen.bingee.core.navigation

import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DetailRouteTest {
    @Test
    fun movieAndTvRoutesRoundTripProviderIdentity() {
        listOf(MediaType.MOVIE, MediaType.SERIES).forEach { type ->
            val route = DetailRoute.create(ExternalMediaRef(MediaSource.TMDB, "1399"), type)
            val parts = route.split('/')
            assertEquals(
                DetailRouteArgs(ExternalMediaRef(MediaSource.TMDB, "1399"), type),
                DetailRoute.parse(parts[1], parts[2], parts[3])
            )
        }
    }

    @Test
    fun animeRouteRoundTripsJikanIdentityAndKeepsNumericCollisionSeparate() {
        val animeRoute = DetailRoute.create(
            ExternalMediaRef(MediaSource.JIKAN, "42"),
            MediaType.ANIME
        )
        val tmdbRoute = DetailRoute.create(
            ExternalMediaRef(MediaSource.TMDB, "42"),
            MediaType.MOVIE
        )

        assertNotEquals(animeRoute, tmdbRoute)
        assertEquals(
            DetailRouteArgs(ExternalMediaRef(MediaSource.JIKAN, "42"), MediaType.ANIME),
            DetailRoute.parse("JIKAN", "ANIME", "42")
        )
    }

    @Test
    fun providerMediaTypeMismatchIsRejectedWithoutSubstitution() {
        assertNull(DetailRoute.parse("TMDB", "ANIME", "42"))
        assertNull(DetailRoute.parse("JIKAN", "MOVIE", "42"))
        assertThrows(IllegalArgumentException::class.java) {
            DetailRoute.create(ExternalMediaRef(MediaSource.TMDB, "42"), MediaType.ANIME)
        }
        assertThrows(IllegalArgumentException::class.java) {
            DetailRoute.create(ExternalMediaRef(MediaSource.JIKAN, "42"), MediaType.SERIES)
        }
    }

    @Test
    fun changingAnimeRouteIdChangesOnlyProviderQualifiedIdentity() {
        val first = DetailRoute.parse("JIKAN", "ANIME", "42")
        val second = DetailRoute.parse("JIKAN", "ANIME", "43")

        assertNotEquals(first, second)
        assertEquals("42", first?.reference?.externalId)
        assertEquals("43", second?.reference?.externalId)
    }

    @Test
    fun malformedArgumentsAreRejected() {
        assertNull(DetailRoute.parse("bad", "MOVIE", "1"))
        assertNull(DetailRoute.parse("TMDB", "bad", "1"))
        assertNull(DetailRoute.parse("TMDB", "MOVIE", " "))
        assertNull(DetailRoute.parse("TMDB", "MOVIE", "bad"))
        assertNull(DetailRoute.parse("TMDB", "MOVIE", "%"))
        assertThrows(IllegalArgumentException::class.java) {
            DetailRoute.create(ExternalMediaRef(MediaSource.TMDB, "x").copy(externalId = " "), MediaType.MOVIE)
        }
    }

    @Test
    fun routeContainsNoInternalIdTokenOrPayload() {
        val route = DetailRoute.create(ExternalMediaRef(MediaSource.TMDB, "550"), MediaType.MOVIE)
        assertTrue(route.startsWith("details/TMDB/MOVIE/"))
        assertFalse(route.contains("local", ignoreCase = true))
        assertFalse(route.contains("token", ignoreCase = true))
        assertFalse(TopLevelDestination.entries.any { it.route == "details" })
    }
}
