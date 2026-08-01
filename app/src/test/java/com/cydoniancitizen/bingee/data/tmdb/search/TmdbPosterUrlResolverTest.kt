package com.cydoniancitizen.bingee.data.tmdb.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class TmdbPosterUrlResolverTest {
    @Test
    fun resolvesOnlySupportedPathsAtListSize() {
        val url = TmdbPosterUrlResolver.resolve("/poster.webp")

        assertEquals("w342", TmdbPosterUrlResolver.LIST_SIZE)
        assertEquals("https://image.tmdb.org/t/p/w342/poster.webp", url)
        assertFalse(requireNotNull(url).contains("/original/"))
        assertNull(TmdbPosterUrlResolver.resolve(null))
        assertNull(TmdbPosterUrlResolver.resolve("https://example.com/poster.jpg"))
        assertNull(TmdbPosterUrlResolver.resolve("/poster.svg"))
        assertNull(TmdbPosterUrlResolver.resolve("/../poster.jpg"))
    }
}
