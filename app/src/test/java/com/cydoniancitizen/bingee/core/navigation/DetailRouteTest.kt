package com.cydoniancitizen.bingee.core.navigation

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
        val route = DetailRoute.create(MediaType.MOVIE, 550)
        assertEquals("details/MOVIE/550", route)
        assertEquals(DetailRouteArgs(MediaType.MOVIE, 550), DetailRoute.parse("MOVIE", "550"))
    }

    @Test
    fun tmdbSeriesRoute() {
        val route = DetailRoute.create(MediaType.SERIES, 1399)
        assertEquals("details/SERIES/1399", route)
        assertEquals(DetailRouteArgs(MediaType.SERIES, 1399), DetailRoute.parse("SERIES", "1399"))
    }

    @Test
    fun malformedArgumentsAreRejected() {
        assertNull(DetailRoute.parse("bad", "1"))
        assertNull(DetailRoute.parse("MOVIE", " "))
        assertNull(DetailRoute.parse("MOVIE", "bad"))
        assertNull(DetailRoute.parse("MOVIE", "0"))
        assertThrows(IllegalArgumentException::class.java) {
            DetailRoute.create(MediaType.MOVIE, 0)
        }
    }

    @Test
    fun routeContainsNoInternalIdTokenOrPayload() {
        val route = DetailRoute.create(MediaType.MOVIE, 550)
        assertTrue(route.startsWith("details/MOVIE/"))
        assertFalse(route.contains("local", ignoreCase = true))
        assertFalse(route.contains("token", ignoreCase = true))
        assertFalse(TopLevelDestination.entries.any { it.route == "details" })
    }
}
