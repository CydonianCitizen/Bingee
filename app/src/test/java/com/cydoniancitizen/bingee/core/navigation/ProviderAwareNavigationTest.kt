package com.cydoniancitizen.bingee.core.navigation

import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class ProviderAwareNavigationTest {
    @Test
    fun tmdbMovieRouteCreatesTmdbMovieDetailRoute() {
        val ref = ExternalMediaRef(MediaSource.TMDB, "550")
        val route = DetailRoute.create(ref, MediaType.MOVIE)
        assertEquals("details/TMDB/MOVIE/550", route)

        val args = DetailRoute.parse("TMDB", "MOVIE", "550")
        assertNotNull(args)
        assertEquals(MediaSource.TMDB, args?.reference?.source)
        assertEquals(MediaType.MOVIE, args?.mediaType)
    }

    @Test
    fun tmdbSeriesRouteCreatesTmdbSeriesDetailRoute() {
        val ref = ExternalMediaRef(MediaSource.TMDB, "1399")
        val route = DetailRoute.create(ref, MediaType.SERIES)
        assertEquals("details/TMDB/SERIES/1399", route)

        val args = DetailRoute.parse("TMDB", "SERIES", "1399")
        assertNotNull(args)
        assertEquals(MediaSource.TMDB, args?.reference?.source)
        assertEquals(MediaType.SERIES, args?.mediaType)
    }

    @Test
    fun imdbRoutesDoNotCreateDetailRoute() {
        val movieRef = ExternalMediaRef(MediaSource.IMDB, "5114")
        val seriesRef = ExternalMediaRef(MediaSource.IMDB, "5114")

        assertThrows(IllegalArgumentException::class.java) {
            DetailRoute.create(movieRef, MediaType.MOVIE)
        }
        assertThrows(IllegalArgumentException::class.java) {
            DetailRoute.create(seriesRef, MediaType.SERIES)
        }

        assertNull(DetailRoute.parse("IMDB", "MOVIE", "5114"))
        assertNull(DetailRoute.parse("IMDB", "SERIES", "5114"))
    }
}
