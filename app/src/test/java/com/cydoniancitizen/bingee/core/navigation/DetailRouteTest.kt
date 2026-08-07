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
    fun jikanMovieRoute() {
        val ref = ExternalMediaRef(MediaSource.JIKAN, "5114")
        val route = DetailRoute.create(ref, MediaType.MOVIE)
        assertEquals("details/JIKAN/MOVIE/5114", route)
        val parsed = DetailRoute.parse("JIKAN", "MOVIE", "5114")
        assertEquals(DetailRouteArgs(ref, MediaType.MOVIE), parsed)
    }

    @Test
    fun jikanSeriesRoute() {
        val ref = ExternalMediaRef(MediaSource.JIKAN, "5114")
        val route = DetailRoute.create(ref, MediaType.SERIES)
        assertEquals("details/JIKAN/SERIES/5114", route)
        val parsed = DetailRoute.parse("JIKAN", "SERIES", "5114")
        assertEquals(DetailRouteArgs(ref, MediaType.SERIES), parsed)
    }

    @Test
    fun jikanAnimeRoute() {
        val ref = ExternalMediaRef(MediaSource.JIKAN, "5114")
        val route = DetailRoute.create(ref, MediaType.ANIME)
        assertEquals("details/JIKAN/ANIME/5114", route)
        val parsed = DetailRoute.parse("JIKAN", "ANIME", "5114")
        assertEquals(DetailRouteArgs(ref, MediaType.ANIME), parsed)
    }

    @Test
    fun providerIdentitySurvivesRouteSerializationAndParsing() {
        val cases = listOf(
            DetailRouteArgs(ExternalMediaRef(MediaSource.TMDB, "100"), MediaType.MOVIE),
            DetailRouteArgs(ExternalMediaRef(MediaSource.TMDB, "200"), MediaType.SERIES),
            DetailRouteArgs(ExternalMediaRef(MediaSource.JIKAN, "300"), MediaType.MOVIE),
            DetailRouteArgs(ExternalMediaRef(MediaSource.JIKAN, "400"), MediaType.SERIES),
            DetailRouteArgs(ExternalMediaRef(MediaSource.JIKAN, "500"), MediaType.ANIME)
        )

        cases.forEach { (ref, mediaType) ->
            val route = DetailRoute.create(ref, mediaType)
            val parts = route.split('/')
            assertEquals(3, parts.size - 1)
            val parsed = DetailRoute.parse(parts[1], parts[2], parts[3])
            assertEquals("Failed for $ref / $mediaType", ref, parsed?.reference)
            assertEquals("Failed mediaType for $ref / $mediaType", mediaType, parsed?.mediaType)
            assertEquals("Source altered for $ref", ref.source, parsed?.reference?.source)
        }
    }

    @Test
    fun noAccidentalTmdbDetailsOpeningForJikanResults() {
        val jikanMovieRoute = DetailRoute.create(
            ExternalMediaRef(MediaSource.JIKAN, "42"),
            MediaType.MOVIE
        )
        val jikanSeriesRoute = DetailRoute.create(
            ExternalMediaRef(MediaSource.JIKAN, "42"),
            MediaType.SERIES
        )
        val tmdbMovieRoute = DetailRoute.create(
            ExternalMediaRef(MediaSource.TMDB, "42"),
            MediaType.MOVIE
        )

        assertTrue(jikanMovieRoute.startsWith("details/JIKAN/"))
        assertTrue(jikanSeriesRoute.startsWith("details/JIKAN/"))
        assertNotEquals(jikanMovieRoute, tmdbMovieRoute)

        val parsedJikanMovie = DetailRoute.parse("JIKAN", "MOVIE", "42")
        assertEquals(MediaSource.JIKAN, parsedJikanMovie?.reference?.source)
        assertNotEquals(MediaSource.TMDB, parsedJikanMovie?.reference?.source)

        val parsedJikanSeries = DetailRoute.parse("JIKAN", "SERIES", "42")
        assertEquals(MediaSource.JIKAN, parsedJikanSeries?.reference?.source)
        assertNotEquals(MediaSource.TMDB, parsedJikanSeries?.reference?.source)
    }

    @Test
    fun providerMediaTypeMismatchIsRejectedWithoutSubstitution() {
        assertNull(DetailRoute.parse("TMDB", "ANIME", "42"))
        assertThrows(IllegalArgumentException::class.java) {
            DetailRoute.create(ExternalMediaRef(MediaSource.TMDB, "42"), MediaType.ANIME)
        }
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
