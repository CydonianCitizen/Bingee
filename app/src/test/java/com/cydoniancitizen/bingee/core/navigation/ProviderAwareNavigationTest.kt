package com.cydoniancitizen.bingee.core.navigation

import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
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
    fun jikanMovieRouteCreatesJikanMovieDetailRoute() {
        val ref = ExternalMediaRef(MediaSource.JIKAN, "5114")
        val route = DetailRoute.create(ref, MediaType.MOVIE)
        assertEquals("details/JIKAN/MOVIE/5114", route)

        val args = DetailRoute.parse("JIKAN", "MOVIE", "5114")
        assertNotNull(args)
        assertEquals(MediaSource.JIKAN, args?.reference?.source)
        assertEquals(MediaType.MOVIE, args?.mediaType)
    }

    @Test
    fun jikanSeriesRouteCreatesJikanSeriesDetailRoute() {
        val ref = ExternalMediaRef(MediaSource.JIKAN, "5114")
        val route = DetailRoute.create(ref, MediaType.SERIES)
        assertEquals("details/JIKAN/SERIES/5114", route)

        val args = DetailRoute.parse("JIKAN", "SERIES", "5114")
        assertNotNull(args)
        assertEquals(MediaSource.JIKAN, args?.reference?.source)
        assertEquals(MediaType.SERIES, args?.mediaType)
    }

    @Test
    fun providerIdentitySurvivesRouteSerializationAndParsing() {
        val tmdbMovieArgs = DetailRoute.parse("TMDB", "MOVIE", "100")
        val tmdbSeriesArgs = DetailRoute.parse("TMDB", "SERIES", "100")
        val jikanMovieArgs = DetailRoute.parse("JIKAN", "MOVIE", "100")
        val jikanSeriesArgs = DetailRoute.parse("JIKAN", "SERIES", "100")

        assertEquals(MediaSource.TMDB, tmdbMovieArgs?.reference?.source)
        assertEquals(MediaSource.TMDB, tmdbSeriesArgs?.reference?.source)
        assertEquals(MediaSource.JIKAN, jikanMovieArgs?.reference?.source)
        assertEquals(MediaSource.JIKAN, jikanSeriesArgs?.reference?.source)

        assertNotEquals(tmdbMovieArgs?.reference?.source, jikanMovieArgs?.reference?.source)
        assertNotEquals(tmdbSeriesArgs?.reference?.source, jikanSeriesArgs?.reference?.source)
    }

    @Test
    fun noAccidentalTmdbDetailsOpeningForJikanResults() {
        val jikanMovieRoute = DetailRoute.create(ExternalMediaRef(MediaSource.JIKAN, "100"), MediaType.MOVIE)
        val jikanSeriesRoute = DetailRoute.create(ExternalMediaRef(MediaSource.JIKAN, "100"), MediaType.SERIES)

        val parsedMovie = DetailRoute.parse("JIKAN", "MOVIE", "100")
        val parsedSeries = DetailRoute.parse("JIKAN", "SERIES", "100")

        assertEquals(MediaSource.JIKAN, parsedMovie?.reference?.source)
        assertEquals(MediaSource.JIKAN, parsedSeries?.reference?.source)

        assertNotEquals("details/TMDB/MOVIE/100", jikanMovieRoute)
        assertNotEquals("details/TMDB/SERIES/100", jikanSeriesRoute)
    }
}
