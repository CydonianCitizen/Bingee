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
    fun tmdbMovieRoute() {
        val ref = ExternalMediaRef(MediaSource.TMDB, "550")
        val route = DetailRoute.create(ref, MediaType.MOVIE)
        assertEquals("details/TMDB/MOVIE/550", route)
        val parsed = DetailRoute.parse("TMDB", "MOVIE", "550")
        assertEquals(DetailRouteArgs(ref, MediaType.MOVIE), parsed)
    }

    @Test
    fun tmdbSeriesRoute() {
        val ref = ExternalMediaRef(MediaSource.TMDB, "1399")
        val route = DetailRoute.create(ref, MediaType.SERIES)
        assertEquals("details/TMDB/SERIES/1399", route)
        val parsed = DetailRoute.parse("TMDB", "SERIES", "1399")
        assertEquals(DetailRouteArgs(ref, MediaType.SERIES), parsed)
    }

    @Test
    fun imdbRoutesAreRejectedForDetails() {
        val ref = ExternalMediaRef(MediaSource.IMDB, "5114")
        assertThrows(IllegalArgumentException::class.java) {
            DetailRoute.create(ref, MediaType.MOVIE)
        }
        assertThrows(IllegalArgumentException::class.java) {
            DetailRoute.create(ref, MediaType.SERIES)
        }
        assertNull(DetailRoute.parse("IMDB", "MOVIE", "5114"))
        assertNull(DetailRoute.parse("IMDB", "SERIES", "5114"))
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
